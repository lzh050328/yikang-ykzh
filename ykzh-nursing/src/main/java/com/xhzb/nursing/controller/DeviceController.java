package com.xhzb.nursing.controller;

import com.xhzb.common.core.controller.BaseController;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.common.core.page.TableDataInfo;
import com.xhzb.nursing.domain.Device;
import com.xhzb.nursing.service.IDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备Controller
 *
 * @author ruoyi
 * @date 2026-04-05
 */
@RestController
@RequestMapping("/nursing/device")
@Tag(name = "设备相关接口")
public class DeviceController extends BaseController
{
    @Autowired
    private IDeviceService deviceService;

    /**
     * 查询设备列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询设备列表")
    public TableDataInfo list(Device device)
    {
        startPage();
        List<Device> list = deviceService.selectDeviceList(device);
        return getDataTable(list);
    }

    @PostMapping("/syncProductList")
    @Operation(summary = "从物联网平台同步产品列表")
    public AjaxResult syncProductList() {
        deviceService.syncProductList();
        return success();
    }


    @GetMapping("/allProduct")
    @Operation(summary = "查询所有产品列表")
    public AjaxResult allProduct() {
        return success(deviceService.allProduct());
    }

    /**
     * 注册设备
     */
    @PostMapping("/register")
    @Operation(summary = "注册设备")
    public AjaxResult register(@RequestBody Device device) {
        deviceService.registerDevice(device);
        return success();
    }

    /**
     * 查看设备上报的数据
     */
    @GetMapping("/queryServiceProperties/{iotId}")
    @Operation(summary = "查看设备上报的数据")
    public AjaxResult queryServiceProperties(@PathVariable("iotId") String iotId) {
        return success(deviceService.queryServiceProperties(iotId));
    }

    /**
     * 查询设备详细数据
     */
    @GetMapping("/{iotId}")
    @Operation(summary = "查询设备详细数据")
    public AjaxResult getInfo(@PathVariable("iotId") String iotId) {
        return success(deviceService.selectDeviceDetail(iotId));
    }

    /**
     * 删除设备
     */
    @DeleteMapping("/{iotId}")
    @Operation(summary = "删除设备")
    public AjaxResult remove(@PathVariable("iotId") String iotId) {
        deviceService.deleteDeviceByIotId(iotId);
        return success();
    }

    @GetMapping("/queryProduct/{productKey}")
    public AjaxResult queryProduct(@PathVariable String productKey){
        return deviceService.queryProduct(productKey);
    }
}