package site.festifriends.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import site.festifriends.common.jwt.JwtAccessDeniedHandler;
import site.festifriends.common.jwt.JwtAuthenticationEntryPoint;
import site.festifriends.common.jwt.JwtAuthenticationFilter;
import site.festifriends.common.jwt.JwtExceptionFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final ObjectMapper objectMapper;

    private final String[] readOnlyUrl = {
        "/favicon.ico",
        "/actuator/health",
        "/api-docs/**",
        "/v3/api-docs/**",
        "/swagger-ui/**", "/swagger",
        "/api/v1/auth/**",
        "/api/v1/performances",
        "/api/v1/performances/**",
        "/api/v1/performances/top-favorites",
        "/api/v1/performances/top-groups",
        "/api/v1/profiles/*",
        "/api/v1/users/check-nickname",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .cors(cors -> corsConfigurationSource())
            .sessionManagement(sessionManagement ->
                sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter.class)
            .authorizeHttpRequests(authorizeHttpRequests ->
                authorizeHttpRequests
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/groups/{groupId}").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/profiles/me").authenticated()
                    .requestMatchers(HttpMethod.GET, readOnlyUrl).permitAll()
                    .requestMatchers("/chat/**").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(exception ->
                exception
                    .accessDeniedHandler(new JwtAccessDeniedHandler(objectMapper))
                    .authenticationEntryPoint(new JwtAuthenticationEntryPoint(objectMapper)));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
        configuration.setAllowedMethods(
            List.of("GET", "POST", "OPTIONS", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
