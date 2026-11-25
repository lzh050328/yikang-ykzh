package com.xhzb.nursing.domain.vo;

import com.xhzb.nursing.domain.DeviceData;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备VO（智能床位用：设备基础信息 + 最新上报数据）
 * <p>
 * 房间/床位上展示的设备，含每个功能点（物模型属性）的最新一条上报数据
 *
 * @author lzh
 * @date 2026-11-03
 */
@Data
@Schema(description = "设备VO（含最新上报数据）")
public class DeviceVo {

    @Schema(title = "设备ID")
    private Long id;

    @Schema(title = "物联网设备ID")
    private String iotId;

    @Schema(title = "设备名称")
    private String deviceName;

    @Schema(title = "产品key")
    private String productKey;

    @Schema(title = "产品名称")
    private String productName;

    @Schema(title = "绑定位置")
    private String bindingLocation;

    @Schema(title = "位置类型 0：随身设备 1：固定设备")
    private Integer locationType;

    @Schema(title = "物理位置类型 0楼层 1房间 2床位")
    private Integer physicalLocationType;

    @Schema(title = "位置备注")
    private String deviceDescription;

    @Schema(title = "最新上报数据")
    private List<DeviceData> deviceDataVos = new ArrayList<>();
}
