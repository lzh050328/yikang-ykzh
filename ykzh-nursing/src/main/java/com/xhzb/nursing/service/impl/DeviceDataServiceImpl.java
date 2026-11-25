package com.xhzb.nursing.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xhzb.common.constant.CacheConstants;
import com.xhzb.common.utils.DateUtils;
import com.xhzb.common.utils.StringUtils;
import com.xhzb.nursing.domain.Device;
import com.xhzb.nursing.service.IDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xhzb.nursing.mapper.DeviceDataMapper;
import com.xhzb.nursing.domain.DeviceData;
import com.xhzb.nursing.service.IDeviceDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.Arrays;

/**
 * 设备数据管理Service业务层处理
 * 
 * @author lzh
 * @date 2026-11-03
 */
@Service
public class DeviceDataServiceImpl extends ServiceImpl<DeviceDataMapper, DeviceData> implements IDeviceDataService
{
    private static final Logger log = LoggerFactory.getLogger(DeviceDataServiceImpl.class);

    /** 北京时间时区 */
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    /** 设备上报时间格式，例如 20260606T111615Z（UTC） */
    private static final DateTimeFormatter EVENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 查询设备数据管理
     * 
     * @param id 设备数据管理主键
     * @return 设备数据管理
     */
    @Override
    public DeviceData selectDeviceDataById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询设备数据管理列表
     * 
     * @param deviceData 设备数据管理
     * @return 设备数据管理
     */
    @Override
    public List<DeviceData> selectDeviceDataList(DeviceData deviceData)
    {
        return deviceDataMapper.selectDeviceDataList(deviceData);
    }

    /**
     * 新增设备数据管理
     * 
     * @param deviceData 设备数据管理
     * @return 结果
     */
    @Override
    public int insertDeviceData(DeviceData deviceData)
    {
        return save(deviceData)? 1 : 0;
    }

    /**
     * 修改设备数据管理
     * 
     * @param deviceData 设备数据管理
     * @return 结果
     */
    @Override
    public int updateDeviceData(DeviceData deviceData)
    {
        return updateById(deviceData)? 1 : 0;
    }

