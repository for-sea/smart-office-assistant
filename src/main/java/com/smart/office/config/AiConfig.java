package com.smart.office.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.embedding.options.model}")
    private String embeddingModelName;

    /**
     * 配置 ChatClient，支持多轮对话
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder
                .defaultSystem("你是一个智能办公助手，请基于提供的上下文信息回答问题。" +
                        "如果上下文中没有相关信息，请明确告知用户'知识库中未找到相关信息'。" +
                        "不要编造信息，回答要简洁准确。")
                .defaultAdvisors(PromptChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 配置嵌入模型
     * Spring AI会自动创建OllamaEmbeddingModel的Bean
     * 这里显式配置可以设置更多参数
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaEmbeddingOptions.builder()
                        .model(embeddingModelName)
                        .build())
                .build();
    }

    /**
     * 配置对话记忆存储
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemory) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemory)
                .maxMessages(20)
                .build();
    }
/*
    Spring 自动注入Milvus客户端，不需要手动配置
    *//**
     * 配置 Milvus 客户端
     *//*
    @Bean
    public MilvusServiceClient milvusClient() {
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(milvusHost)
                .withPort(milvusPort)
                .build());
    }

    *//**
     * 配置向量存储（Milvus）
     *//*
    @Bean
    @Primary
    public VectorStore vectorStore(MilvusServiceClient embeddingClient, EmbeddingModel embeddingModel) {
        return MilvusVectorStore.builder(embeddingClient, embeddingModel)
                .collectionName(collectionName)
                .embeddingDimension(dimension)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .initializeSchema(true)  // 自动创建集合
                .build();
    }*/
}
