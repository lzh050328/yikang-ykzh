package com.xhzb.nursing.service.impl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import cn.hutool.json.JSONUtil;
import com.xhzb.common.constant.CacheConstants;
import com.xhzb.common.utils.DateUtils;
import com.xhzb.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.xhzb.nursing.mapper.AlertRuleMapper;
import com.xhzb.nursing.domain.AlertData;
import com.xhzb.nursing.domain.AlertRule;
import com.xhzb.nursing.domain.DeviceData;
import com.xhzb.nursing.service.IAlertDataService;
import com.xhzb.nursing.service.IAlertRuleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 报警规则Service业务层处理
 * 
 * @author lzh
 * @date 2026-11-05
 */
@Service
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements IAlertRuleService
{
    private static final Logger log = LoggerFactory.getLogger(AlertRuleServiceImpl.class);

    /** 通配设备ID：报警规则里 iot_id 为 -1 表示该产品下所有设备通用 */
    private static final String WILDCARD_IOT_ID = "-1";

    /** 上报数据有效期（分钟），超过该时间的上报数据不再参与过滤 */
    private static final long REPORT_TIMEOUT_MINUTES = 1L;

    /** 角色名称：行政（设备异常数据的接收人） */
    private static final String ROLE_ADMINISTRATIVE = "行政";

    /** 角色名称：超级管理员（所有报警都要通知） */
    private static final String ROLE_SUPER_ADMIN = "超级管理员";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter TIME_FMT_SHORT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private AlertRuleMapper alertRuleMapper;

    @Autowired
    private IAlertDataService alertDataService;

    /** 读取设备上报数据用的模板（与写入方 DeviceDataServiceImpl 的序列化方式一致） */
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /** 计数/沉默周期用字符串模板，保证自增（INCR）可用 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询报警规则
     * 
     * @param id 报警规则主键
     * @return 报警规则
     */
    @Override
    public AlertRule selectAlertRuleById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询报警规则列表
     * 
     * @param alertRule 报警规则
     * @return 报警规则
     */
    @Override
    public List<AlertRule> selectAlertRuleList(AlertRule alertRule)
    {
        return alertRuleMapper.selectAlertRuleList(alertRule);
    }

    /**
     * 新增报警规则
     * 
     * @param alertRule 报警规则
     * @return 结果
     */
    @Override
    public int insertAlertRule(AlertRule alertRule)
    {
        return save(alertRule)? 1 : 0;
    }

    /**
     * 修改报警规则
     * 
     * @param alertRule 报警规则
     * @return 结果
     */
    @Override
    public int updateAlertRule(AlertRule alertRule)
    {
        return updateById(alertRule)? 1 : 0;
    }

    /**
     * 批量删除报警规则
     * 
     * @param ids 需要删除的报警规则主键
     * @return 结果
     */
    @Override
    public int deleteAlertRuleByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids))? 1 : 0;
    }

    /**
     * 删除报警规则信息
     * 
     * @param id 报警规则主键
     * @return 结果
     */
    @Override
    public int deleteAlertRuleById(Long id)
    {
        return removeById(id)? 1 : 0;
    }

    /**
     * 设备上报数据报警规则过滤
     * <p>
     * 流程：取启用的报警规则 + Redis 中的设备最新上报数据 → 逐条数据匹配规则 →
     * 四层校验（生效时段 / 阈值 / 沉默周期 / 持续周期）→ 全部通过则写一条报警数据。
     */
    @Override
    public void deviceDataAlertFilter() {
        // ==================== 一、前置数据判断，避免无效数据 ====================
        // 1. 查询所有可用（启用）的报警规则
        List<AlertRule> allRules = lambdaQuery().eq(AlertRule::getStatus, 1).list();
        if (allRules == null || allRules.isEmpty()) {
            log.error("报警规则过滤结束：没有启用的报警规则");
            return;
        }

        // 2. 从 Redis Hash 中查询所有设备上报数据
        Map<Object, Object> reportHash = redisTemplate.opsForHash()
                .entries(CacheConstants.IOT_DEVICE_DATA_KEY);
        if (reportHash == null || reportHash.isEmpty()) {
            log.error("报警规则过滤结束：Redis[{}]中没有设备上报数据", CacheConstants.IOT_DEVICE_DATA_KEY);
            return;
        }

        // 3. 把 Hash 的所有 value 汇总成上报数据集合
        List<DeviceData> reports = new ArrayList<>();
        for (Object value : reportHash.values()) {
            reports.addAll(parseReportData(value));
        }

        // 4. 上报数据集合为空
        if (reports.isEmpty()) {
            log.error("报警规则过滤结束：Redis中的设备上报数据解析后为空");
            return;
        }
        log.info("报警规则过滤开始：可用规则 {} 条，待过滤上报数据 {} 条", allRules.size(), reports.size());

        LocalDateTime now = LocalDateTime.now();

        // ==================== 二、遍历设备上报数据，获取报警规则 ====================
        for (DeviceData report : reports) {
            // 1. 上报时间超过 1 分钟的数据直接跳过
            if (report.getAlarmTime() == null
                    || report.getAlarmTime().plusMinutes(REPORT_TIMEOUT_MINUTES).isBefore(now)) {
                log.error("报警规则过滤跳过：设备[{}]功能[{}]上报时间[{}]已超过1分钟",
                        report.getIotId(), report.getFunctionId(), report.getAlarmTime());
                continue;
            }

            // 2. 查询满足要求的报警规则（2.1 同产品通配规则 + 2.2 指定设备专属规则）
            Set<AlertRule> matchedRules = new LinkedHashSet<>();
            for (AlertRule rule : allRules) {
                // 2.1 同类型产品下的通配规则：product_key、function_id 匹配，iot_id 为 -1
                boolean wildcardMatch = WILDCARD_IOT_ID.equals(rule.getIotId())
                        && StringUtils.equals(rule.getProductKey(), report.getProductKey())
                        && StringUtils.equals(rule.getFunctionId(), report.getFunctionId());
                // 2.2 该设备的专属规则：product_key、function_id、iot_id 都匹配
                boolean specificMatch = StringUtils.equals(rule.getIotId(), report.getIotId())
                        && StringUtils.equals(rule.getProductKey(), report.getProductKey())
                        && StringUtils.equals(rule.getFunctionId(), report.getFunctionId());
                if (wildcardMatch) {
                    matchedRules.add(rule);
                    log.info("命中通配报警规则：规则[{}]，设备[{}]", rule.getAlertRuleName(), report.getIotId());
                } else if (specificMatch) {
                    matchedRules.add(rule);
                    log.info("命中专属报警规则：规则[{}]，设备[{}]", rule.getAlertRuleName(), report.getIotId());
                }
            }

            // 2.3 去重后集合为空
            if (matchedRules.isEmpty()) {
                log.error("报警规则过滤跳过：设备[{}]功能[{}]没有匹配到有效的报警规则",
                        report.getIotId(), report.getFunctionId());
                continue;
            }

            // ==================== 三、遍历报警规则数据，进行4层校验 ====================
            for (AlertRule rule : matchedRules) {
                // 1. 第一层校验：生效时段
                if (!inEffectivePeriod(rule.getAlertEffectivePeriod(), LocalTime.now())) {
                    log.error("报警规则跳过：规则[{}]当前不在生效时段[{}]内",
                            rule.getAlertRuleName(), rule.getAlertEffectivePeriod());
                    continue;
                }

                // 2. 第二层校验：数据阈值（不是异常数据则清除持续周期计数）
                if (!isAbnormal(report.getDataValue(), rule.getOperator(), rule.getValue())) {
                    stringRedisTemplate.delete(countKey(rule, report));
                    log.error("报警规则跳过：规则[{}]上报值[{}]不属于异常数据，已清除报警次数",
                            rule.getAlertRuleName(), report.getDataValue());
                    continue;
                }

                // 3. 第三层校验：沉默周期
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(silentKey(rule, report)))) {
                    log.error("报警规则跳过：规则[{}]设备[{}]功能[{}]仍在沉默周期内",
                            rule.getAlertRuleName(), report.getIotId(), report.getFunctionId());
                    continue;
                }

                // 4. 第四层校验：持续周期
                long duration = rule.getDuration() == null || rule.getDuration() < 1 ? 1 : rule.getDuration();
                long count = increaseCount(rule, report);
                if (count != duration) {
                    log.error("报警规则跳过：规则[{}]持续次数[{}/{}]未达到持续周期",
                            rule.getAlertRuleName(), count, duration);
                    continue;
                }

                // ==================== 四、校验全部通过，触发报警 ====================
                // 1. 删除报警次数
                stringRedisTemplate.delete(countKey(rule, report));

                // 2. 添加沉默周期
                int silentMinutes = rule.getAlertSilentPeriod() == null ? 0 : rule.getAlertSilentPeriod();
                if (silentMinutes > 0) {
                    stringRedisTemplate.opsForValue()
                            .set(silentKey(rule, report), "1", silentMinutes, TimeUnit.MINUTES);
                }

                // 3. 查询报警接收人用户ID，按接收人批量保存报警数据
                Set<Long> userIds = resolveAlertUserIds(rule, report);
                if (userIds.isEmpty()) {
                    log.error("报警规则过滤结束：规则[{}]设备[{}]功能[{}]未查询到任何报警接收人用户ID，不保存报警数据",
                            rule.getAlertRuleName(), report.getIotId(), report.getFunctionId());
                    return;
                }
                saveAlertDataBatch(rule, report, userIds, silentMinutes);
            }
        }
        log.info("报警规则过滤结束");
    }

    /**
     * 解析 Redis Hash 中某个设备的上报数据（JSON 数组字符串）
     */
    private List<DeviceData> parseReportData(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        String json = value instanceof String ? (String) value : JSONUtil.toJsonStr(value);
        try {
            List<DeviceData> list = JSONUtil.toList(json, DeviceData.class);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            log.error("报警规则过滤：Redis中的设备上报数据解析失败，内容[{}]", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 判断当前时间是否在报警生效时段内，格式 00:00:00~23:59:59；为空视为全天生效
     */
    private boolean inEffectivePeriod(String period, LocalTime now) {
        if (StringUtils.isEmpty(period)) {
            log.info("报警规则过滤：生效时段为空，按全天生效处理");
            return true;
        }
        String[] parts = period.split("[~～]");
        if (parts.length != 2) {
            log.error("报警规则过滤：生效时段[{}]格式不正确，按全天生效处理", period);
            return true;
        }
        LocalTime start = parseTime(parts[0].trim());
        LocalTime end = parseTime(parts[1].trim());
        if (start == null || end == null) {
            log.error("报警规则过滤：生效时段[{}]解析失败，按全天生效处理", period);
            return true;
        }
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        // 跨天时段，例如 22:00:00~06:00:00
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private LocalTime parseTime(String text) {
        try {
            return text.length() > 5 ? LocalTime.parse(text, TIME_FMT) : LocalTime.parse(text, TIME_FMT_SHORT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断上报值是否异常：按规则运算符与阈值比较
     */
    private boolean isAbnormal(String dataValue, String operator, Double threshold) {
        if (StringUtils.isEmpty(dataValue) || StringUtils.isEmpty(operator) || threshold == null) {
            log.error("报警规则过滤：数据值[{}]、运算符[{}]、阈值[{}]不完整，无法比较",
                    dataValue, operator, threshold);
            return false;
        }
        double value;
        try {
            value = Double.parseDouble(dataValue.trim());
        } catch (NumberFormatException e) {
            log.error("报警规则过滤：上报值[{}]不是数字，无法比较阈值", dataValue);
            return false;
        }
        switch (operator.trim()) {
            case ">":
                return value > threshold;
            case ">=":
            case "≥":
                return value >= threshold;
            case "<":
                return value < threshold;
            case "<=":
            case "≤":
                return value <= threshold;
            case "=":
            case "==":
                return Double.compare(value, threshold) == 0;
            case "!=":
            case "≠":
                return Double.compare(value, threshold) != 0;
            default:
                log.error("报警规则过滤：不支持的运算符[{}]", operator);
                return false;
        }
    }

    /**
     * 报警次数自增 1，返回自增后的次数（首次自增时设置过期时间，避免计数长期残留）
     */
    private long increaseCount(AlertRule rule, DeviceData report) {
        String key = countKey(rule, report);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) {
            log.error("报警规则过滤：报警次数自增失败，key[{}]", key);
            return 0;
        }
        if (count == 1L) {
            long duration = rule.getDuration() == null || rule.getDuration() < 1 ? 1 : rule.getDuration();
            stringRedisTemplate.expire(key, duration + 1, TimeUnit.MINUTES);
        }
        return count;
    }

    /**
     * 查询报警接收人用户ID
     * <p>
     * 设备异常数据 → 角色为[行政]的用户；老人异常数据 → 该设备绑定老人的护理员
     * （移动设备走 device→elder→nursing_elder，固定设备走 device→bed→elder→nursing_elder）；
     * 两种情况都要叠加角色为[超级管理员]的用户，最后放进 Set 去重。
     *
     * @param rule   报警规则（取报警数据类型）
     * @param report 上报数据（取设备ID、位置类型）
     * @return 去重后的用户ID集合，可能为空
     */
    private Set<Long> resolveAlertUserIds(AlertRule rule, DeviceData report) {
        Set<Long> userIds = new LinkedHashSet<>();
        Integer alertDataType = rule.getAlertDataType();

        if (alertDataType != null && alertDataType == 1) {
            // 设备异常数据：通知角色为[行政]的用户
            List<Long> adminUserIds = alertRuleMapper.selectUserIdsByRoleName(ROLE_ADMINISTRATIVE);
            if (adminUserIds != null && !adminUserIds.isEmpty()) {
                userIds.addAll(adminUserIds);
            }
            log.info("报警接收人：设备异常数据，角色[{}]查到用户 {} 个", ROLE_ADMINISTRATIVE,
                    adminUserIds == null ? 0 : adminUserIds.size());
        } else {
            // 老人异常数据：通知该设备绑定老人的护理员
            Integer locationType = report.getLocationType();
            List<Long> nursingUserIds;
            if (locationType != null && locationType == 0) {
                nursingUserIds = alertRuleMapper.selectNursingUserIdsByMobileDevice(report.getIotId());
                log.info("报警接收人：老人异常数据，移动设备[{}]查到护理员 {} 个", report.getIotId(),
                        nursingUserIds == null ? 0 : nursingUserIds.size());
            } else {
                if (locationType == null) {
                    log.error("报警接收人：设备[{}]的位置类型为空，按固定设备查询护理员", report.getIotId());
                }
                nursingUserIds = alertRuleMapper.selectNursingUserIdsByFixedDevice(report.getIotId());
                log.info("报警接收人：老人异常数据，固定设备[{}]查到护理员 {} 个", report.getIotId(),
                        nursingUserIds == null ? 0 : nursingUserIds.size());
            }
            if (nursingUserIds != null && !nursingUserIds.isEmpty()) {
                userIds.addAll(nursingUserIds);
            }
        }

        // 无论哪种报警类型，都要加上超级管理员
        List<Long> superAdminUserIds = alertRuleMapper.selectUserIdsByRoleName(ROLE_SUPER_ADMIN);
        if (superAdminUserIds != null && !superAdminUserIds.isEmpty()) {
            userIds.addAll(superAdminUserIds);
        }
        log.info("报警接收人汇总：规则[{}]设备[{}]去重后共 {} 个用户{}",
                rule.getAlertRuleName(), report.getIotId(), userIds.size(), userIds);
        return userIds;
    }

    /**
     * 按接收人批量保存报警数据：一个用户一条报警数据
     *
     * @param rule          命中的报警规则
     * @param report        触发报警的上报数据
     * @param userIds       接收人用户ID集合
     * @param silentMinutes 沉默周期（分钟），仅用于日志
     */
    private void saveAlertDataBatch(AlertRule rule, DeviceData report, Set<Long> userIds, int silentMinutes) {
        List<AlertData> alertDataList = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            alertDataList.add(buildAlertData(rule, report, userId));
        }
        try {
            if (alertDataService.saveBatch(alertDataList)) {
                log.info("报警触发成功：规则[{}]，设备[{}]，功能[{}]，上报值[{}]，沉默{}分钟，按{}个接收人批量保存报警数据{}条",
                        rule.getAlertRuleName(), report.getIotId(), report.getFunctionId(),
                        report.getDataValue(), silentMinutes, userIds.size(), alertDataList.size());
            } else {
                log.error("报警触发失败：报警数据批量保存失败，规则[{}]，设备[{}]，接收人{}个",
                        rule.getAlertRuleName(), report.getIotId(), userIds.size());
            }
        } catch (Exception e) {
            log.error("报警触发失败：批量保存报警数据异常，规则[{}]，设备[{}]",
                    rule.getAlertRuleName(), report.getIotId(), e);
        }
    }

    /**
     * 组装一条报警数据（接收人 userId）
     */
    private AlertData buildAlertData(AlertRule rule, DeviceData report, Long userId) {
        AlertData alertData = new AlertData();
        alertData.setIotId(report.getIotId());
        alertData.setDeviceName(report.getDeviceName());
        alertData.setProductKey(report.getProductKey());
        alertData.setProductName(report.getProductName());
        alertData.setFunctionId(report.getFunctionId());
        alertData.setAccessLocation(report.getAccessLocation());
        alertData.setLocationType(report.getLocationType());
        alertData.setPhysicalLocationType(report.getPhysicalLocationType());
        alertData.setDeviceDescription(report.getDeviceDescription());
        alertData.setDataValue(StringUtils.substring(report.getDataValue(), 0, 100));
        alertData.setAlertRuleId(rule.getId());
        alertData.setType(rule.getAlertDataType() == null ? 0 : rule.getAlertDataType());
        alertData.setStatus(0);
        alertData.setUserId(userId);
        alertData.setAlertReason(buildAlertReason(rule));
        alertData.setCreateTime(DateUtils.getNowDate());
        return alertData;
    }

    /**
     * 报警原因：功能名称 + 运算符 + 阈值 + 持续周期 + 聚合周期
     */
    private String buildAlertReason(AlertRule rule) {
        String functionName = StringUtils.isNotEmpty(rule.getFunctionName())
                ? rule.getFunctionName() : rule.getFunctionId();
        long duration = rule.getDuration() == null || rule.getDuration() < 1 ? 1 : rule.getDuration();
        return String.format("%s%s%s，持续%d个周期，聚合周期1分钟",
                functionName, rule.getOperator(), rule.getValue(), duration);
    }

    private String countKey(AlertRule rule, DeviceData report) {
        return CacheConstants.ALERT_COUNT_KEY + rule.getId() + ":" + report.getIotId() + ":" + report.getFunctionId();
    }

    private String silentKey(AlertRule rule, DeviceData report) {
        return CacheConstants.ALERT_SILENT_KEY + rule.getId() + ":" + report.getIotId() + ":" + report.getFunctionId();
    }
}
