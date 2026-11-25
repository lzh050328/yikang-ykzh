package com.xhzb.nursing.service.impl;

import java.io.InputStream;
import java.util.Date;
import java.util.List;

import cn.hutool.json.JSONUtil;
import com.xhzb.common.core.domain.entity.SysUser;
import com.xhzb.common.exception.base.BaseException;
import com.xhzb.common.utils.DateUtils;
import com.xhzb.common.utils.SecurityUtils;
import com.xhzb.common.utils.StringUtils;
import com.xhzb.nursing.constnat.SystemConstant;
import com.xhzb.nursing.domain.HealthAssessmentDataCollection;
import com.xhzb.nursing.domain.dto.health.BasicInfo;
import com.xhzb.nursing.domain.dto.health.DailyLivingActivities;
import com.xhzb.nursing.domain.dto.health.ElderAssessmentDto;
import com.xhzb.nursing.domain.dto.health.HealthAssessmentDto;
import com.xhzb.nursing.domain.dto.health.MentalState;
import com.xhzb.nursing.domain.dto.health.PerceptionAndCommunication;
import com.xhzb.nursing.domain.dto.health.SocialParticipation;
import lombok.extern.slf4j.Slf4j;
import com.xhzb.nursing.domain.vo.ElderInfoVO;
import com.xhzb.nursing.domain.vo.ElderLevelResultVO;
import com.xhzb.nursing.domain.vo.HealthReportResultVO;
import com.xhzb.nursing.service.IHealthAssessmentDataCollectionService;
import com.xhzb.nursing.service.IHealthAssessmentReportService;
import com.xhzb.oss.client.OSSAliyunFileStorageService;
import com.xhzb.nursing.util.PDFUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xhzb.nursing.mapper.HealthAssessmentMapper;
import com.xhzb.nursing.domain.HealthAssessment;
import com.xhzb.nursing.service.IHealthAssessmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhzb.nursing.domain.HealthAssessmentReport;
import com.xhzb.nursing.mapper.HealthAssessmentReportMapper;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 健康评估记录Service业务层处理
 * 
 * @author lzh
 * @date 2026-10-28
 */
@Slf4j
@Service
public class HealthAssessmentServiceImpl extends ServiceImpl<HealthAssessmentMapper, HealthAssessment> implements IHealthAssessmentService
{
    @Autowired
    private HealthAssessmentMapper healthAssessmentMapper;
    @Autowired
    private IHealthAssessmentDataCollectionService iHealthAssessmentDataCollectionService;
    @Autowired
    private HealthAssessmentReportMapper healthAssessmentReportMapper;
    @Autowired
    private OSSAliyunFileStorageService oSSAliyunFileStorageService;

    /**
     * 查询健康评估记录
     * 
     * @param id 健康评估记录主键
     * @return 健康评估记录
     */
    @Override
    public HealthAssessmentDataCollection  selectHealthAssessmentById(Long id)
    {
        return iHealthAssessmentDataCollectionService.getById(id);
    }

    /**
     * 查询健康评估记录列表
     * 
     * @param healthAssessment 健康评估记录
     * @return 健康评估记录
     */
    @Override
    public List<HealthAssessment> selectHealthAssessmentList(HealthAssessment healthAssessment)
    {
        return healthAssessmentMapper.selectHealthAssessmentList(healthAssessment);
    }

