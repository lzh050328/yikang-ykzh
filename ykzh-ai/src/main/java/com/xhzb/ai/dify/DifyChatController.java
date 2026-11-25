package com.xhzb.ai.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhzb.ai.dify.client.DifyClient;
import com.xhzb.ai.dify.domain.DifyChatMessageRequest;
import com.xhzb.common.core.controller.BaseController;
import com.xhzb.common.core.domain.AjaxResult;
import com.xhzb.common.utils.SecurityUtils;
import com.xhzb.common.utils.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Dify-AI 智能体对话控制器
 *
 * <p>四个接口一一对应 Dify 的同名接口：
 * <ul>
 *     <li>POST   /ai/dify/chat-messages                         -> POST /chat-messages（streaming，SSE 流式返回）</li>
 *     <li>GET    /ai/dify/conversations                         -> GET /conversations</li>
 *     <li>GET    /ai/dify/conversations/{id}/messages           -> GET /messages</li>
 *     <li>DELETE /ai/dify/conversations/{id}                    -> DELETE /conversations/{id}</li>
 * </ul>
 *
 * @author xhzb
 */
@Slf4j
@RestController
@RequestMapping("/ai/dify")
@Tag(name = "Dify-AI 智能体对话")
public class DifyChatController extends BaseController {

    /**
     * 未登录时使用的 Dify 用户标识（接口已在 SecurityConfig 中放行，允许匿名访问）
     */
    private static final String ANONYMOUS_USER = "xhzb-anonymous";

    private static final String SSE_DATA_PREFIX = "data:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DifyClient difyClient;

