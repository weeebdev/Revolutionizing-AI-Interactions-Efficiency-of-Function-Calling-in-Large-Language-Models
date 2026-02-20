package com.aipaper.service.impl;

import com.aipaper.service.LlmProvider;
import com.aipaper.service.LlmRoutingService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class LlmRoutingServiceImpl implements LlmRoutingService {

    private final Map<LlmProvider, ChatClient> clients;

    public LlmRoutingServiceImpl(
            @Qualifier("ollamaChatModel") ChatModel ollama,
            @Qualifier("geminiChatModel") ChatModel gemini,
            @Qualifier("groqChatModel") ChatModel groq) {

        this.clients = new EnumMap<>(LlmProvider.class);
        this.clients.put(LlmProvider.OLLAMA, ChatClient.builder(ollama).build());
        this.clients.put(LlmProvider.GEMINI, ChatClient.builder(gemini).build());
        this.clients.put(LlmProvider.GROQ, ChatClient.builder(groq).build());
    }

    @Override
    public String call(LlmProvider provider, String userMessage) {
        return getClient(provider)
                .prompt()
                .user(userMessage)
                .call()
                .content();
    }

    @Override
    public ChatResponse callWithDetails(LlmProvider provider, String userMessage) {
        return getClient(provider)
                .prompt()
                .user(userMessage)
                .call()
                .chatResponse();
    }

    @Override
    public <T> T callWithStructuredOutput(LlmProvider provider, String userMessage, Class<T> responseType) {
        return getClient(provider)
                .prompt()
                .user(userMessage)
                .call()
                .entity(responseType);
    }

    @Override
    public ChatClient getClient(LlmProvider provider) {
        ChatClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("No client configured for provider: " + provider);
        }
        return client;
    }
}
