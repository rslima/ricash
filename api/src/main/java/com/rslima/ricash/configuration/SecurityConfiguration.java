package com.rslima.ricash.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.GET;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.
                authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(GET,"/index.html").permitAll()
                                .anyRequest().authenticated())
                .oauth2Login(login ->
                        login.loginPage("/oauth2/authorization/keycloak")
                                .defaultSuccessUrl("/api/v1/users"))

                .build();
    }
}
