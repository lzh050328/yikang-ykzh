package com.xhzb.ai.dify.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Dify 智能体（Agent）对接配置
 *
 * <p>对应 application.yml 中的 dify 节点，例如：
 * <pre>
 * dify:
 *   base-url: http://localhost/v1
 *   api-key: app-xxxxxxxx
 *   connect-timeout: 10000
 *   read-timeout: 600000
 * </pre>
 *
 * @author xhzb
 */
@Data
@Component
@ConfigurationProperties(prefix = "dify")
public class DifyProperties {

    /**
     * Dify API 基础地址（包含 /v1）
     */
    private String baseUrl = "http://localhost/v1";

    /**
     * Dify 应用（Agent）API 密钥
     */
    private String apiKey;

    /**
     * 连接超时（毫秒）
     */
    private Integer connectTimeout = 10000;

    /**
     * 普通接口读取超时（毫秒）；流式对话接口不设置超时
     */
    private Integer readTimeout = 600000;
}
