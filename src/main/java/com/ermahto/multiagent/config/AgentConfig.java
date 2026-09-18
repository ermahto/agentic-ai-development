package com.ermahto.multiagent.config;

import com.ermahto.multiagent.tool.JavaSpecialistTool;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.tools.FunctionTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    private final AgentProperties agentProperties;
    private final String configuredApiKey;

    public AgentConfig(
            AgentProperties agentProperties,
            @Value("${google.api-key:}") String configuredApiKey) {
        this.agentProperties = agentProperties;
        this.configuredApiKey = configuredApiKey;
    }

    @PostConstruct
    void validateGeminiAccess() {
        resolveApiKey();
        log.info("Gemini API key resolved for Google ADK (AI Studio, not Vertex)");
    }

    @Bean("javaSpecialistAgent")
    public BaseAgent javaSpecialistAgent() {
        AgentProperties.AgentDefinition specialist = agentProperties.javaSpecialist();
        log.info("Initializing Java Specialist agent '{}' on model {}", specialist.name(), specialist.model());
        return LlmAgent.builder()
                .name(specialist.name())
                .description(specialist.description())
                .model(gemini(specialist.model()))
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
                .model(gemini(orchestrator.model()))
                .instruction(orchestrator.systemPrompt())
                .tools(javaSpecialistFunctionTool)
                .build();
    }

    @Bean
    public InMemoryRunner orchestratorRunner(@Qualifier("orchestratorAgent") BaseAgent orchestratorAgent) {
        return new InMemoryRunner(orchestratorAgent);
    }

    /**
     * ADK's default Gemini client only reads {@code System.getenv("GOOGLE_API_KEY")}.
     * Passing the key here also honors Spring {@code google.api-key} and {@code GEMINI_API_KEY}.
     */
    private Gemini gemini(String modelName) {
        return Gemini.builder()
                .modelName(modelName)
                .apiKey(resolveApiKey())
                .build();
    }

    private String resolveApiKey() {
        String key = firstNonBlank(
                System.getenv("GOOGLE_API_KEY"),
                System.getenv("GEMINI_API_KEY"),
                configuredApiKey,
                System.getProperty("GOOGLE_API_KEY"));
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Set GOOGLE_API_KEY (or GEMINI_API_KEY) in the process environment, "
                            + "or set google.api-key in application.yml. System.setProperty is not read by the GenAI client.");
        }
        return key.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
