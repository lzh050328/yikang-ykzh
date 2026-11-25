package com.xhzb.ai.dify.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhzb.ai.dify.DifyApiException;
import com.xhzb.ai.dify.config.DifyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Dify 服务端 API 客户端
 *
 * <p>只使用 JDK 自带的 {@link HttpClient}，无需引入额外依赖。
 * 所有接口都直连 Dify 同名接口：
 * <ul>
 *     <li>POST /chat-messages 发送对话消息（streaming）</li>
 *     <li>GET  /conversations 获取会话列表</li>
 *     <li>GET  /messages 获取会话历史消息</li>
 *     <li>DELETE /conversations/{conversation_id} 删除会话</li>
 * </ul>
 *
 * @author xhzb
 */
@Slf4j
@Component
public class DifyClient {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final DifyProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile HttpClient httpClient;

    public DifyClient(DifyProperties properties) {
        this.properties = properties;
    }

    /**
     * 发送对话消息（streaming 模式）
     *
     * <p>返回未读取的响应流，由调用方逐行消费并转发给前端。
     *
     * @param payload 请求体（response_mode 由调用方设置为 streaming）
     * @return 原始响应（body 为未消费的输入流）
     */
    public HttpResponse<InputStream> chatMessagesStream(ObjectNode payload) throws Exception {
        String url = buildUrl("/chat-messages", null);
        String body = objectMapper.writeValueAsString(payload);
        log.info("[Dify] 调用发送对话消息接口 POST {} , 请求体={}", url, body);

        HttpRequest request = authRequest(url)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        log.info("[Dify] 发送对话消息接口已响应, status={}", response.statusCode());
        return response;
    }

    /**
     * 获取会话列表 GET /conversations
     *
     * @param user   Dify 用户标识
     * @param lastId 上一页最后一条记录的ID（分页用，可空）
     * @param limit  每页条数
     */
    public JsonNode listConversations(String user, String lastId, Integer limit) {
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"user", user});
        params.add(new String[]{"last_id", lastId});
        params.add(new String[]{"limit", String.valueOf(limit == null ? 20 : limit)});
        String url = buildUrl("/conversations", params);
        log.info("[Dify] 调用获取会话列表接口 GET {}", url);

        HttpRequest request = authRequest(url)
                .timeout(Duration.ofMillis(readTimeout()))
                .GET()
                .build();
        return execute(request, "获取会话列表");
    }

    /**
     * 获取会话历史消息 GET /messages
     *
     * @param conversationId 会话ID
     * @param user           Dify 用户标识
     * @param firstId        当前页第一条消息ID（分页用，可空）
     * @param limit          每页条数
     */
    public JsonNode listMessages(String conversationId, String user, String firstId, Integer limit) {
        List<String[]> params = new ArrayList<>();
        params.add(new String[]{"conversation_id", conversationId});
        params.add(new String[]{"user", user});
        params.add(new String[]{"first_id", firstId});
        params.add(new String[]{"limit", String.valueOf(limit == null ? 100 : limit)});
        String url = buildUrl("/messages", params);
        log.info("[Dify] 调用获取会话历史消息接口 GET {}", url);

        HttpRequest request = authRequest(url)
                .timeout(Duration.ofMillis(readTimeout()))
                .GET()
                .build();
        return execute(request, "获取会话历史消息");
    }

    /**
     * 删除会话 DELETE /conversations/{conversation_id}
     *
     * @param conversationId 会话ID
     * @param user           Dify 用户标识
     */
    public JsonNode deleteConversation(String conversationId, String user) {
        String url = buildUrl("/conversations/" + conversationId, null);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("user", user);
        log.info("[Dify] 调用删除会话接口 DELETE {} , 请求体={}", url, body);

        HttpRequest request = authRequest(url)
                .timeout(Duration.ofMillis(readTimeout()))
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return execute(request, "删除会话");
    }

    private JsonNode execute(HttpRequest request, String apiName) {
        try {
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();
            log.info("[Dify] {} 响应状态={}, 报文={}", apiName, status, abbreviate(body));

            if (status < 200 || status >= 300) {
                throw new DifyApiException("Dify " + apiName + "失败(status=" + status + ")：" + abbreviate(body));
            }
            if (body == null || body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (DifyApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Dify] {} 线程被中断", apiName, e);
            throw new DifyApiException("Dify " + apiName + "被中断", e);
        } catch (Exception e) {
            log.error("[Dify] {} 调用异常: {}", apiName, e.getMessage(), e);
            throw new DifyApiException("Dify " + apiName + "异常：" + e.getMessage(), e);
        }
    }

    private HttpRequest.Builder authRequest(String url) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new DifyApiException("未配置 dify.api-key，无法调用 Dify 接口");
        }
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + apiKey)
                .header("Content-Type", "application/json");
    }

    /**
     * 拼接 Dify 请求地址，base-url 支持以 / 结尾
     */
    private String buildUrl(String apiPath, List<String[]> params) {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new DifyApiException("未配置 dify.base-url，无法调用 Dify 接口");
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        StringBuilder url = new StringBuilder(base).append(apiPath);
        if (params != null && !params.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            for (String[] param : params) {
                if (param == null || param.length < 2) {
                    continue;
                }
                String value = param[1];
                if (value == null || value.isEmpty()) {
                    continue;
                }
                pairs.add(param[0] + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
            if (!pairs.isEmpty()) {
                url.append('?').append(String.join("&", pairs));
            }
        }
        return url.toString();
    }

    private HttpClient client() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    int connectTimeout = properties.getConnectTimeout() == null ? 10000 : properties.getConnectTimeout();
                    httpClient = HttpClient.newBuilder()
                            // Dify 前置一般为 nginx，固定 HTTP/1.1 避免协议升级导致流式读取异常
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofMillis(connectTimeout))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    log.info("[Dify] HttpClient 初始化完成, baseUrl={}, connectTimeout={}ms",
                            properties.getBaseUrl(), connectTimeout);
                }
            }
        }
        return httpClient;
    }

    private int readTimeout() {
        return properties.getReadTimeout() == null ? 600000 : properties.getReadTimeout();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 2000 ? text.substring(0, 2000) + "...(已截断)" : text;
    }
}
