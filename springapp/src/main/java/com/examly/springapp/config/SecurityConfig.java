package com.examly.springapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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

/**
 * Central Spring Security configuration.
 *
 * Login (DAO authentication):
 *   UsernamePasswordAuthenticationToken -> AuthenticationManager (ProviderManager)
 *   -> DaoAuthenticationProvider -> MyUserDetailsService -> UserRepo -> PasswordEncoder.matches()
 *
 * Every other request:
 *   JwtAuthenticationFilter -> SecurityContext -> role rules below -> controller
 *   (resource ownership is additionally enforced inside the services).
 *
 * CSRF is disabled deliberately: the API is stateless, the JWT travels in the Authorization header
 * and no authentication cookie or server-side session exists, so a cross-site request cannot
 * ride on ambient browser credentials.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String MANAGER = "MANAGER";
    private static final String CLIENT = "CLIENT";

    private final MyUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final boolean publicReadEndpoints;

    public SecurityConfig(MyUserDetailsService userDetailsService, JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint, JwtAccessDeniedHandler accessDeniedHandler,
            @Value("${app.security.public-read-endpoints:false}") boolean publicReadEndpoints) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.publicReadEndpoints = publicReadEndpoints;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(daoAuthenticationProvider());
    }

    /** The JWT filter must run only inside the security chain, not also as a plain servlet filter. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .authenticationProvider(daoAuthenticationProvider())
            .authorizeHttpRequests(auth -> {
                auth
                    // ---- public
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/register", "/api/login").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll();

                if (publicReadEndpoints) {
                    // Only for the SRS auto-evaluation platform (see application.properties).
                    auth.requestMatchers(HttpMethod.GET, "/api/ticket", "/api/feedback").permitAll();
                }

                auth
                    // ---- tickets
                    .requestMatchers(HttpMethod.POST, "/api/ticket").hasRole(CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/ticket/user/**").hasRole(CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/ticket/agent/**").hasRole(CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/ticket").hasAnyRole(MANAGER, CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/ticket/*").hasRole(CLIENT)
                    .requestMatchers(HttpMethod.PUT, "/api/ticket/*").hasAnyRole(MANAGER, CLIENT)
                    .requestMatchers(HttpMethod.DELETE, "/api/ticket/*").hasRole(CLIENT)
                    // ---- support agents
                    .requestMatchers(HttpMethod.POST, "/api/supportAgent").hasRole(MANAGER)
                    .requestMatchers(HttpMethod.GET, "/api/supportAgent").hasRole(MANAGER)
                    .requestMatchers(HttpMethod.GET, "/api/supportAgent/*").hasAnyRole(MANAGER, CLIENT)
                    .requestMatchers(HttpMethod.PUT, "/api/supportAgent/*").hasRole(MANAGER)
                    .requestMatchers(HttpMethod.DELETE, "/api/supportAgent/*").hasRole(MANAGER)
                    // ---- feedback
                    .requestMatchers(HttpMethod.POST, "/api/feedback").hasRole(CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/feedback/user/**").hasRole(CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/feedback").hasAnyRole(MANAGER, CLIENT)
                    .requestMatchers(HttpMethod.GET, "/api/feedback/*").hasAnyRole(MANAGER, CLIENT)
                    .requestMatchers(HttpMethod.DELETE, "/api/feedback/*").hasRole(CLIENT)
                    // ---- everything else
                    .anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
