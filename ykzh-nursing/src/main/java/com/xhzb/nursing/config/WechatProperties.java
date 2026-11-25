package com.xhzb.nursing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置（application.yml 中的 wechat 节点）
 *
 * @author lzh
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /** 小程序 appid */
    private String appid;

    /** 小程序 secret */
    private String secret;
}
