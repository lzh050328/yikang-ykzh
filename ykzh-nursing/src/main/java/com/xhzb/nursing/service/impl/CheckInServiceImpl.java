package com.xhzb.nursing.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhzb.common.exception.base.BaseException;
import com.xhzb.common.utils.SecurityUtils;
import com.xhzb.common.utils.StringUtils;
import com.xhzb.nursing.domain.Bed;
import com.xhzb.nursing.domain.CheckIn;
import com.xhzb.nursing.domain.CheckInConfig;
import com.xhzb.nursing.domain.Contract;
import com.xhzb.nursing.domain.Elder;
import com.xhzb.nursing.domain.HealthAssessment;
import com.xhzb.nursing.domain.HealthAssessmentReport;
import com.xhzb.nursing.domain.NursingLevel;
import com.xhzb.nursing.domain.dto.checkin.CheckInApplyDto;
import com.xhzb.nursing.domain.dto.checkin.CheckInConfigApplyDto;
import com.xhzb.nursing.domain.dto.checkin.CheckInContractDto;
import com.xhzb.nursing.domain.dto.checkin.ElderFamilyDto;
import com.xhzb.nursing.domain.vo.CheckInConfigVo;
import com.xhzb.nursing.domain.vo.CheckInDetailVo;
import com.xhzb.nursing.mapper.CheckInMapper;
import com.xhzb.nursing.mapper.HealthAssessmentReportMapper;
import com.xhzb.nursing.service.IBedService;
import com.xhzb.nursing.service.ICheckInConfigService;
import com.xhzb.nursing.service.ICheckInService;
import com.xhzb.nursing.service.IContractService;
import com.xhzb.nursing.service.IElderService;
import com.xhzb.nursing.service.IHealthAssessmentService;
import com.xhzb.nursing.service.INursingLevelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.Arrays;
import java.util.Date;

/**
 * 入住信息Service业务层处理
 * 
 * @author lzh
 * @date 2026-10-30
 */
@Slf4j
@Service
public class CheckInServiceImpl extends ServiceImpl<CheckInMapper, CheckIn> implements ICheckInService
{
    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private IHealthAssessmentService healthAssessmentService;

    @Autowired
    private HealthAssessmentReportMapper healthAssessmentReportMapper;

    @Autowired
    private IElderService elderService;

    @Autowired
    private IBedService bedService;

    @Autowired
    private IContractService contractService;

    @Autowired
    private ICheckInConfigService checkInConfigService;

    @Autowired
    private INursingLevelService nursingLevelService;

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 查询入住信息
     * 
     * @param id 入住信息主键
     * @return 入住信息
     */
    @Override
    public CheckIn selectCheckInById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询入住信息列表
     * 
     * @param checkIn 入住信息
     * @return 入住信息
     */
    @Override
    public List<CheckIn> selectCheckInList(CheckIn checkIn)
    {
        return checkInMapper.selectCheckInList(checkIn);
    }

    /**
     * 新增入住信息
     * 
     * @param checkIn 入住信息
     * @return 结果
     */
    @Override
    public int insertCheckIn(CheckIn checkIn)
    {
        return save(checkIn)? 1 : 0;
    }

    /**
     * 修改入住信息
     * 
     * @param checkIn 入住信息
     * @return 结果
     */
    @Override
    public int updateCheckIn(CheckIn checkIn)
    {
        return updateById(checkIn)? 1 : 0;
    }

