package com.xhzb.nursing.domain.dto.member;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序登录请求参数
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "小程序登录请求参数")
public class WxLoginDto {

    /** 临时登录凭证 code */
    @NotBlank(message = "登录凭证code不能为空")
    @Schema(title = "临时登录凭证code")
    private String code;

    /** 微信用户昵称 */
    @Schema(title = "微信用户昵称")
    private String nickName;

    /** 获取手机号的临时 code */
    @NotBlank(message = "手机号凭证phoneCode不能为空")
    @Schema(title = "获取手机的临时code")
    private String phoneCode;
}
