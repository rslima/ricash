package com.rslima.ricash.configuration;

import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves spring.jdbc.template.query-timeout reaches the auto-configured
 * JdbcClient the repositories use (application.yml sets 30s; shortened here).
 */
@SpringBootTest(properties = "spring.jdbc.template.query-timeout=1s")
@Import(TestRicashApplication.class)
class QueryTimeoutTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void statementsExceedingTheTimeout_areCancelled() {
        assertThatThrownBy(() -> jdbcClient.sql("SELECT pg_sleep(5)").query().singleValue())
                .isInstanceOf(QueryTimeoutException.class);
    }
}