    /**
     * 发送对话消息（流式输出）
     *
     * <p>直接以 text/event-stream 把 Dify 的原始 SSE 事件逐条推送给前端，
     * 前端按 data: 行解析，取 answer 字段做持续输出。
     */
    @PostMapping(value = "/chat-messages")
    @Operation(summary = "发送对话消息（SSE 流式输出）")
    public void chatMessages(@RequestBody(required = false) DifyChatMessageRequest request, HttpServletResponse response) {
        String query = request == null ? null : request.getQuery();
        String conversationId = request == null ? "" : StringUtils.trim(request.getConversationId());
        String user = resolveUser(request == null ? null : request.getUserId());

        log.info("[Dify-AI] 收到对话请求, user={}, conversationId={}, query={}", user,
                StringUtils.isEmpty(conversationId) ? "（新会话）" : conversationId, query);

        if (StringUtils.isEmpty(query)) {
            log.warn("[Dify-AI] 对话请求参数非法：query 为空, user={}", user);
            writeSseError(response, "问题内容不能为空");
            return;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query);
        // 对话接口固定使用 streaming 模式
        payload.put("response_mode", "streaming");
        // conversation_id 为空时 Dify 会自动新建会话并在流中返回新的会话ID
        payload.put("conversation_id", StringUtils.isEmpty(conversationId) ? "" : conversationId);
        payload.put("user", user);
        // 首次对话后由 Dify 自动生成会话名称，便于历史记录展示
        payload.put("auto_generate_name", true);
        if (request.getInputs() == null || request.getInputs().isEmpty()) {
            payload.set("inputs", objectMapper.createObjectNode());
        } else {
            payload.set("inputs", objectMapper.valueToTree(request.getInputs()));
        }

        long startTime = System.currentTimeMillis();
        HttpResponse<InputStream> upstream = null;
        try {
            upstream = difyClient.chatMessagesStream(payload);
            if (upstream.statusCode() != 200) {
                String errorBody = readAll(upstream.body());
                log.error("[Dify-AI] Dify 对话接口返回异常, status={}, body={}", upstream.statusCode(), errorBody);
                writeSseError(response, "Dify 服务返回异常(status=" + upstream.statusCode() + ")：" + errorBody);
                return;
            }
            prepareSseResponse(response);
            forwardStream(upstream.body(), response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Dify-AI] 对话请求被中断, user={}", user, e);
        } catch (Exception e) {
            log.error("[Dify-AI] 对话请求失败, user={}, conversationId={}", user, conversationId, e);
            writeSseError(response, "对话失败：" + e.getMessage());
        } finally {
            closeQuietly(upstream);
            log.info("[Dify-AI] 对话请求处理结束, user={}, 耗时={}ms", user, System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表")
    public AjaxResult listConversations(
            @Schema(description = "Dify 用户标识，不传则取当前登录用户") @RequestParam(required = false) String userId,
            @Schema(description = "分页参数：上一页最后一条会话ID") @RequestParam(required = false) String lastId,
            @Schema(description = "每页条数，默认 20") @RequestParam(required = false, defaultValue = "20") Integer limit) {
        String user = resolveUser(userId);
        log.info("[Dify-AI] 查询会话列表, user={}, lastId={}, limit={}", user, lastId, limit);
        JsonNode data = difyClient.listConversations(user, lastId, limit);
        log.info("[Dify-AI] 查询会话列表完成, user={}, 会话数={}", user, data.path("data").size());
        return success(data);
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "获取会话历史消息")
    public AjaxResult listMessages(
            @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED) @PathVariable String conversationId,
            @Schema(description = "Dify 用户标识，不传则取当前登录用户") @RequestParam(required = false) String userId,
            @Schema(description = "分页参数：当前页第一条消息ID") @RequestParam(required = false) String firstId,
            @Schema(description = "每页条数，默认 100") @RequestParam(required = false, defaultValue = "100") Integer limit) {
        String user = resolveUser(userId);
        log.info("[Dify-AI] 查询会话历史消息, user={}, conversationId={}, firstId={}, limit={}", user, conversationId, firstId, limit);
        JsonNode data = difyClient.listMessages(conversationId, user, firstId, limit);
        log.info("[Dify-AI] 查询会话历史消息完成, conversationId={}, 消息数={}", conversationId, data.path("data").size());
        return success(data);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "删除会话")
    public AjaxResult deleteConversation(
            @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED) @PathVariable String conversationId,
            @Schema(description = "Dify 用户标识，不传则取当前登录用户") @RequestParam(required = false) String userId) {
        String user = resolveUser(userId);
        log.info("[Dify-AI] 删除会话, user={}, conversationId={}", user, conversationId);
        difyClient.deleteConversation(conversationId, user);
        log.info("[Dify-AI] 删除会话完成, conversationId={}", conversationId);
        return success();
    }

    /**
     * 逐行读取 Dify 的 SSE 流并原样转发给前端，保证前端能持续收到数据
     */
    private void forwardStream(InputStream stream, HttpServletResponse response) {
        int eventCount = 0;
        String currentConversationId = "";
        String currentMessageId = "";

        ServletOutputStream out;
        try {
            out = response.getOutputStream();
        } catch (IOException e) {
            log.error("[Dify-AI] 获取响应输出流失败，无法转发流式数据: {}", e.getMessage());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 写响应单独 try：这里的 IOException 代表前端断开（关页面/点了停止），属于正常现象
                try {
                    if (line.isEmpty()) {
                        // 空行代表一个 SSE 事件结束，补一个换行并立即 flush
                        out.write('\n');
                        out.flush();
                        continue;
                    }
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    out.write('\n');
                } catch (IOException clientAbort) {
                    log.warn("[Dify-AI] 前端连接已断开，停止转发, conversationId={}, 已输出事件数={}, 原因={}",
                            currentConversationId, eventCount, clientAbort.getMessage());
                    return;
                }

                if (line.startsWith(SSE_DATA_PREFIX)) {
                    eventCount++;
                    String data = line.substring(SSE_DATA_PREFIX.length()).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    try {
                        JsonNode node = objectMapper.readTree(data);
                        String event = node.path("event").asText("");
                        String conversationId = node.path("conversation_id").asText("");
                        String messageId = node.path("message_id").asText("");
                        if (StringUtils.isEmpty(currentConversationId) && StringUtils.isNotEmpty(conversationId)) {
                            currentConversationId = conversationId;
                            log.info("[Dify-AI] 本次对话 conversationId={}", conversationId);
                        }
                        if (StringUtils.isEmpty(currentMessageId) && StringUtils.isNotEmpty(messageId)) {
                            currentMessageId = messageId;
                            log.info("[Dify-AI] 本次对话 messageId={}", messageId);
                        }
                        if ("error".equals(event)) {
                            log.error("[Dify-AI] Dify 流式事件返回错误: {}", data);
                        }
                    } catch (Exception parseException) {
                        log.warn("[Dify-AI] SSE 数据解析失败（不影响转发）: {}", data);
                    }
                }
            }
            try {
                out.flush();
            } catch (IOException flushError) {
                log.warn("[Dify-AI] 流式输出收尾 flush 失败，前端可能已断开: {}", flushError.getMessage());
            }
            log.info("[Dify-AI] 流式输出结束, conversationId={}, messageId={}, 事件数={}",
                    currentConversationId, currentMessageId, eventCount);
        } catch (IOException upstreamError) {
            // 读上游（Dify）流失败：连接重置/网关中断等，必须让前端知道回答被截断
            log.error("[Dify-AI] 读取 Dify 流式响应失败（上游中断）, conversationId={}, 已输出事件数={}",
                    currentConversationId, eventCount, upstreamError);
            writeSseError(response, "与智能体服务的连接中断，回答可能不完整：" + upstreamError.getMessage());
        }
    }

