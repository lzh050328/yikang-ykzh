package com.xhzb.nursing.service;

import java.util.List;
import java.util.Map;
import com.xhzb.nursing.domain.DeviceData;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 设备数据管理Service接口
 * 
 * @author lzh
 * @date 2026-11-03
 */
public interface IDeviceDataService extends IService<DeviceData>
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
     * 批量删除设备数据管理
     * 
     * @param ids 需要删除的设备数据管理主键集合
     * @return 结果
     */
    public int deleteDeviceDataByIds(Long[] ids);

    /**
     * 删除设备数据管理信息
     * 
     * @param id 设备数据管理主键
     * @return 结果
     */
    public int deleteDeviceDataById(Long id);

    /**
     * 处理 IoTDA 平台推送的设备属性上报消息，把上报的属性值批量写入 device_data 表
     *
     * @param contentStr AMQP 消息内容（JSON）
     */
    void handleDeviceReportData(String contentStr);

    /**
     * 小程序端按天查询指标数据：按小时聚合，返回 24 个点（缺失小时补 0）
     *
     * @param iotId      设备ID
     * @param functionId 功能点（如 HeartRate）
     * @param startTime  开始时间（毫秒时间戳，可空，默认当天 00:00:00）
     * @param endTime    结束时间（毫秒时间戳，可空，默认当天 23:59:59）
     * @return [{dateTime: "00:00", dataValue: 73.0}]
     */
    List<Map<String, Object>> selectAvgDataByHour(String iotId, String functionId, Long startTime, Long endTime);

    /**
     * 小程序端按周查询指标数据：按天聚合，返回 7 个点（缺失日期补 0）
     *
     * @param iotId      设备ID
     * @param functionId 功能点（如 HeartRate）
     * @param startTime  开始时间（毫秒时间戳，可空，默认 6 天前的 00:00:00）
     * @param endTime    结束时间（毫秒时间戳，可空，默认当天 23:59:59）
     * @return [{dateTime: "01.15", dataValue: 107}]
     */
    List<Map<String, Object>> selectAvgDataByDay(String iotId, String functionId, Long startTime, Long endTime);
}
