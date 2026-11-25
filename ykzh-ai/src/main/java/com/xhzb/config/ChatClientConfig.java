package com.xhzb.config;

import com.xhzb.constants.Constants;
import com.xhzb.tool.NursingProjectTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import redis.clients.jedis.JedisPooled;

/*
    聊天的客户端有很多配置不适合在yml中
    比如:
        1.系统提示词很长
        2.模型的对接方式, 到底是本地的,还是openAI的, 还是其他方法
            对接不同的方式需要创建不同的对象才行!!!!!!!!
       ...
    综合以上因素, 这个客户端必须由我们自己创建而且还需要放入ioc容器中
 */
@Configuration // 标注这是一个配置类
public class ChatClientConfig {

    /** 向量库专用 Redis 地址，部署时用 xhzb.vector-store.host / port 覆盖 */
    @Value("${xhzb.vector-store.host:localhost}")
    private String vectorStoreHost;

    @Value("${xhzb.vector-store.port:6378}")
    private int vectorStorePort;

    /**
     * DeepSeek 走的是 OpenAI 兼容协议，所以模型参数类型是 OpenAiChatModel
     * （Spring AI 1.x 里没有 DeepSeekChatModel 这个类，可用的就是 OpenAiChatModel + base-url 指向 api.deepseek.com）
     */
    @Bean("deepseekChatClient")
    public ChatClient deepseekChatClient(OpenAiChatModel openAiChatModel,
                                        NursingProjectTool nursingProjectTool,
                                        ChatMemory chatMemory,
                                        VectorStore vectorStore) {
        // 日志增强器
        SimpleLoggerAdvisor loggerAdvisor = new SimpleLoggerAdvisor();
        // 会话记忆增强器
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        // RAG 增强器：先把用户问题丢进向量库检索相似片段，再把片段拼进提示词
        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .similarityThreshold(0.7) // 相似度阈值，低于它的片段不要
                        .topK(3)                  // 最多取 3 个片段
                        .build())
                .build();
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(Constants.PROMPT)   // 配置系统提示词
                .defaultTools(nursingProjectTool)   // 配置技能
                .defaultAdvisors(loggerAdvisor, memoryAdvisor, questionAnswerAdvisor) // 配置增强器
                .build();
    }

    @Bean("ollamaai")
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel, NursingProjectTool nursingProjectTool) {
        // 构建聊天客户端, 必须有模型的参数, 才能构建, 还可以设置其他的重要的参数
        return
                ChatClient.builder(ollamaChatModel)
                        .defaultSystem(Constants.PROMPT)// 配置系统提示词
                        .defaultTools(nursingProjectTool)// 配置技能
                        .build();
    }

    @Bean
    @Lazy // 懒加载：Ollama 没启动时不影响应用启动，首次用到向量库才初始化
    public VectorStore redisVectorStore(OllamaEmbeddingModel ollamaEmbeddingModel) {
        // 向量库独立redis地址，不和业务redis共用（地址从配置读取，方便换环境）
        JedisPooled jedisPooled = new JedisPooled(vectorStoreHost, vectorStorePort);
        return RedisVectorStore.builder(jedisPooled, ollamaEmbeddingModel)
                .indexName("spring-ai-index") //向量索引名称
                .prefix("doc:") //向量索引名称
                .initializeSchema(true) //自动建索引，维度取自 qwen3-embedding:0.6b（1024）
                .build();
    }
    /**
     * 构建AI健康评估的对话实例 - chatClientDeepseekHealth
     * @param deepSeekChatModel
     * @return
     */
    @Bean
    public ChatClient chatClientDeepseekHealth(OpenAiChatModel deepSeekChatModel) {
        return ChatClient.builder(deepSeekChatModel)
                .defaultSystem("你是一个健康评估专家，专门用来评估老人的健康情况")
                .build();
    }
}
