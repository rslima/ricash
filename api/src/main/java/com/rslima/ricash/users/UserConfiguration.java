package com.rslima.ricash.users;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class UserConfiguration {

    @Bean
    public UserRepository userRepository(JdbcClient client) {
        return new UserJdbcRepository(client);
    }
}