    /**
     * 新增健康评估记录
     * 
     * @param dto 健康评估记录
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertHealthAssessment(ElderAssessmentDto dto)
    {
        // 入参校验：主表和采集表都依赖姓名、身份证（数据库均为 NOT NULL）
        BasicInfo basicInfo = dto.getBasicInfo();
        if (basicInfo == null) {
            throw new BaseException("基本信息不能为空");
        }
        if (StringUtils.isEmpty(basicInfo.getElderName())) {
            throw new BaseException("老人姓名不能为空");
        }
        if (StringUtils.isEmpty(basicInfo.getIdCard())) {
            throw new BaseException("身份证号不能为空");
        }

        Long returnId;
        if(dto.getId() == null){
            HealthAssessment healthAssessment = new HealthAssessment();
            healthAssessment.setElderName(basicInfo.getElderName());
            healthAssessment.setIdCard(basicInfo.getIdCard());
            healthAssessment.setEvaluationProgress(0);
            // 入住情况：0=未入住（前端用 checkInStatus === 0 判断未入住，为 null 会被显示成"已入住"）
            healthAssessment.setCheckInStatus(0);
            healthAssessment.setCreateTime(new Date());
            healthAssessment.setCreateBy(SecurityUtils.getLoginUser().getUserId().toString());
            this.save(healthAssessment);
            returnId = healthAssessment.getId();
        }else {
            HealthAssessment assessment = getById(dto.getId());
            if (assessment == null){
                throw new BaseException("ID参数非法，记录不存在");
            }
            returnId = assessment.getId();
            assessment.setElderName(basicInfo.getElderName());
            assessment.setIdCard(basicInfo.getIdCard());
            this.updateById(assessment);
        }
        HealthAssessmentDataCollection collection = new HealthAssessmentDataCollection();
        collection.setId(returnId);
        collection.setBasicInfo(JSONUtil.toJsonStr(basicInfo));
        collection.setHealthAssessment(JSONUtil.toJsonStr(dto.getHealthAssessmentDto()));
        collection.setDailyLivingActivities(JSONUtil.toJsonStr(dto.getDailyLivingActivities()));
        collection.setMentalState(JSONUtil.toJsonStr(dto.getMentalState()));
        collection.setPerceptionCommunication(JSONUtil.toJsonStr(dto.getPerceptionAndCommunication()));
        collection.setSocialParticipation(JSONUtil.toJsonStr(dto.getSocialParticipation()));
        if(dto.getId()==null){
            collection.setCreateTime(new Date());
            collection.setCreateBy(SecurityUtils.getLoginUser().getUserId().toString());
        }
        iHealthAssessmentDataCollectionService.saveOrUpdate(collection);
        return returnId;
    }

    /**
     * 修改健康评估记录
     * 
     * @param dto 健康评估记录
     * @return 结果
     */
//    @Override
//    public HealthAssessmentDataCollection  updateHealthAssessment(ElderAssessmentDto dto)
//    {
//        return iHealthAssessmentDataCollectionService.getById(dto.getId());
//    }
//    }

    /**
     * 取消健康评估（评估中 -> 已取消）
     *
     * @param id 健康评估记录主键
     * @return 主键ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long cancelHealthAssessment(Long id)
    {
        HealthAssessment assessment = getById(id);
        if (assessment == null) {
            throw new BaseException("健康评估记录不存在");
        }
        Integer progress = assessment.getEvaluationProgress();
        if (progress != null && progress == 2) {
            throw new BaseException("该评估已取消，无需重复操作");
        }
        if (progress != null && progress == 1) {
            throw new BaseException("该评估已完成，不能取消");
        }
        assessment.setEvaluationProgress(2); // 2 = 已取消（字典 evaluation_progress）
        assessment.setUpdateTime(new Date());
        assessment.setUpdateBy(SecurityUtils.getLoginUser().getUserId().toString());
        this.updateById(assessment);
        return id;
    }

    /**
     * 批量删除健康评估记录（级联删除采集数据和评估报告）
     *
     * @param ids 需要删除的健康评估记录主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteHealthAssessmentByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        List<Long> idList = Arrays.asList(ids);
        // 先删子表，再删主表，避免留下孤儿数据
        iHealthAssessmentDataCollectionService.removeByIds(idList);
        healthAssessmentReportMapper.delete(new LambdaQueryWrapper<HealthAssessmentReport>()
                .in(HealthAssessmentReport::getHealthAssessmentId, idList));
        return this.removeByIds(idList) ? 1 : 0;
    }

    /**
     * 删除健康评估记录信息（级联删除）
     *
     * @param id 健康评估记录主键
     * @return 结果
     */
    @Override
    public int deleteHealthAssessmentById(Long id)
    {
        return deleteHealthAssessmentByIds(new Long[]{id});
    }

