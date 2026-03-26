package com.rslima.ricash;

import com.rslima.ricash.configuration.CorsProperties;
import com.rslima.ricash.configuration.JwtClaimProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.mockito.Mockito.mock;

@TestConfiguration(proxyBeanMethods = false)
public class TestRicashApplication {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
	}

	@Bean
	JwtDecoder jwtDecoder() {
		return mock(JwtDecoder.class);
	}

	@Bean
	JwtClaimProperties jwtClaimProperties() {
		return new JwtClaimProperties("preferred_username", "realm_access/roles", "", "");
	}

	@Bean
	CorsProperties corsProperties() {
		return new CorsProperties(List.of("http://localhost:5173"));
	}

	public static void main(String[] args) {
		SpringApplication.from(RicashApplication::main).with(TestRicashApplication.class).run(args);
	}

}
