package com.xhzb.nursing.domain.dto.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 小程序端新增预约请求参数
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "小程序端新增预约请求参数")
public class ReservationCreateDto {

    /** 预约人手机号 */
    @NotBlank(message = "预约人手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    @Schema(title = "预约人手机号")
    private String mobile;

    /** 预约人姓名 */
    @NotBlank(message = "预约人姓名不能为空")
    @Schema(title = "预约人姓名")
    private String name;

    /** 预约时间，格式 yyyy-MM-dd HH:mm:ss（兼容 ISO 的 T 分隔） */
    @NotBlank(message = "预约时间不能为空")
    @Schema(title = "预约时间")
    private String time;

    /** 预约类型，0：参观预约 */
    @NotNull(message = "预约类型不能为空")
    @Schema(title = "预约类型，0：参观预约，1：探访预约")
    private Integer type;

    /** 探访人（老人姓名） */
    @NotBlank(message = "探访人不能为空")
    @Schema(title = "探访人（老人姓名）")
    private String visitor;
}
