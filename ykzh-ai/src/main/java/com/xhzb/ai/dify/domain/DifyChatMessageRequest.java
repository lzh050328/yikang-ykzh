package com.xhzb.ai.dify.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * Dify 发送对话消息请求参数
 *
 * @author xhzb
 */
@Data
@Schema(name = "DifyChatMessageRequest", description = "Dify 发送对话消息请求参数")
public class DifyChatMessageRequest {

    /**
     * 用户提问内容
     */
    @Schema(description = "用户提问内容")
    private String query;

    /**
     * 会话ID；为空表示开启新会话（由 Dify 生成并返回）
     */
    @Schema(description = "会话ID，为空表示新会话")
    private String conversationId;

    /**
     * Dify 应用定义的输入变量（Agent 应用一般为空）
     */
    @Schema(description = "Dify 应用输入变量")
    private Map<String, Object> inputs;

    /**
     * Dify 侧的用户标识；不传时后端根据登录用户自动生成
     */
    @Schema(description = "Dify 侧用户标识，不传则取当前登录用户")
    private String userId;
}