    /**
     * 批量删除入住信息
     * 
     * @param ids 需要删除的入住信息主键
     * @return 结果
     */
    @Override
    public int deleteCheckInByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids))? 1 : 0;
    }

    /**
     * 删除入住信息信息
     * 
     * @param id 入住信息主键
     * @return 结果
     */
    @Override
    public int deleteCheckInById(Long id)
    {
        return removeById(id)? 1 : 0;
    }

    /**
     * 申请入住
     * <p>
     * 业务步骤：校验评估完成 → 校验老人未入住 → 校验床位空闲 → 占床 → 老人建档/更新
     * → 新增合同 → 新增入住（家属JSON存remark）→ 新增入住配置 → 回写评估表关联老人
     * → 评估结果表置为已入住
     *
     * @param dto 申请入住入参
     * @return 入住记录ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyCheckIn(CheckInApplyDto dto)
    {
        // ---------- 入参校验 ----------
        if (dto == null || dto.getHealthAssessmentId() == null) {
            throw new BaseException("健康评估ID不能为空");
        }
        Elder elderDto = dto.getCheckInElderDto();
        if (elderDto == null) {
            throw new BaseException("老人基本信息不能为空");
        }
        if (StringUtils.isEmpty(elderDto.getName())) {
            throw new BaseException("老人姓名不能为空");
        }
        if (StringUtils.isEmpty(elderDto.getIdCardNo())) {
            throw new BaseException("老人身份证号不能为空");
        }
        CheckInConfigApplyDto cfgDto = dto.getCheckInConfigDto();
        if (cfgDto == null) {
            throw new BaseException("入住配置不能为空");
        }

        // ---------- 1. 判断评估是否完成 ----------
        HealthAssessment assessment = healthAssessmentService.getById(dto.getHealthAssessmentId());
        if (assessment == null) {
            throw new BaseException("健康评估记录不存在");
        }
        if (assessment.getEvaluationProgress() == null || assessment.getEvaluationProgress() != 1) {
            throw new BaseException("未完成评估不能入住");
        }
        // 防重复提交第二层：这份评估已经办理过入住（10 秒窗口之后重复点、或换个床位再提交也拦住）
        if (assessment.getCheckInStatus() != null && assessment.getCheckInStatus() == 1) {
            throw new BaseException("该评估已办理入住，请勿重复提交");
        }

        // ---------- 2. 校验老人是否已入住 ----------
        long activeCount = this.count(new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getIdCardNo, elderDto.getIdCardNo())
                .eq(CheckIn::getStatus, 0));
        if (activeCount > 0) {
            throw new BaseException("老人已入住");
        }

        // ---------- 3. 判断床位是否已被入住 ----------
        Bed bed = null;
        if (cfgDto.getBedId() != null) {
            bed = bedService.getById(cfgDto.getBedId());
        }
        if (bed == null && StringUtils.isNotEmpty(cfgDto.getBedNumber())) {
            bed = bedService.getOne(new LambdaQueryWrapper<Bed>()
                    .eq(Bed::getBedNumber, cfgDto.getBedNumber()).last("limit 1"), false);
        }
        if (bed == null) {
            throw new BaseException("床位不存在");
        }
        if (bed.getBedStatus() != null && bed.getBedStatus() == 1) {
            throw new BaseException("有其他老人已入住");
        }

        String userId = SecurityUtils.getLoginUser().getUserId().toString();
        Date now = new Date();

        // ---------- 4. 更新床位状态为已入住 ----------
        bed.setBedStatus(1);
        bed.setUpdateTime(now);
        bed.setUpdateBy(userId);
        bedService.updateById(bed);

        // ---------- 5. 新增或更新老人（同一身份证视为同一老人） ----------
        Elder elder = elderService.getOne(new LambdaQueryWrapper<Elder>()
                .eq(Elder::getIdCardNo, elderDto.getIdCardNo()).last("limit 1"), false);
        boolean isNewElder = (elder == null);
        if (isNewElder) {
            elder = new Elder();
        }
        copyElderProfile(elderDto, elder);
        elder.setBedId(bed.getId());
        elder.setBedNumber(bed.getBedNumber());
        if (elder.getStatus() == null) {
            elder.setStatus(1);
        }
        if (isNewElder) {
            elder.setCreateTime(now);
            elder.setCreateBy(userId);
            elderService.save(elder);
        } else {
            elder.setUpdateTime(now);
            elder.setUpdateBy(userId);
            elderService.updateById(elder);
        }

        // ---------- 6. 新增签约合同 ----------
        CheckInContractDto contractDto = dto.getCheckInContractDto();
        Contract contract = new Contract();
        contract.setElderId(elder.getId().intValue());
        contract.setElderName(elder.getName());
        contract.setContractName(contractDto != null && StringUtils.isNotEmpty(contractDto.getContractName())
                ? contractDto.getContractName() : "养老机构入住服务协议");
        contract.setContractNumber(buildContractNumber());
        String agreementPath = contractDto == null ? null : contractDto.getAgreementPath();
        contract.setAgreementPath(StringUtils.isEmpty(agreementPath) ? "" : truncate(agreementPath, 255));
        contract.setThirdPartyName(contractDto == null ? null : contractDto.getThirdPartyName());
        contract.setThirdPartyPhone(contractDto == null ? null : contractDto.getThirdPartyPhone());
        LocalDateTime signDate = parseDateTime(contractDto == null ? null : contractDto.getSignDate());
        if (signDate == null) {
            signDate = LocalDateTime.now();
        }
        contract.setSignDate(signDate);
        contract.setStartDate(parseDateTime(cfgDto.getStartDate()));
        contract.setEndDate(parseDateTime(cfgDto.getEndDate()));
        // 签约日期 <= 当前时间 → 已生效(1)，否则未生效(0)
        contract.setStatus(signDate.isAfter(LocalDateTime.now()) ? 0 : 1);
        contract.setSortOrder(1);
        contract.setCreateTime(now);
        contract.setCreateBy(userId);
        contractService.save(contract);

        // ---------- 7. 新增入住（家属信息转 JSON 存 remark） ----------
        CheckIn checkIn = new CheckIn();
        checkIn.setElderName(elder.getName());
        checkIn.setElderId(elder.getId());
        checkIn.setIdCardNo(elder.getIdCardNo());
        checkIn.setStartDate(parseDateTime(cfgDto.getStartDate()));
        checkIn.setEndDate(parseDateTime(cfgDto.getEndDate()));
        checkIn.setNursingLevelName(resolveNursingLevelName(cfgDto));
        checkIn.setBedNumber(StringUtils.isNotEmpty(cfgDto.getBedNumber()) ? cfgDto.getBedNumber() : bed.getBedNumber());
        checkIn.setStatus(0);
        checkIn.setSortOrder(1);
        checkIn.setCreateTime(now);
        checkIn.setCreateBy(userId);
        checkIn.setRemark(dto.getElderFamilyDtoList() == null
                ? "[]" : JSONUtil.toJsonStr(dto.getElderFamilyDtoList()));
        this.save(checkIn);

        // ---------- 8. 新增入住配置 ----------
        CheckInConfig config = new CheckInConfig();
        config.setCheckInId(checkIn.getId());
        config.setNursingLevelId(cfgDto.getNursingLevelId());
        config.setNursingLevelName(checkIn.getNursingLevelName());
        LocalDateTime feeStart = parseDateTime(cfgDto.getFeeStartDate());
        config.setFeeStartDate(feeStart != null ? feeStart : checkIn.getStartDate());
        config.setFeeEndDate(parseDateTime(cfgDto.getFeeEndDate()));
        config.setDeposit(nvl(cfgDto.getDeposit()));
        config.setNursingFee(nvl(cfgDto.getNursingFee()));
        config.setBedFee(nvl(cfgDto.getBedFee()));
        config.setInsurancePayment(nvl(cfgDto.getInsurancePayment()));
        config.setGovernmentSubsidy(nvl(cfgDto.getGovernmentSubsidy()));
        config.setOtherFees(nvl(cfgDto.getOtherFees()));
        config.setSortOrder(1);
        config.setCreateTime(now);
        config.setCreateBy(userId);
        checkInConfigService.save(config);

        // ---------- 9. 修改入住评估表，并做老人关联 ----------
        assessment.setElderId(elder.getId());
        assessment.setCheckInStatus(1);
        assessment.setUpdateTime(now);
        assessment.setUpdateBy(userId);
        healthAssessmentService.updateById(assessment);

        // ---------- 10. 修改入住评估结果表，改为已入住 ----------
        HealthAssessmentReport report = healthAssessmentReportMapper.selectOne(
                new LambdaQueryWrapper<HealthAssessmentReport>()
                        .eq(HealthAssessmentReport::getHealthAssessmentId, dto.getHealthAssessmentId())
                        .orderByDesc(HealthAssessmentReport::getId)
                        .last("limit 1"));
        if (report != null) {
            report.setCheckInStatus(1);
            report.setUpdateTime(now);
            report.setUpdateBy(userId);
            healthAssessmentReportMapper.updateById(report);
        }

        return checkIn.getId();
    }

    /**
     * 查询入住详情（老人 + 家属 + 入住配置 + 合同）
     *
     * @param id 入住记录ID
     * @return 入住详情
     */
    @Override
    public CheckInDetailVo selectCheckInDetail(Long id)
    {
        if (id == null) {
            throw new BaseException("入住记录ID不能为空");
        }
        CheckIn checkIn = getById(id);
        if (checkIn == null) {
            throw new BaseException("入住记录不存在");
        }
        CheckInDetailVo vo = new CheckInDetailVo();

        // 老人信息
        Elder elder = checkIn.getElderId() == null ? null : elderService.getById(checkIn.getElderId());
        vo.setCheckInElderVo(elder);

        // 家属信息（申请入住时以 JSON 存在 remark）
        List<ElderFamilyDto> familyList = new ArrayList<>();
        if (StringUtils.isNotEmpty(checkIn.getRemark()) && checkIn.getRemark().trim().startsWith("[")) {
            try {
                familyList = JSONUtil.toList(checkIn.getRemark(), ElderFamilyDto.class);
            } catch (Exception e) {
                log.warn("解析入住记录 {} 的家属信息失败：{}", id, e.getMessage());
            }
        }
        vo.setElderFamilyVoList(familyList);

        // 入住配置
        CheckInConfig config = checkInConfigService.getOne(new LambdaQueryWrapper<CheckInConfig>()
                .eq(CheckInConfig::getCheckInId, id)
                .orderByDesc(CheckInConfig::getId)
                .last("limit 1"), false);
        if (config != null) {
            CheckInConfigVo configVo = new CheckInConfigVo();
            BeanUtils.copyProperties(config, configVo);
            configVo.setStartDate(formatDate(checkIn.getStartDate()));
            configVo.setEndDate(formatDate(checkIn.getEndDate()));
            configVo.setBedNumber(checkIn.getBedNumber());
            vo.setCheckInConfigVo(configVo);
        }

        // 合同（按老人取最新一条）
        if (checkIn.getElderId() != null) {
            Contract contract = contractService.getOne(new LambdaQueryWrapper<Contract>()
                    .eq(Contract::getElderId, checkIn.getElderId().intValue())
                    .orderByDesc(Contract::getId)
                    .last("limit 1"), false);
            vo.setContract(contract);
        }
        return vo;
    }

    /**
     * 生成合同号：HT + yyyyMMddHHmmss + 4位当日递增序号
     */
    private String buildContractNumber()
    {
        LocalDateTime now = LocalDateTime.now();
        String dayPrefix = "HT" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long todayCount = contractService.count(new LambdaQueryWrapper<Contract>()
                .likeRight(Contract::getContractNumber, dayPrefix));
        String seq = String.format("%04d", todayCount + 1);
        return dayPrefix + now.format(DateTimeFormatter.ofPattern("HHmmss")) + seq;
    }

    /**
     * 护理等级名称：优先取入参，其次按ID查库
     */
    private String resolveNursingLevelName(CheckInConfigApplyDto cfgDto)
    {
        if (StringUtils.isNotEmpty(cfgDto.getNursingLevelName())) {
            return cfgDto.getNursingLevelName();
        }
        if (cfgDto.getNursingLevelId() != null) {
            NursingLevel level = nursingLevelService.getById(cfgDto.getNursingLevelId());
            if (level != null) {
                return level.getName();
            }
        }
        throw new BaseException("护理等级不能为空");
    }

    /**
     * 把前端提交的老人信息复制到实体（只覆盖资料字段，不动主键/创建信息）
     */
    private void copyElderProfile(Elder source, Elder target)
    {
        target.setName(source.getName());
        target.setIdCardNo(source.getIdCardNo());
        target.setSex(source.getSex());
        target.setPhone(source.getPhone());
        target.setBirthday(source.getBirthday());
        target.setAddress(source.getAddress());
        target.setImage(source.getImage());
        target.setIdCardNationalEmblemImg(source.getIdCardNationalEmblemImg());
        target.setIdCardPortraitImg(source.getIdCardPortraitImg());
        target.setNation(source.getNation());
        target.setEducationLevel(source.getEducationLevel());
        target.setSocialSecurityCard(source.getSocialSecurityCard());
        target.setLivingSituation(source.getLivingSituation());
        target.setReligiousBelief(source.getReligiousBelief());
        target.setEconomicSource(source.getEconomicSource());
        target.setMaritalStatus(source.getMaritalStatus());
        target.setMedicalPaymentMethod(source.getMedicalPaymentMethod());
        target.setCoreSuggestion(source.getCoreSuggestion());
    }

    private BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String truncate(String value, int maxLength)
    {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 解析前端传来的日期字符串（兼容 yyyy-MM-dd HH:mm:ss / yyyy-MM-dd'T'HH:mm:ss / yyyy-MM-dd）
     */
    private LocalDateTime parseDateTime(String value)
    {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        String text = value.trim();
        // 兼容 ISO 带时区或毫秒的写法，只取前 19 位
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text, DATE_FMT).atStartOfDay();
            }
            return LocalDateTime.parse(text, DATE_TIME_FMT);
        } catch (Exception e) {
            log.warn("无法解析日期：{}", value);
            return null;
        }
    }

    private String formatDate(LocalDateTime value)
    {
        return value == null ? null : value.format(DATE_FMT);
    }
}
