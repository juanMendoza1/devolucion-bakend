package com.nodos.devolucion.config;

import com.nodos.devolucion.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Autoridad que genera User.getAuthorities() para el perfil administrador (codPerfil = 1). */
    public static final String ROL_ADMIN = "ROLE_PERFIL_1";

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Habilitamos CORS pasándole nuestra configuración
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Desactivamos CSRF porque usaremos tokens
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configuramos los permisos de las rutas
            .authorizeHttpRequests(auth -> auth
                // Despachos internos del servidor: la petición original ya pasó por el filtro JWT.
                // ASYNC lo usan las descargas en streaming (reportes Excel); ERROR, las páginas de error.
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                // Solo el login es público. El registro de usuarios queda para el administrador.
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/register").hasAuthority(ROL_ADMIN)
                // SockJS no envía el header Authorization en el handshake
                .requestMatchers("/ws-import/**").permitAll()
                // Operación destructiva: solo administrador (perfil 1)
                .requestMatchers(HttpMethod.DELETE, "/api/tarifas/reset-factory").hasAuthority(ROL_ADMIN)
                .anyRequest().authenticated()
            )

            // Sin token (o token inválido) respondemos 401 para que el frontend redirija al login
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            
            // API Stateless
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Proveedor y Filtro JWT
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 2. Definimos las reglas exactas de CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (configurable en application.properties: app.cors.allowed-origins)
        configuration.setAllowedOrigins(allowedOrigins); 
        
        // Métodos permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Cabeceras permitidas (muy importante para enviar el token y el JSON)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        
        // Permitir credenciales
        configuration.setAllowCredentials(true);

        // Aplicamos esta configuración a todas las rutas (/**)
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}