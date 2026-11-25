package com.xhzb.nursing.domain.dto.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序端绑定家人请求参数
 *
 * @author lzh
 * @date 2026-11-03
 */
@Data
@Schema(description = "小程序绑定家人请求参数")
public class MemberBindDto {

    /** 老人身份证号 */
    @NotBlank(message = "身份证号不能为空")
    @Schema(title = "老人身份证号")
    private String idCard;

    /** 老人姓名 */
    @Schema(title = "老人姓名")
    private String name;

    /** 家属对老人的称呼 */
    @Schema(title = "家属对老人的称呼")
    private String remark;
}