    /**
     * 查询健康评估报告（按健康评估 ID，取最新一条）
     *
     * @param id 健康评估记录主键
     * @return 评估报告，没有时返回 null
     */
    @Override
    public HealthAssessmentReport selectReportByHealthAssessmentId(Long id)
    {
        if (id == null) {
            return null;
        }
        return reportService.getOne(new LambdaQueryWrapper<HealthAssessmentReport>()
                .eq(HealthAssessmentReport::getHealthAssessmentId, id)
                .orderByDesc(HealthAssessmentReport::getId)
                .last("limit 1"));
    }

    /**
     * 查询老人信息详情（入住流程字段填充）
     * <p>
     * 数据来源：评估主表（姓名、身份证、核心建议）+ 采集表 basicInfo JSON（其余字段）。
     * elder（老人档案）表当前为空且无对应实体，等入住流程建档后可改为优先读 elder 表。
     *
     * @param id 健康评估记录主键
     * @return 老人信息
     */
    @Override
    public ElderInfoVO selectElderInfoByAssessmentId(Long id)
    {
        if (id == null) {
            throw new BaseException("评估ID不能为空");
        }
        HealthAssessment assessment = getById(id);
        if (assessment == null) {
            throw new BaseException("健康评估记录不存在");
        }

        ElderInfoVO vo = new ElderInfoVO();
        // 主表兜底
        vo.setName(assessment.getElderName());
        vo.setIdCardNo(assessment.getIdCard());
        vo.setCoreSuggestion(assessment.getCoreSuggestion());

        // 用采集表里评估表单填写的内容补齐其余字段
        HealthAssessmentDataCollection collection = iHealthAssessmentDataCollectionService.getById(id);
        if (collection != null && StringUtils.isNotEmpty(collection.getBasicInfo())) {
            BasicInfo basicInfo = JSONUtil.toBean(collection.getBasicInfo(), BasicInfo.class);
            if (basicInfo != null) {
                if (StringUtils.isNotEmpty(basicInfo.getElderName())) {
                    vo.setName(basicInfo.getElderName());
                }
                if (StringUtils.isNotEmpty(basicInfo.getIdCard())) {
                    vo.setIdCardNo(basicInfo.getIdCard());
                }
                vo.setPhone(basicInfo.getElderContact());
                vo.setMedicalPaymentMethod(basicInfo.getMedicalPaymentMethod());
                vo.setNation(basicInfo.getNation());
                vo.setEducationLevel(basicInfo.getEducationLevel());
                vo.setSocialSecurityCard(basicInfo.getSocialSecurityCard());
                vo.setLivingSituation(basicInfo.getLivingSituation());
                vo.setReligiousBelief(basicInfo.getReligiousBelief());
                vo.setEconomicSource(basicInfo.getEconomicSource());
                vo.setMaritalStatus(basicInfo.getMaritalStatus());
            }
        }
        return vo;
    }


    public String getLevle(String level) {
        switch (level){
            case "能力完好":
                return "0";
            case "轻度受损":
                return "1";
            case "中度受损":
                return "2";
            case "重度受损":
                return "3";
        }
        throw new BaseException("无效的风险等级");
    }

    @Autowired
    private ChatClient chatClientDeepseekHealth;

