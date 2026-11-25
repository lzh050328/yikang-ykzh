package com.xhzb.nursing.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhzb.nursing.domain.AlertRule;

/**
 * 报警规则Mapper接口
 * 
 * @author lzh
 * @date 2026-11-05
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule>
{
    /**
     * 查询报警规则
     * 
     * @param id 报警规则主键
     * @return 报警规则
     */
    public AlertRule selectAlertRuleById(Long id);

    /**
     * 查询报警规则列表
     * 
     * @param alertRule 报警规则
     * @return 报警规则集合
     */
    public List<AlertRule> selectAlertRuleList(AlertRule alertRule);

    /**
     * 新增报警规则
     * 
     * @param alertRule 报警规则
     * @return 结果
     */
    public int insertAlertRule(AlertRule alertRule);

    /**
     * 修改报警规则
     * 
     * @param alertRule 报警规则
     * @return 结果
     */
    public int updateAlertRule(AlertRule alertRule);

    /**
     * 删除报警规则
     * 
     * @param id 报警规则主键
     * @return 结果
     */
    public int deleteAlertRuleById(Long id);

    /**
     * 批量删除报警规则
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAlertRuleByIds(Long[] ids);

    /**
     * 按角色名称查询用户ID
     *
     * @param roleName 角色名称，例如 行政、超级管理员
     * @return 用户ID集合
     */
    List<Long> selectUserIdsByRoleName(@Param("roleName") String roleName);

    /**
     * 移动设备（随身设备）对应的护理员用户ID：device → elder → nursing_elder
     *
     * @param iotId 物联网设备ID
     * @return 护理员用户ID集合
     */
    List<Long> selectNursingUserIdsByMobileDevice(@Param("iotId") String iotId);

    /**
     * 固定设备对应的护理员用户ID：device → bed → elder → nursing_elder
     *
     * @param iotId 物联网设备ID
     * @return 护理员用户ID集合
     */
    List<Long> selectNursingUserIdsByFixedDevice(@Param("iotId") String iotId);
}
