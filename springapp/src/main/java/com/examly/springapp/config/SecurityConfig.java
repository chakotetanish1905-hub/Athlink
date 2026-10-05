package com.examly.springapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
 * Login (DAO authentication, see the Spring Security PPT):
 *   POST /api/login -> AuthenticationManager -> DaoAuthenticationProvider
 *   -> MyUserDetailsService -> UserRepo -> PasswordEncoder.matches() -> JWT
 *
 * Every other request:
 *   Authorization: Bearer <JWT> -> JwtAuthenticationFilter -> SecurityContext -> role rules below
 *
 * No server session is created (STATELESS). CSRF is disabled because the API uses a JWT
 * in the Authorization header instead of cookies.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final MyUserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    // SRS "Platform Prerequisites": the evaluation platform may need GET /api/ticket and
    // GET /api/feedback without a token. Keep this false for normal use.
    @Value("${app.security.public-read-endpoints:false}")
    private boolean publicReadEndpoints;

    public SecurityConfig(MyUserDetailsService userDetailsService, JwtUtils jwtUtils,
            JwtAuthenticationEntryPoint authenticationEntryPoint, JwtAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    // Passwords are stored as BCrypt hashes, never as plain text.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Loads the user with MyUserDetailsService and checks the password with the PasswordEncoder.
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // Coordinates authentication by delegating to the DaoAuthenticationProvider.
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(daoAuthenticationProvider());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtUtils, userDetailsService);

        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

        http.authorizeHttpRequests(auth -> {
            // Public URLs
            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/api/register", "/api/login").permitAll();
            auth.requestMatchers("/error").permitAll();

            if (publicReadEndpoints) {
                auth.requestMatchers(HttpMethod.GET, "/api/ticket", "/api/feedback").permitAll();
            }

            // Tickets
            auth.requestMatchers(HttpMethod.POST, "/api/ticket").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket/user/**").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket/agent/**").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/ticket/*").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.PUT, "/api/ticket/*").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.DELETE, "/api/ticket/*").hasRole("CLIENT");

            // Support agents
            auth.requestMatchers(HttpMethod.POST, "/api/supportAgent").hasRole("MANAGER");
            auth.requestMatchers(HttpMethod.GET, "/api/supportAgent").hasRole("MANAGER");
            auth.requestMatchers(HttpMethod.GET, "/api/supportAgent/*").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.PUT, "/api/supportAgent/*").hasRole("MANAGER");
            auth.requestMatchers(HttpMethod.DELETE, "/api/supportAgent/*").hasRole("MANAGER");

            // Feedback
            auth.requestMatchers(HttpMethod.POST, "/api/feedback").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/feedback/user/**").hasRole("CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/feedback").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.GET, "/api/feedback/*").hasAnyRole("MANAGER", "CLIENT");
            auth.requestMatchers(HttpMethod.DELETE, "/api/feedback/*").hasRole("CLIENT");

            // Anything else needs a logged-in user
            auth.anyRequest().authenticated();
        });

        http.authenticationProvider(daoAuthenticationProvider());
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
