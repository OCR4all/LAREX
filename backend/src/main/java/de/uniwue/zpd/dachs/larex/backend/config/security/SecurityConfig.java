package de.uniwue.zpd.dachs.larex.backend.config.security;

import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final ActionRunService actionRunService;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter,
                          CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
                          ActionRunService actionRunService,
                          CorsProperties corsProperties) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
        this.actionRunService = actionRunService;
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsProperties.allowedOrigin()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled: SPA with JWT stateless authentication - no session cookies requiring CSRF protection
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                )
                .addFilterBefore(new ActionResultUploadPreAuthFilter(actionRunService), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**", "/api/public/**", "/api/v1/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicAwareBearerTokenResolver())
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthConverter)
                        )
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    @Bean
    public BearerTokenResolver publicAwareBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> isPublicShareDownloadRequest(request) ? null : delegate.resolve(request);
    }

    private boolean isPublicShareDownloadRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.startsWith("/public/") || uri.startsWith("/api/v1/public/"));
    }
}
