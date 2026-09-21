package likelion.kitalk.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  private static final String[] SWAGGER_WHITELIST = {
      "/api/docs",
      "/api/docs/**",
      "/api/swagger-ui/**",
      "/swagger-ui.html",
      "/swagger-ui/**",
      "/v3/api-docs/**",
      "/v3/api-docs.yaml"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(SWAGGER_WHITELIST).permitAll()
            .requestMatchers("/auth/**", "/health", "/actuator/**", "/error").permitAll()
            .requestMatchers("/api/menu/**", "/api/touch/**", "/api/phone/**").permitAll()
            .anyRequest().denyAll()
        );
    return http.build();
  }
}
