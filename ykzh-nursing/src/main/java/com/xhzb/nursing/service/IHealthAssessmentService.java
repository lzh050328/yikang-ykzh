package com.xhzb.nursing.service;

import java.util.List;
import com.xhzb.nursing.domain.HealthAssessment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhzb.nursing.domain.HealthAssessmentDataCollection;
import com.xhzb.nursing.domain.dto.health.ElderAssessmentDto;

/**
 * 健康评估记录Service接口
 * 
 * @author lzh
 * @date 2026-10-28
 */
public interface IHealthAssessmentService extends IService<HealthAssessment>
{
    /**
     * 查询健康评估记录
     * 
     * @param id 健康评估记录主键
     * @return 健康评估记录
     */
    public HealthAssessmentDataCollection  selectHealthAssessmentById(Long id);

    /**
     * 查询健康评估记录列表
     * 
     * @param healthAssessment 健康评估记录
     * @return 健康评估记录集合
     */
    public List<HealthAssessment> selectHealthAssessmentList(HealthAssessment healthAssessment);

    /**
     * 新增健康评估记录
     * 
     * @param dto 健康评估记录
     * @return 主键ID
     */
    public Long insertHealthAssessment(ElderAssessmentDto dto);

//    /**
//     * 修改健康评估记录
//     *
//     * @param dto 健康评估记录
//     * @return 结果
//     */
//    public HealthAssessmentDataCollection updateHealthAssessment(ElderAssessmentDto  dto);

    /**
     * 取消健康评估（评估中 -> 已取消）
     *
     * @param id 健康评估记录主键
     * @return 主键ID
     */
    public Long cancelHealthAssessment(Long id);

    /**
     * 批量删除健康评估记录
     * 
     * @param ids 需要删除的健康评估记录主键集合
     * @return 结果
     */
    public int deleteHealthAssessmentByIds(Long[] ids);

    /**
     * 删除健康评估记录信息
     * 
     * @param id 健康评估记录主键
     * @return 结果
     */
    public int deleteHealthAssessmentById(Long id);

    Long  assessmentData(ElderAssessmentDto dto);

    /**
     * 查询健康评估报告（按健康评估ID）
     *
     * @param id 健康评估记录主键
     * @return 评估报告，没有时返回 null
     */
    public com.xhzb.nursing.domain.HealthAssessmentReport selectReportByHealthAssessmentId(Long id);

    /**
     * 查询老人信息详情（入住流程字段填充）
     *
     * @param id 健康评估记录主键
     * @return 老人信息（姓名、证件、民族、支付方式、核心建议等）
     */
    public com.xhzb.nursing.domain.vo.ElderInfoVO selectElderInfoByAssessmentId(Long id);
}
