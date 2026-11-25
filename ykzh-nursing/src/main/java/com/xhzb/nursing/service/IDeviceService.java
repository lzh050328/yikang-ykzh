package com.xhzb.nursing.service;

import java.util.List;

import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.nursing.domain.Device;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhzb.nursing.domain.vo.ProductVo;
import com.xhzb.nursing.domain.vo.DeviceDetailVo;
import com.xhzb.nursing.domain.vo.DevicePropertyDataVO;

/**
 * 设备管理Service接口
 * 
 * @author lzh
 * @date 2026-11-02
 */
public interface IDeviceService extends IService<Device>
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
     * 批量删除设备管理
     * 
     * @param ids 需要删除的设备管理主键集合
     * @return 结果
     */
    public int deleteDeviceByIds(Long[] ids);

    /**
     * 删除设备管理信息
     * 
     * @param id 设备管理主键
     * @return 结果
     */
    public int deleteDeviceById(Long id);

    void syncProductList();

    /**
     * 查询所有产品列表
     *
     * @return 产品列表
     */
    List<ProductVo> allProduct();

    /**
     * 注册设备：校验重复 -> 生成密钥 -> 调用华为云 IoT 注册 -> 落库
     *
     * @param device 设备信息
     */
    void registerDevice(Device device);

    /**
     * 查询设备详细数据：以本地 device 表为准，缺的数据调用华为云查询设备补齐
     *
     * @param iotId 物联网设备ID
     * @return 设备详细数据
     */
    DeviceDetailVo selectDeviceDetail(String iotId);

    /**
     * 查询设备上报的数据（华为云设备影子 reported 数据，按属性拆分为列表）
     *
     * @param iotId 物联网设备ID
     * @return 设备上报数据列表
     */
    List<DevicePropertyDataVO> queryServiceProperties(String iotId);

    /**
     * 删除设备：先删除本地，再删除华为云平台上注册的设备
     *
     * @param iotId 物联网设备ID
     * @return 影响行数
     */
    int deleteDeviceByIotId(String iotId);

    /**
     * 查询产品详情
     * @param productKey
     * @return
     */
    AjaxResult queryProduct(String productKey);
}