    /**
     * 设置 SSE 响应头
     */
    private void prepareSseResponse(HttpServletResponse response) {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        // 关闭 nginx 缓冲，保证逐字输出
        response.setHeader("X-Accel-Buffering", "no");
    }

    /**
     * 以 SSE 事件格式返回错误信息，前端统一按 event=error 处理
     */
    private void writeSseError(HttpServletResponse response, String message) {
        prepareSseResponse(response);
        try {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("event", "error");
            error.put("message", message == null ? "未知错误" : message);
            error.put("created_at", System.currentTimeMillis() / 1000);
            ServletOutputStream out = response.getOutputStream();
            out.write(("data: " + error + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            log.warn("[Dify-AI] 已向客户端返回错误事件: {}", message);
        } catch (Exception e) {
            log.error("[Dify-AI] 返回错误事件失败: {}", e.getMessage());
        }
    }

    /**
     * 解析 Dify 用户标识：
     * 1. 优先使用前端显式传入的 userId；
     * 2. 其次取当前登录用户（接口虽已放行，但前端会携带 token，仍能拿到登录信息）；
     * 3. 都拿不到时使用匿名标识。
     */
    private String resolveUser(String userId) {
        if (StringUtils.isNotEmpty(userId)) {
            log.info("[Dify-AI] 使用前端传入的 Dify userId={}", userId);
            return userId;
        }
        try {
            Long loginUserId = SecurityUtils.getUserId();
            if (loginUserId != null) {
                String difyUserId = "xhzb-" + loginUserId;
                log.info("[Dify-AI] 根据登录用户生成 Dify userId={}", difyUserId);
                return difyUserId;
            }
        } catch (Exception e) {
            log.warn("[Dify-AI] 未获取到登录用户信息，使用匿名用户标识: {}", e.getMessage());
        }
        return ANONYMOUS_USER;
    }

    private String readAll(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[Dify-AI] 读取 Dify 错误响应失败: {}", e.getMessage());
            return "";
        }
    }

    private void closeQuietly(HttpResponse<InputStream> response) {
        if (response == null) {
            return;
        }
        try {
            response.body().close();
        } catch (Exception ignored) {
            // 忽略关闭异常
        }
    }
}
