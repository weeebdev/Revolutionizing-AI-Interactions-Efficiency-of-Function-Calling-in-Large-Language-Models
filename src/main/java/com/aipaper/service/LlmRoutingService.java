package com.aipaper.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

public interface LlmRoutingService {

    String call(LlmProvider provider, String userMessage);

    ChatResponse callWithDetails(LlmProvider provider, String userMessage);

    <T> T callWithStructuredOutput(LlmProvider provider, String userMessage, Class<T> responseType);

    ChatClient getClient(LlmProvider provider);
}
