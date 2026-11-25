package com.xhzb.nursing.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备上报数据VO（一条属性一行）
 * <p>
 * 数据来源：华为云物联网平台「查询设备影子数据」接口中 reported 的 properties
 *
 * @author lzh
 * @date 2026-11-02
 */
@Data
@Schema(description = "设备上报数据")
public class DevicePropertyDataVO {

    @Schema(title = "功能ID（物模型属性名）")
    private String functionId;

    @Schema(title = "上报时间（北京时间）")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime eventTime;

    @Schema(title = "上报的属性值")
    private Object value;
}
