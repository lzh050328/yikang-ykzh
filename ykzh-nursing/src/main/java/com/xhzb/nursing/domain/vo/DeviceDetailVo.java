package com.xhzb.nursing.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 设备详细数据VO
 * <p>
 * 数据来源：本地 device 表 + 华为云物联网平台查询结果合并
 *
 * @author lzh
 * @date 2026-11-02
 */
@Data
@Schema(description = "设备详细数据")
public class DeviceDetailVo {

    @Schema(title = "主键id")
    private Long id;

    @Schema(title = "物联网设备ID")
    private String iotId;

    @Schema(title = "设备名称")
    private String deviceName;

    @Schema(title = "节点id(设备标识符)")
    private String nodeId;

    @Schema(title = "设备秘钥")
    private String secret;

    @Schema(title = "产品key")
    private String productKey;

    @Schema(title = "产品名称")
    private String productName;

    @Schema(title = "位置类型 0：随身设备 1：固定设备")
    private Integer locationType;

    @Schema(title = "绑定位置")
    private String bindingLocation;

    @Schema(title = "位置备注")
    private String remark;

    @Schema(title = "设备状态：ONLINE/OFfline/ABNORMAL/INACTIVE")
    private String deviceStatus;

    /**
     * 设备激活时间（华为云返回的是带时区的字符串，已转为北京时间）
     */
    @Schema(title = "设备激活时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime activeTime;

    @Schema(title = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @Schema(title = "创建人id")
    private Long createBy;

    @Schema(title = "创建人")
    private String creator;
}
