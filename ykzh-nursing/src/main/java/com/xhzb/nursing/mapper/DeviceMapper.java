package com.xhzb.nursing.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhzb.nursing.domain.Device;

/**
 * 设备管理Mapper接口
 * 
 * @author lzh
 * @date 2026-11-02
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device>
{
    /**
     * 查询设备管理
     * 
     * @param id 设备管理主键
     * @return 设备管理
     */
    public Device selectDeviceById(Long id);

    /**
     * 查询设备管理列表
     * 
     * @param device 设备管理
     * @return 设备管理集合
     */
    public List<Device> selectDeviceList(Device device);

    /**
     * 新增设备管理
     * 
     * @param device 设备管理
     * @return 结果
     */
    public int insertDevice(Device device);

    /**
     * 修改设备管理
     * 
     * @param device 设备管理
     * @return 结果
     */
    public int updateDevice(Device device);

    /**
     * 删除设备管理
     * 
     * @param id 设备管理主键
     * @return 结果
     */
    public int deleteDeviceById(Long id);

    /**
     * 批量删除设备管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDeviceByIds(Long[] ids);

    /**
     * 查询绑定在指定房间/床位上的固定设备，以及绑定在指定老人上的随身设备
     *
     * @param roomIds  房间ID集合
     * @param bedIds   床位ID集合
     * @param elderIds 老人ID集合
     * @return 设备集合
     */
    List<Device> selectDevicesByBinding(@Param("roomIds") List<Long> roomIds,
                                        @Param("bedIds") List<Long> bedIds,
                                        @Param("elderIds") List<Long> elderIds);
}
