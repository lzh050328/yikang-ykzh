package com.xhzb.nursing.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhzb.nursing.domain.DeviceData;

/**
 * 设备数据管理Mapper接口
 * 
 * @author lzh
 * @date 2026-11-03
 */
@Mapper
public interface DeviceDataMapper extends BaseMapper<DeviceData>
{
    /**
     * 查询设备数据管理
     * 
     * @param id 设备数据管理主键
     * @return 设备数据管理
     */
    public DeviceData selectDeviceDataById(Long id);

    /**
     * 查询设备数据管理列表
     * 
     * @param deviceData 设备数据管理
     * @return 设备数据管理集合
     */
    public List<DeviceData> selectDeviceDataList(DeviceData deviceData);

    /**
     * 新增设备数据管理
     * 
     * @param deviceData 设备数据管理
     * @return 结果
     */
    public int insertDeviceData(DeviceData deviceData);

    /**
     * 修改设备数据管理
     * 
     * @param deviceData 设备数据管理
     * @return 结果
     */
    public int updateDeviceData(DeviceData deviceData);

    /**
     * 删除设备数据管理
     * 
     * @param id 设备数据管理主键
     * @return 结果
     */
    public int deleteDeviceDataById(Long id);

    /**
     * 批量删除设备数据管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDeviceDataByIds(Long[] ids);

    /**
     * 查询指定设备每个功能点的最新一条上报数据
     *
     * @param iotIds 物联网设备ID集合
     * @return 设备数据集合
     */
    List<DeviceData> selectLatestDataByIotIds(@Param("iotIds") List<String> iotIds);

    /**
     * 按小时统计某功能点在时间区间内的平均值（小程序端"按天查询"）
     *
     * @param iotId      设备ID
     * @param functionId 功能点
     * @param start      开始时间
     * @param end        结束时间
     * @return [{label: "08:00", val: 73.0}]
     */
    @Select("select date_format(alarm_time, '%H:00') as label,"
            + " round(avg(cast(data_value as decimal(12,2))), 1) as val"
            + " from device_data"
            + " where iot_id = #{iotId} and function_id = #{functionId}"
            + " and alarm_time between #{start} and #{end}"
            + " group by label order by label")
    List<Map<String, Object>> selectAvgGroupByHour(@Param("iotId") String iotId,
                                                   @Param("functionId") String functionId,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * 按天统计某功能点在时间区间内的平均值（小程序端"按周查询"）
     *
     * @param iotId      设备ID
     * @param functionId 功能点
     * @param start      开始时间
     * @param end        结束时间
     * @return [{label: "11.03", val: 73}]
     */
    @Select("select date_format(alarm_time, '%m.%d') as label,"
            + " round(avg(cast(data_value as decimal(12,2))), 0) as val"
            + " from device_data"
            + " where iot_id = #{iotId} and function_id = #{functionId}"
            + " and alarm_time between #{start} and #{end}"
            + " group by label order by min(alarm_time)")
    List<Map<String, Object>> selectAvgGroupByDay(@Param("iotId") String iotId,
                                                  @Param("functionId") String functionId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);
}
