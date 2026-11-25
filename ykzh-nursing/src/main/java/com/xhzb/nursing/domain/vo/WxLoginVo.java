package com.xhzb.nursing.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 小程序登录返回结果
 *
 * @author lzh
 * @date 2026-10-30
 */
@Data
@Schema(description = "小程序登录返回结果")
public class WxLoginVo {

    /** 令牌 */
    @Schema(title = "令牌")
    private String token;

    /** 家属昵称 */
    @Schema(title = "家属昵称")
    private String nickName;
}
