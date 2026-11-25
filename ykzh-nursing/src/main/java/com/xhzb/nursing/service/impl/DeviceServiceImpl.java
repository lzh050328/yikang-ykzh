package com.xhzb.nursing.service.impl;

import java.util.Collections;
import java.util.List;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.huaweicloud.sdk.iotda.v5.IoTDAClient;
import com.huaweicloud.sdk.iotda.v5.model.*;
import com.xhzb.common.constant.CacheConstants;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.common.exception.ServiceException;
import com.xhzb.common.exception.base.BaseException;
import com.xhzb.common.utils.DateUtils;
import com.xhzb.common.utils.bean.BeanUtils;
import com.xhzb.nursing.domain.vo.DeviceDetailVo;
import com.xhzb.nursing.domain.vo.DevicePropertyDataVO;
import com.xhzb.nursing.domain.vo.ProductVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xhzb.nursing.mapper.DeviceMapper;
import com.xhzb.nursing.domain.Device;
import com.xhzb.nursing.service.IDeviceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 设备管理Service业务层处理
 * 
 * @author lzh
 * @date 2026-11-02
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements IDeviceService
{
    private static final Logger log = LoggerFactory.getLogger(DeviceServiceImpl.class);

    /**
     * 华为云设备标识符(node_id)的格式要求：只允许字母、数字、冒号、下划线、中划线，长度 1~64
     */
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9:_-]{1,64}$");

    /**
     * 设备状态兜底值：华为云查不到状态时，按离线处理
     */
    private static final String DEVICE_STATUS_OFFLINE = "OFFLINE";

    @Autowired
    private DeviceMapper deviceMapper;

    /**
     * 查询设备管理
     * 
     * @param id 设备管理主键
     * @return 设备管理
     */
    @Override
    public Device selectDeviceById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询设备管理列表
     * 
     * @param device 设备管理
     * @return 设备管理
     */
    @Override
    public List<Device> selectDeviceList(Device device)
    {
        return deviceMapper.selectDeviceList(device);
    }

    /**
     * 新增设备管理
     * 
     * @param device 设备管理
     * @return 结果
     */
    @Override
    public int insertDevice(Device device)
    {
        return save(device)? 1 : 0;
    }

    /**
     * 修改设备管理
     * 
     * @param device 设备管理
     * @return 结果
     */
    @Override
    public int updateDevice(Device device)
    {
        return updateById(device)? 1 : 0;
    }

    /**
     * 批量删除设备管理
     * 
     * @param ids 需要删除的设备管理主键
     * @return 结果
     */
    @Override
    public int deleteDeviceByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids))? 1 : 0;
    }

    /**
     * 删除设备管理信息
     * 
     * @param id 设备管理主键
     * @return 结果
     */
    @Override
    public int deleteDeviceById(Long id)
    {
        return removeById(id)? 1 : 0;
    }


    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private IoTDAClient client;
    /**
     * 同步产品列表
     */
    /**
     * 同步产品列表
     */
    @Override
    public void syncProductList() {
        //请求参数
        ListProductsRequest listProductsRequest = new ListProductsRequest();
        //设置条数
        listProductsRequest.setLimit(50);
        //发起请求
        ListProductsResponse response = client.listProducts(listProductsRequest);
        if(response.getHttpStatusCode() != 200){
            throw new BaseException("物联网接口 - 查询产品，同步失败");
        }
        //存储到redis
        redisTemplate.opsForValue().set(CacheConstants.IOT_ALL_PRODUCT_LIST, JSONUtil.toJsonStr(response.getProducts()));

    }

    /**
     * 查询所有产品列表
     *
     * @return
     */
    @Override
    public List<ProductVo> allProduct() {
        //从redis中查询数据
        String jsonStr = redisTemplate.opsForValue().get(CacheConstants.IOT_ALL_PRODUCT_LIST);
        //如果数据为空，则返回一个空集合
        if(StringUtils.isEmpty(jsonStr)){
            return Collections.emptyList();
        }
        //解析数据，并返回
        return JSONUtil.toList(jsonStr, ProductVo.class);
    }

    /**
     * 注册设备
     * <p>
     * 流程：校验设备名称/标识符/位置产品是否重复 -> 生成设备密钥 -> 调用华为云 IoT 注册设备 -> 数据入库
     *
     * @param device 设备信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerDevice(Device device) {
        if (!StringUtils.hasText(device.getDeviceName())) {
            throw new ServiceException("设备名称不能为空");
        }
        if (!StringUtils.hasText(device.getNodeId())) {
            throw new ServiceException("设备标识符不能为空");
        }
        // 华为云要求 node_id 只能由 字母/数字/冒号/下划线/中划线 组成，长度 1~64，否则直接 400
        if (!NODE_ID_PATTERN.matcher(device.getNodeId()).matches()) {
            throw new ServiceException("设备标识符【" + device.getNodeId()
                    + "】格式不正确：只能包含字母、数字、冒号(:)、下划线(_)、中划线(-)，长度 1~64 位，不能有中文或空格");
        }

        // 1. 设备名称不允许重复
        Long nameCount = lambdaQuery().eq(Device::getDeviceName, device.getDeviceName()).count();
        if (nameCount != null && nameCount > 0) {
            throw new ServiceException("设备名称【" + device.getDeviceName() + "】已存在，请更换设备名称");
        }

        // 2. 设备标识符(nodeId)不允许重复
        Long nodeIdCount = lambdaQuery().eq(Device::getNodeId, device.getNodeId()).count();
        if (nodeIdCount != null && nodeIdCount > 0) {
            throw new ServiceException("设备标识符【" + device.getNodeId() + "】已存在，请更换设备标识符");
        }

        // 3. 同一个位置不允许绑定相同的产品
        Long bindCount = lambdaQuery()
                .eq(Device::getBindingLocation, device.getBindingLocation())
                .eq(Device::getProductKey, device.getProductKey())
                .count();
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException("当前位置已绑定相同产品【" + device.getProductName() + "】，请勿重复绑定");
        }

        // 4. 生成 32 位设备密钥，调用华为云物联网平台注册设备
        String secret = IdUtil.simpleUUID();
        AddDevice addDevice = new AddDevice()
                .withDeviceName(device.getDeviceName())
                .withNodeId(device.getNodeId())
                // 华为云的产品标识即前端传入的 productKey
                .withProductId(device.getProductKey())
                // 密钥认证：authType 固定为 SECRET
                .withAuthInfo(new AuthInfo().withAuthType("SECRET").withSecret(secret));
        if (StringUtils.hasText(device.getRemark())) {
            addDevice.withDescription(device.getRemark());
        }

        AddDeviceResponse response;
        try {
            response = client.addDevice(new AddDeviceRequest().withBody(addDevice));
        } catch (Exception e) {
            log.error("调用华为云注册设备失败，设备名称：{}", device.getDeviceName(), e);
            throw new ServiceException("调用华为云注册设备失败：" + e.getMessage());
        }
        // 华为云注册设备接口返回 201 表示成功
        if (response == null || response.getHttpStatusCode() != 201) {
            throw new ServiceException("注册设备失败，华为云返回状态码："
                    + (response == null ? "无响应" : response.getHttpStatusCode()));
        }

        // 5. 组装设备数据并入库
        device.setSecret(secret);
        // 华为云返回的 deviceId 即本地的物联网设备ID
        device.setIotId(response.getDeviceId());
        device.setCreateTime(DateUtils.getNowDate());
        if (!save(device)) {
            throw new ServiceException("设备信息保存失败");
        }
        log.info("设备注册成功，设备名称：{}，iotId：{}", device.getDeviceName(), device.getIotId());
    }

    /**
     * 查询设备详细数据
     * <p>
     * 1. 先根据 iotId 从本地 device 表查询
     * 2. 本地数据不够时，调用华为云查询设备接口补齐
     * 3. 华为云返回的激活时间是带时区的字符串，转换为北京时间 LocalDateTime
     * 4. 合并两种数据后返回
     *
     * @param iotId 物联网设备ID
     * @return 设备详细数据
     */
    @Override
    public DeviceDetailVo selectDeviceDetail(String iotId) {
        if (!StringUtils.hasText(iotId)) {
            throw new ServiceException("设备ID不能为空");
        }

        // 1. 本地数据库查询
        Device device = lambdaQuery().eq(Device::getIotId, iotId).one();
        if (device == null) {
            throw new ServiceException("设备不存在，iotId：" + iotId);
        }
        DeviceDetailVo detail = new DeviceDetailVo();
        BeanUtils.copyProperties(device, detail);
        // BaseEntity.createBy 是 String，VO 里是 Long，类型不一致不会被拷贝，这里单独转换
        detail.setCreateBy(Convert.toLong(device.getCreateBy()));

        // 2. 本地数据不够，调用华为云查询设备详情补齐
        try {
            ShowDeviceResponse response = client.showDevice(new ShowDeviceRequest().withDeviceId(iotId));
            if (response != null && response.getHttpStatusCode() == 200) {
                // 设备状态以华为云为准：ONLINE / OFFLINE / INACTIVE / ABNORMAL，保证与平台一致
                detail.setDeviceStatus(StringUtils.hasText(response.getStatus())
                        ? response.getStatus() : DEVICE_STATUS_OFFLINE);
                // 3. 激活时间：华为云返回带时区的时间字符串，转成北京时间的 LocalDateTime
                if (StringUtils.hasText(response.getActiveTime())) {
                    detail.setActiveTime(OffsetDateTime.parse(response.getActiveTime())
                            .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                            .toLocalDateTime());
                }
                // 本地缺失的字段，用华为云的数据补齐
                if (!StringUtils.hasText(detail.getProductName())) {
                    detail.setProductName(response.getProductName());
                }
                if (!StringUtils.hasText(detail.getProductKey())) {
                    detail.setProductKey(response.getProductId());
                }
                if (!StringUtils.hasText(detail.getNodeId())) {
                    detail.setNodeId(response.getNodeId());
                }
                if (!StringUtils.hasText(detail.getDeviceName())) {
                    detail.setDeviceName(response.getDeviceName());
                }
                if (!StringUtils.hasText(detail.getSecret()) && response.getAuthInfo() != null) {
                    detail.setSecret(response.getAuthInfo().getSecret());
                }
            } else {
                log.warn("查询华为云设备详情失败，iotId：{}，状态码：{}", iotId,
                        response == null ? "无响应" : response.getHttpStatusCode());
            }
        } catch (Exception e) {
            // 云端查询失败不影响本地数据的返回
            log.warn("调用华为云查询设备详情异常，iotId：{}", iotId, e);
        }
        // 云端没返回状态时兜底为离线，避免状态为空
        if (!StringUtils.hasText(detail.getDeviceStatus())) {
            detail.setDeviceStatus(DEVICE_STATUS_OFFLINE);
        }

        // 4. 返回合并后的数据
        return detail;
    }

    /**
     * 删除设备：先删除本地，再删除华为云平台上注册的设备
     *
     * @param iotId 物联网设备ID
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeviceByIotId(String iotId) {
        if (!StringUtils.hasText(iotId)) {
            throw new ServiceException("设备ID不能为空");
        }
        Device device = lambdaQuery().eq(Device::getIotId, iotId).one();
        if (device == null) {
            throw new ServiceException("设备不存在，iotId：" + iotId);
        }

        // 1. 先删除本地
        int rows = baseMapper.deleteById(device.getId());

        // 2. 再删除华为云设备（平台失败只记日志，避免本地数据删不掉）
        try {
            DeleteDeviceResponse response = client.deleteDevice(
                    new DeleteDeviceRequest().withDeviceId(iotId));
            if (response == null || response.getHttpStatusCode() >= 300) {
                log.error("删除华为云设备失败，iotId：{}，状态码：{}，请到控制台手动清理",
                        iotId, response == null ? "无响应" : response.getHttpStatusCode());
            } else {
                log.info("华为云设备已删除，iotId：{}", iotId);
            }
        } catch (Exception e) {
            log.error("删除华为云设备异常，iotId：{}，本地已删除，请到控制台手动清理：{}",
                    iotId, e.getMessage());
        }
        return rows;
    }

    /**
     * 查询产品详情
     * @param productKey
     * @return
     */
    @Override
    public AjaxResult queryProduct(String productKey) {
        //参数校验
        if(StringUtils.isEmpty(productKey)){
            throw new BaseException("请输入正确的参数");
        }
        //调用华为云物联网接口
        ShowProductRequest showProductRequest = new ShowProductRequest();
        showProductRequest.setProductId(productKey);
        ShowProductResponse response;

        try {
            response = client.showProduct(showProductRequest);
        } catch (Exception e) {
            throw new BaseException("查询产品详情失败");
        }
        //判断是否存在服务数据
        List<ServiceCapability> serviceCapabilities = response.getServiceCapabilities();
        if(CollUtil.isEmpty(serviceCapabilities)){
            return AjaxResult.success(Collections.emptyList());
        }

        return AjaxResult.success(serviceCapabilities);
    }
    /**
     * 查询设备上报的数据
     * <p>
     * 1. 调用华为云「查询设备影子数据」接口（iot_06_v5_0079），取 reported
     * 2. 把 reported.properties 这个 Map 拆成「一条属性一行」的列表
     * 3. 上报时间由 UTC 转成北京时间的 LocalDateTime
     *
     * @param iotId 物联网设备ID
     * @return 设备上报数据列表
     */
    @Override
    public List<DevicePropertyDataVO> queryServiceProperties(String iotId) {
        if (!StringUtils.hasText(iotId)) {
            throw new ServiceException("设备ID不能为空");
        }

        ShowDeviceShadowResponse response;
        try {
            response = client.showDeviceShadow(new ShowDeviceShadowRequest().withDeviceId(iotId));
        } catch (Exception e) {
            log.error("调用华为云查询设备影子失败，iotId：{}", iotId, e);
            throw new ServiceException("查询设备上报数据失败：" + e.getMessage());
        }
        if (response == null || response.getHttpStatusCode() != 200) {
            throw new ServiceException("查询设备上报数据失败，华为云返回状态码："
                    + (response == null ? "无响应" : response.getHttpStatusCode()));
        }

        List<DeviceShadowData> shadowList = response.getShadow();
        if (shadowList == null || shadowList.isEmpty()) {
            return Collections.emptyList();
        }

        List<DevicePropertyDataVO> result = new ArrayList<>();
        for (DeviceShadowData shadow : shadowList) {
            DeviceShadowProperties reported = shadow.getReported();
            if (reported == null || reported.getProperties() == null) {
                continue;
            }
            // 上报时间：华为云返回 20250219T070424Z（UTC），统一转北京时间
            LocalDateTime eventTime = parseEventTime(reported.getEventTime());
            // properties 是 Map：key 为功能ID（物模型属性名），value 为上报值
            if (reported.getProperties() instanceof Map<?, ?> properties) {
                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    DevicePropertyDataVO data = new DevicePropertyDataVO();
                    data.setFunctionId(String.valueOf(entry.getKey()));
                    data.setValue(entry.getValue());
                    data.setEventTime(eventTime);
                    result.add(data);
                }
            }
        }
        return result;
    }

    /**
     * 把华为云影子的上报时间（UTC）转换为北京时间的 LocalDateTime
     *
     * @param eventTime 例如：20250219T070424Z
     * @return 北京时间，解析失败返回 null
     */
    private LocalDateTime parseEventTime(String eventTime) {
        if (!StringUtils.hasText(eventTime)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(eventTime, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"))
                    .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                    .toLocalDateTime();
        } catch (Exception e) {
            log.warn("解析设备上报时间失败，eventTime：{}", eventTime, e);
            return null;
        }
    }
}
