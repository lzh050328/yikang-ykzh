package com.xhzb.ai;

import com.xhzb.common.utils.SecurityUtils;
import com.xhzb.service.ChatHistoryService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class ChattingController {

    //注入ChatClient聊天客户端对象
    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient chatClient;

    @Autowired
    private ChatHistoryService chatHistoryService;

    /*  @PostMapping("/chat")
      public String chatting(String prompt, String chatId) {
          return chatClient.prompt(prompt).call().content();
      }*/

    @PostMapping("/chat")
    public Flux<String> chatting(String prompt, String chatId) {
        // 每次调用对话接口都保存会话ID
        chatHistoryService.saveHistory(SecurityUtils.getUserId().toString(), chatId);

        Flux<String> content = chatClient
                .prompt()
                .user(prompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
        return content;
    }
}
