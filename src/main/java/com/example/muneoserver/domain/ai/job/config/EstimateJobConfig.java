package com.example.muneoserver.domain.ai.job.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EstimateJobConfig {

    @Bean
    public ObjectMapper estimateJobObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