    @Autowired
    private IHealthAssessmentReportService reportService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long assessmentData(ElderAssessmentDto dto) {
        // ---------- 1. 校验 + 推导四个维度等级（纯内存，不碰库） ----------
        if (dto == null || dto.getHealthAssessmentDto() == null) {
            throw new BaseException("健康评估数据不能为空");
        }
        DailyLivingActivities daily = dto.getDailyLivingActivities();
        MentalState mental = dto.getMentalState();
        PerceptionAndCommunication perception = dto.getPerceptionAndCommunication();
        SocialParticipation social = dto.getSocialParticipation();
        if (daily == null || StringUtils.isEmpty(daily.getAbilityRating())) {
            throw new BaseException("日常活动评级不能为空");
        }
        if (mental == null || StringUtils.isEmpty(mental.getAbilityRating())) {
            throw new BaseException("精神状态评级不能为空");
        }
        if (perception == null || StringUtils.isEmpty(perception.getAbilityRating())) {
            throw new BaseException("感知觉与沟通评级不能为空");
        }
        if (social == null || StringUtils.isEmpty(social.getAbilityRating())) {
            throw new BaseException("社会参与评级不能为空");
        }
        String dailyLivingLevel = getLevle(daily.getAbilityRating());
        String mentalStateLevel = getLevle(mental.getAbilityRating());
        String perceptionLevel = getLevle(perception.getAbilityRating());
        String socialLevel = getLevle(social.getAbilityRating());

        HealthAssessmentDto.Recent30Days recent30Days = dto.getHealthAssessmentDto().getRecent30Days();
        if (recent30Days == null) {
            throw new BaseException("近30天意外事件数据不能为空");
        }
        Integer fall = recent30Days.getFall();
        Integer choking = recent30Days.getChoking();
        Integer suicideAttempt = recent30Days.getSuicideAttempt();
        Integer lost = recent30Days.getLost();
        Integer coma = recent30Days.getComa();

        HealthAssessmentDto.DiseaseDiagnosis diseaseDiagnosis = dto.getHealthAssessmentDto().getDiseaseDiagnosis();
        if (diseaseDiagnosis == null) {
            throw new BaseException("疾病诊断数据不能为空");
        }
        int dementia = "无".equals(diseaseDiagnosis.getDementia()) ? 0 : 1;
        int mentalIllness = "无".equals(diseaseDiagnosis.getMentalIllness()) ? 0 : 1;
        int chronicDiseases = "无".equals(diseaseDiagnosis.getChronicDiseases()) ? 0 : 1;

        // ---------- 2. 调 AI 做能力等级评定（先算后写库，避免留下半截数据） ----------
        String healthPrompt = String.format(SystemConstant.USER_PROMPT1, dailyLivingLevel, mentalStateLevel,
                perceptionLevel, socialLevel, fall, choking, suicideAttempt, lost, coma,
                dementia, mentalIllness, chronicDiseases);
        ElderLevelResultVO elderLevelResultVO = this.chatBigModel(healthPrompt, ElderLevelResultVO.class);
        if (elderLevelResultVO == null || StringUtils.isEmpty(elderLevelResultVO.getFinalLevel())) {
            throw new BaseException("AI 能力等级评定失败，请稍后重试");
        }

        // ---------- 3. 体检报告智能分析（未上传报告或解析失败时降级，不阻断主流程） ----------
        HealthReportResultVO healthReportResultVO = null;
        String medicalReportUrl = recent30Days.getMedicalReport();
        if (StringUtils.isNotEmpty(medicalReportUrl)) {
            try {
                InputStream inputStream = oSSAliyunFileStorageService.download(medicalReportUrl);
                String pdfContent = PDFUtil.pdfToString(inputStream);
                if (StringUtils.isNotEmpty(pdfContent)) {
                    healthReportResultVO = this.chatBigModel(
                            String.format(SystemConstant.USER_PROMPT2, pdfContent), HealthReportResultVO.class);
                } else {
                    log.warn("体检报告 PDF 文本为空，跳过 AI 分析：{}", medicalReportUrl);
                }
            } catch (Exception e) {
                log.warn("体检报告下载/解析/AI 分析失败，已跳过：{}", e.getMessage());
            }
        }

        // ---------- 4. 一次性落库：主表 + 采集表 + 报告表 ----------
        Long id = insertHealthAssessment(dto);

        HealthAssessmentReport healthAssessmentReport = new HealthAssessmentReport();
        healthAssessmentReport.setHealthAssessmentId(id);
        healthAssessmentReport.setDailyActivityLevel(dailyLivingLevel);
        healthAssessmentReport.setMentalStatusLevel(mentalStateLevel);
        healthAssessmentReport.setPerceptionCommunicationLevel(perceptionLevel);
        healthAssessmentReport.setSocialParticipationLevel(socialLevel);
        healthAssessmentReport.setInitialAbilityLevel(elderLevelResultVO.getPreLevel());
        healthAssessmentReport.setFinalAbilityLevel(elderLevelResultVO.getFinalLevel());
        healthAssessmentReport.setLevelChangeReason(elderLevelResultVO.getReason());

        Integer coreSuggestion = null;
        if (healthReportResultVO != null) {
            healthAssessmentReport.setRiskLevel(healthReportResultVO.getRiskLevel());
            healthAssessmentReport.setReportSummary(healthReportResultVO.getSummarize());
            healthAssessmentReport.setAbnormalAnalysis(JSONUtil.toJsonStr(healthReportResultVO.getAbnormalData()));
            healthAssessmentReport.setSystemScore(JSONUtil.toJsonStr(healthReportResultVO.getSystemScore()));
            Double healthScore = healthReportResultVO.getHealthScore();
            if (healthScore != null) {
                healthAssessmentReport.setHealthScore(healthScore.toString());
                healthAssessmentReport.setRecommendedCareLevel(this.getCareLevel(healthScore));
                coreSuggestion = healthScore >= 60 ? 1 : 0;
                healthAssessmentReport.setCoreSuggestion(coreSuggestion);
            }
        }
        // 入住情况：0=未入住（新建报告还没办入住，不能留 null，否则前端会显示"已入住"）
        healthAssessmentReport.setCheckInStatus(0);
        // 评估时间 / 评估员：前端报告页展示这两个字段，不赋值就会显示成 "--"
        healthAssessmentReport.setAssessmentTime(new Date());
        SysUser loginUser = SecurityUtils.getLoginUser().getUser();
        healthAssessmentReport.setAssessorName(StringUtils.isNotEmpty(loginUser.getNickName())
                ? loginUser.getNickName() : loginUser.getUserName());
        healthAssessmentReport.setCreateTime(new Date());
        healthAssessmentReport.setCreateBy(SecurityUtils.getLoginUser().getUserId().toString());
        reportService.save(healthAssessmentReport);

        // ---------- 5. 标记评估完成（进度 1，前端据此显示"已完成"） ----------
        HealthAssessment healthAssessment = new HealthAssessment();
        healthAssessment.setId(id);
        healthAssessment.setCoreSuggestion(coreSuggestion);
        healthAssessment.setEvaluationProgress(1);
        healthAssessment.setUpdateTime(new Date());
        healthAssessment.setUpdateBy(SecurityUtils.getLoginUser().getUserId().toString());
        this.updateById(healthAssessment);

        return id;
    }
    /**
     * 从 LLM 响应中提取首个 JSON 对象。
     * <p>
     * 处理三种常见情况：纯 JSON、带 markdown ```json 围栏、JSON 前后夹杂说明文字。
     * </p>
     */
    private String extractJson(String content) {
        // 优先匹配 ```json ... ``` 代码块
        Pattern fence = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```", Pattern.CASE_INSENSITIVE);
        Matcher fm = fence.matcher(content);
        if (fm.find()) {
            return fm.group(1);
        }
        // 否则取首个 {...} 顶层对象
        Pattern obj = Pattern.compile("\\{[\\s\\S]*\\}");
        Matcher om = obj.matcher(content);
        if (om.find()) {
            return om.group();
        }
        return content;
    }



    /**
     * 根据健康评分获取建议护理等级
     * @param healthScore
     * @return
     */
    private String getCareLevel(Double healthScore) {
        String level = "不建议入住";
        if (healthScore>=90) {
            level = "三级护理";
        } else if (healthScore>=80) {
            level = "二级护理";
        } else if (healthScore>=70){
            level = "一级护理";
        } else if (healthScore>=60) {
            level = "特级护理";
        }
        return level;
    }


    /**
     * 与大模型交互对话
     * @param prompt
     * @param type
     * @return
     * @param <T>
     */
    private <T> T chatBigModel(String prompt,Class<T> type) {
        String content = chatClientDeepseekHealth.prompt()
                .user(prompt)
                .call()
                .content();

        //解析 JSON 结果：解析 AI 返回的 JSON 格式分析结果。
        String json = extractJson(content);
        T t = JSONUtil.toBean(json, type);
        return t;
    }

}
