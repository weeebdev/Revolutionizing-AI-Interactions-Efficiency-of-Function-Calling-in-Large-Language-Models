package com.aipaper.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientConfig {

    @Bean
    @Qualifier("ollamaChatModel")
    public ChatModel ollamaChatModel(
            @Value("${llm.ollama.base-url}") String baseUrl,
            @Value("${llm.ollama.model}") String model) {

        var api = new OllamaApi.Builder()
                .baseUrl(baseUrl)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder()
                        .model(model)
                        .temperature(0.0)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("geminiChatModel")
    public ChatModel geminiChatModel(
            @Value("${llm.gemini.api-key}") String apiKey,
            @Value("${llm.gemini.base-url}") String baseUrl,
            @Value("${llm.gemini.model}") String model) {

        var api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.0)
                        .build())
                .build();
    }

    @Bean
    @Qualifier("groqChatModel")
    public ChatModel groqChatModel(
            @Value("${llm.groq.api-key}") String apiKey,
            @Value("${llm.groq.base-url}") String baseUrl,
            @Value("${llm.groq.model}") String model) {

        var api = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.0)
                        .build())
                .build();
    }
}
