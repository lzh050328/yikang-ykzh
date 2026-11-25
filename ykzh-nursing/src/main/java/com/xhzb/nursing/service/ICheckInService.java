package com.xhzb.nursing.service;

import java.util.List;
import com.xhzb.nursing.domain.CheckIn;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 入住信息Service接口
 * 
 * @author lzh
 * @date 2026-10-30
 */
public interface ICheckInService extends IService<CheckIn>
{
    /**
     * 查询入住信息
     * 
     * @param id 入住信息主键
     * @return 入住信息
     */
    public CheckIn selectCheckInById(Long id);

    /**
     * 查询入住信息列表
     * 
     * @param checkIn 入住信息
     * @return 入住信息集合
     */
    public List<CheckIn> selectCheckInList(CheckIn checkIn);

    /**
     * 新增入住信息
     * 
     * @param checkIn 入住信息
     * @return 结果
     */
    public int insertCheckIn(CheckIn checkIn);

    /**
     * 修改入住信息
     * 
     * @param checkIn 入住信息
     * @return 结果
     */
    public int updateCheckIn(CheckIn checkIn);

    /**
     * 批量删除入住信息
     * 
     * @param ids 需要删除的入住信息主键集合
     * @return 结果
     */
    public int deleteCheckInByIds(Long[] ids);

    /**
     * 删除入住信息信息
     * 
     * @param id 入住信息主键
     * @return 结果
     */
    public int deleteCheckInById(Long id);

    /**
     * 申请入住（核心业务：校验评估/在住/床位 → 占床 → 建档 → 签约 → 入住 → 配置 → 回写评估）
     *
     * @param dto 申请入住入参
     * @return 入住记录ID
     */
    public Long applyCheckIn(com.xhzb.nursing.domain.dto.checkin.CheckInApplyDto dto);

    /**
     * 查询入住详情（老人 + 家属 + 入住配置 + 合同）
     *
     * @param id 入住记录ID
     * @return 入住详情
     */
    public com.xhzb.nursing.domain.vo.CheckInDetailVo selectCheckInDetail(Long id);
}
