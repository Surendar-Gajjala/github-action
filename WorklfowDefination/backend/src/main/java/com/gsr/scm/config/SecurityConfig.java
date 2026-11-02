package com.gsr.scm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsr.scm.common.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;

import static com.gsr.scm.config.Roles.ADMIN;
import static com.gsr.scm.config.Roles.AUTHOR;
import static com.gsr.scm.config.Roles.ENGINEERING;
import static com.gsr.scm.config.Roles.MANAGER;

/**
 * HTTP Basic, stateless. Demo users are for local development only; replace the {@link UserDetailsService}
 * with your identity provider (e.g. OAuth2 resource server) for any shared environment.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                .httpBasic(b -> b.authenticationEntryPoint(jsonEntryPoint(mapper)))
                .exceptionHandling(e -> e.authenticationEntryPoint(jsonEntryPoint(mapper))
                        .accessDeniedHandler(jsonAccessDenied(mapper)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**", "/actuator/health").permitAll()
                        // Business data changes
                        .requestMatchers(HttpMethod.POST, "/api/items/**", "/api/parts/**").hasAnyRole(AUTHOR, ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/items/**", "/api/parts/**").hasAnyRole(AUTHOR, ADMIN)
                        // Workflow administration
                        .requestMatchers(HttpMethod.POST, "/api/workflows/definitions/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/workflows/definitions/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/workflows/lifecycle-transitions/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/workflows/lifecycle-transitions/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/workflows/instances/*/retry").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/workflows/instances").hasAnyRole(AUTHOR, ADMIN)
                        // Approvals: role is checked per task (candidate group) in WorkflowTaskService
                        .requestMatchers(HttpMethod.POST, "/api/workflows/tasks/**").hasAnyRole(ENGINEERING, MANAGER)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("author").password(encoder.encode("author")).roles(AUTHOR).build(),
                User.withUsername("engineer").password(encoder.encode("engineer")).roles(ENGINEERING).build(),
                User.withUsername("manager").password(encoder.encode("manager")).roles(MANAGER).build(),
                User.withUsername("admin").password(encoder.encode("admin")).roles(ADMIN, AUTHOR).build());
    }

    private AuthenticationEntryPoint jsonEntryPoint(ObjectMapper mapper) {
        // No WWW-Authenticate header, so browsers do not pop up a native login dialog.
        return (request, response, ex) -> write(mapper, response, 401, "Unauthorized",
                "Authentication required", request.getRequestURI());
    }

    private AccessDeniedHandler jsonAccessDenied(ObjectMapper mapper) {
        return (request, response, ex) -> write(mapper, response, 403, "Forbidden",
                "You are not allowed to perform this operation", request.getRequestURI());
    }

    private static void write(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response, int status,
                              String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), status, error, message, path, null, null));
    }
}
