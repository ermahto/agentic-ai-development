package com.ermahto.multiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agents")
public record AgentProperties(AgentDefinition orchestrator, AgentDefinition javaSpecialist) {

    public record AgentDefinition(
            String name,
            String description,
            String model,
            String systemPrompt
    ) {
        public AgentDefinition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Agent name is required");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("Agent model is required");
            }
            if (systemPrompt == null || systemPrompt.isBlank()) {
                throw new IllegalArgumentException("Agent system prompt is required");
            }
        }
    }
}
