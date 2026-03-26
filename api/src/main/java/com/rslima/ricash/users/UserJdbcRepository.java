package com.rslima.ricash.users;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;

@RequiredArgsConstructor
public class UserJdbcRepository implements UserRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Optional<User> findById(String id) {
        return jdbcClient.sql("SELECT id FROM public.users WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new User(rs.getString("id")))
                .optional();
    }

    @Override
    public User create(String id) {
        jdbcClient.sql("INSERT INTO public.users (id) VALUES (:id)")
                .param("id", id)
                .update();
        return new User(id);
    }
}
