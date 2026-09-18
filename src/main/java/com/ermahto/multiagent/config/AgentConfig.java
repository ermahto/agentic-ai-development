package com.ermahto.multiagent.config;

import com.ermahto.multiagent.tool.JavaSpecialistTool;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.tools.FunctionTool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private final AgentProperties agentProperties;

    @Value("${google.api-key:}")
    private String googleApiKey;

    @PostConstruct
    void exportGoogleApiKey() {
        if (StringUtils.hasText(googleApiKey) && !StringUtils.hasText(System.getenv("GOOGLE_API_KEY"))) {
            System.setProperty("GOOGLE_API_KEY", googleApiKey);
        }
        if (!StringUtils.hasText(System.getenv("GOOGLE_API_KEY"))
                && !StringUtils.hasText(System.getProperty("GOOGLE_API_KEY"))) {
            log.warn("GOOGLE_API_KEY is not set. Gemini-backed agents will fail until the key is provided.");
        } else {
            log.info("Gemini authentication is configured for Google ADK");
        }
    }

    @Bean("javaSpecialistAgent")
    public BaseAgent javaSpecialistAgent() {
        AgentProperties.AgentDefinition specialist = agentProperties.javaSpecialist();
        log.info("Initializing Java Specialist agent '{}' on model {}", specialist.name(), specialist.model());
        return LlmAgent.builder()
                .name(specialist.name())
                .description(specialist.description())
                .model(specialist.model())
                .instruction(specialist.systemPrompt())
                .build();
    }

    @Bean
    public JavaSpecialistTool javaSpecialistTool(
            @Qualifier("javaSpecialistAgent") BaseAgent javaSpecialistAgent) {
        return new JavaSpecialistTool(javaSpecialistAgent);
    }

    @Bean
    public FunctionTool javaSpecialistFunctionTool(JavaSpecialistTool javaSpecialistTool) {
        return FunctionTool.create(javaSpecialistTool, "delegateToJavaSpecialist");
    }

    @Bean("orchestratorAgent")
    public BaseAgent orchestratorAgent(FunctionTool javaSpecialistFunctionTool) {
        AgentProperties.AgentDefinition orchestrator = agentProperties.orchestrator();
        log.info("Initializing Orchestrator agent '{}' on model {}", orchestrator.name(), orchestrator.model());
        return LlmAgent.builder()
                .name(orchestrator.name())
                .description(orchestrator.description())
                .model(orchestrator.model())
                .instruction(orchestrator.systemPrompt())
                .tools(javaSpecialistFunctionTool)
                .build();
    }

    @Bean
    public InMemoryRunner orchestratorRunner(@Qualifier("orchestratorAgent") BaseAgent orchestratorAgent) {
        return new InMemoryRunner(orchestratorAgent);
    }
}
