package com.xhzb.nursing.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.xhzb.common.core.domain.entity.SysUser;

/**
 * 护理员-老人关联Mapper接口
 *
 * @author lzh
 * @date 2026-11-05
 */
@Mapper
public interface NursingElderMapper
{
    /**
     * 查询负责指定老人的护理员用户
     * <p>
     * RoomMapper.selectByFloorIdWithNur 的嵌套查询会用到，返回字段别名为 userId、nick_name
     *
     * @param elderId 老人ID
     * @return 护理员用户集合
     */
    List<SysUser> selectUserByElderId(@Param("elderId") Long elderId);
}
