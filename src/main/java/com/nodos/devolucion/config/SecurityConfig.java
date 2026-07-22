package com.nodos.devolucion.config;

import com.nodos.devolucion.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Desactivamos CSRF porque usaremos tokens (las protecciones CSRF son para sesiones tradicionales)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configuramos los permisos de las rutas
            .authorizeHttpRequests(auth -> auth
                // Dejamos pública cualquier ruta que empiece con /api/auth/ (para poder hacer login)
                .requestMatchers("/api/auth/**").permitAll()
                // Cualquier otra petición del sistema REQUIERE estar autenticado
                .anyRequest().authenticated()
            )
            
            // Le decimos a Spring que NO guarde sesiones en memoria (es una API REST, debe ser Stateless)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Le pasamos el proveedor que creamos en ApplicationConfig
            .authenticationProvider(authenticationProvider)
            
            // Insertamos nuestro filtro JWT ANTES del filtro tradicional de usuario/contraseña de Spring
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
