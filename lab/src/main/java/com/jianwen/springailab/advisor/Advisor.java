package com.jianwen.springailab.advisor;

import com.jianwen.springailab.chat.ChatOptions;
import com.jianwen.springailab.chat.ChatResponse;
import com.jianwen.springailab.chat.Message;

import java.util.List;

/**
 * Spring AI Advisor-shaped interceptors around a ChatClient call.
 * before: mutate messages / options; after: inspect or rewrite response.
 */
public interface Advisor {
    default List<Message> before(List<Message> messages, ChatOptions options) {
        return messages;
    }

    default ChatOptions beforeOptions(List<Message> messages, ChatOptions options) {
        return options;
    }

    default ChatResponse after(List<Message> messages, ChatOptions options, ChatResponse response) {
        return response;
    }

    String name();
}
