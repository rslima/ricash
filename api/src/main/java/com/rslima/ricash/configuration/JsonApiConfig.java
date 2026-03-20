package com.rslima.ricash.configuration;

import com.toedter.spring.hateoas.jsonapi.JsonApiConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonApiConfig {
    @Bean
    public JsonApiConfiguration jsonApiConfiguration() {
        return new JsonApiConfiguration();
    }
}
