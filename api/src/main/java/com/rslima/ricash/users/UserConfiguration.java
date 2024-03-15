package com.rslima.ricash.users;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserConfiguration {
    @Bean
    public UserService userService() {
        return new UserServiceBean();
    }
}