    /**
     * 批量删除设备数据管理
     * 
     * @param ids 需要删除的设备数据管理主键
     * @return 结果
     */
    @Override
    public int deleteDeviceDataByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids))? 1 : 0;
    }

    /**
     * 删除设备数据管理信息
     * 
     * @param id 设备数据管理主键
     * @return 结果
     */
    @Override
    public int deleteDeviceDataById(Long id)
    {
        return removeById(id)? 1 : 0;
    }

    /**
     * 处理 IoTDA 平台推送的设备属性上报消息，把上报的属性值批量写入 device_data 表
     * <p>
     * 消息示例：
     * {"resource":"device.property","event":"report","event_time":"20260606T111615Z","event_time_ms":"...",
     *  "request_id":"...","notify_data":{"header":{"device_id":"...","node_id":"...","product_id":"..."},
     *  "body":{"services":[{"service_id":"...","properties":{...},"event_time":"20260606T111615Z"}]}}}
     *
     * @param contentStr AMQP 消息内容（JSON）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDeviceReportData(String contentStr) {
        if (StringUtils.isBlank(contentStr)) {
            return;
        }
        JSONObject content;
        try {
            content = JSONUtil.parseObj(contentStr);
        } catch (Exception e) {
            log.warn("设备上报数据不是合法JSON，忽略。content：{}", contentStr);
            return;
        }

        JSONObject notifyData = content.getJSONObject("notify_data");
        if (notifyData == null) {
            return;
        }
        JSONObject header = notifyData.getJSONObject("header");
        JSONObject body = notifyData.getJSONObject("body");
        if (header == null || body == null) {
            return;
        }

        // 2. 解析 device_id
        String deviceId = header.getStr("device_id");
        if (StringUtils.isBlank(deviceId)) {
            return;
        }

        // 3. 设备不存在直接返回
        Device device = deviceService.lambdaQuery().eq(Device::getIotId, deviceId).one();
        if (device == null) {
            log.info("设备未在本地注册，忽略上报数据，deviceId：{}", deviceId);
            return;
        }

        JSONArray services = body.getJSONArray("services");
        if (services == null || services.isEmpty()) {
            return;
        }

        List<DeviceData> dataList = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            JSONObject service = services.getJSONObject(i);
            // 优先用服务级 event_time，没有则用消息根节点的
            String eventTime = service.getStr("event_time", content.getStr("event_time"));
            LocalDateTime reportTime = toBeijingTime(eventTime);

            JSONObject properties = service.getJSONObject("properties");
            if (properties == null || properties.isEmpty()) {
                continue;
            }
            // 4. 一个属性一条记录，批量保存
            for (String functionId : properties.keySet()) {
                dataList.add(buildDeviceData(device, functionId, properties.getStr(functionId), reportTime));
            }
        }

        if (dataList.isEmpty()) {
            return;
        }
        saveDeviceReports(dataList, deviceId);
    }

    /**
     * 保存一次设备上报的数据：MySQL 存历史（device_data），Redis 存最新
     * <p>
     * Redis 使用 Hash 结构，大 key 固定为 {@link CacheConstants#IOT_DEVICE_DATA_KEY}，
     * 小 key 为设备ID，value 为本次上报的全部属性（JSON 数组），
     * 每次上报直接覆盖，所以 Redis 里永远是该设备最新一次上报的数据。
     *
     * @param dataList 本次上报解析出的设备数据（一个属性一条）
     * @param deviceId 物联网设备ID
     */
    private void saveDeviceReports(List<DeviceData> dataList, String deviceId) {
        saveBatch(dataList);
        log.info("设备上报数据入库成功，deviceId：{}，共 {} 条", deviceId, dataList.size());
        try {
            redisTemplate.opsForHash().put(CacheConstants.IOT_DEVICE_DATA_KEY, deviceId, JSONUtil.toJsonStr(dataList));
        } catch (Exception e) {
            // Redis 写失败不影响入库，页面数据会回落到数据库
            log.error("设备上报数据写入Redis失败，deviceId：{}", deviceId, e);
        }
    }

    /**
     * 组装一条设备数据（属性值 + 设备位置信息 + 上报时间）
     */
    private DeviceData buildDeviceData(Device device, String functionId, String value, LocalDateTime reportTime) {
        DeviceData data = new DeviceData();
        data.setIotId(device.getIotId());
        data.setDeviceName(device.getDeviceName());
        data.setProductKey(device.getProductKey());
        data.setProductName(device.getProductName());
        data.setFunctionId(functionId);
        data.setAccessLocation(device.getBindingLocation());
        data.setLocationType(device.getLocationType());
        data.setPhysicalLocationType(device.getPhysicalLocationType());
        data.setDeviceDescription(device.getDeviceDescription());
        // data_value 字段长度 50
        data.setDataValue(StringUtils.substring(value, 0, 50));
        data.setAlarmTime(reportTime);
        data.setCreateTime(DateUtils.getNowDate());
        return data;
    }

    /**
     * 上报时间由 UTC 转成北京时间的 LocalDateTime
     *
     * @param eventTime 例如 20260606T111615Z
     * @return 北京时间，解析失败返回 null
     */
    private LocalDateTime toBeijingTime(String eventTime) {
        if (StringUtils.isBlank(eventTime)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(eventTime, EVENT_TIME_FORMAT)
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("设备上报时间解析失败，eventTime：{}", eventTime);
            return null;
        }
    }

    /**
     * 小程序端按天查询：按小时聚合，固定返回 24 个点
     */
    @Override
    public List<Map<String, Object>> selectAvgDataByHour(String iotId, String functionId,
                                                         Long startTime, Long endTime) {
        LocalDateTime end = endTime == null
                ? LocalDate.now(BEIJING_ZONE).atTime(23, 59, 59)
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), BEIJING_ZONE);
        LocalDateTime start = startTime == null
                ? end.toLocalDate().atStartOfDay()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), BEIJING_ZONE);

        Map<String, Object> valueMap = toLabelValueMap(
                baseMapper.selectAvgGroupByHour(iotId, functionId, start, end));

        List<Map<String, Object>> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            String label = String.format("%02d:00", hour);
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("dateTime", label);
            item.put("dataValue", valueMap.getOrDefault(label, 0D));
            result.add(item);
        }
        return result;
    }

    /**
     * 小程序端按周查询：按天聚合，固定返回 7 个点
     */
    @Override
    public List<Map<String, Object>> selectAvgDataByDay(String iotId, String functionId,
                                                        Long startTime, Long endTime) {
        LocalDateTime end = endTime == null
                ? LocalDate.now(BEIJING_ZONE).atTime(23, 59, 59)
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), BEIJING_ZONE);
        LocalDateTime start = startTime == null
                ? end.toLocalDate().minusDays(6).atStartOfDay()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), BEIJING_ZONE);

        Map<String, Object> valueMap = toLabelValueMap(
                baseMapper.selectAvgGroupByDay(iotId, functionId, start, end));

        DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("MM.dd");
        List<Map<String, Object>> result = new ArrayList<>(7);
        LocalDate startDate = start.toLocalDate();
        for (int i = 0; i < 7; i++) {
            LocalDate day = startDate.plusDays(i);
            String label = day.format(dayFormat);
            Map<String, Object> item = new LinkedHashMap<>(2);
            item.put("dateTime", label);
            item.put("dataValue", valueMap.getOrDefault(label, 0L));
            result.add(item);
        }
        return result;
    }

    /**
     * 把 [{label, val}] 转成 label -> value 的映射
     */
    private Map<String, Object> toLabelValueMap(List<Map<String, Object>> rows) {
        Map<String, Object> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object label = row.get("label");
            if (label == null) {
                continue;
            }
            Object val = row.get("val");
            if (val instanceof Number number) {
                map.put(String.valueOf(label), number.doubleValue());
            }
        }
        return map;
    }
}
