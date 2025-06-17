package com.kapil.cognos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.kapil.cognos.customFilter.UserValidationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, UserValidationFilter cognosFilter)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
		//	.httpBasic(Customizer.withDefaults()) // activates HTTP Basic authentication
			.authorizeHttpRequests(authz -> authz
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**" ).permitAll()
                .requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
			.formLogin(form -> form.disable())
			.addFilterBefore(cognosFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
	
}
