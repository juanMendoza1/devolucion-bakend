package com.nodos.devolucion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extraer el header "Authorization"
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userLogin;

        // 2. Si no hay header o no empieza con "Bearer ", continuamos con el siguiente filtro 
        // (Spring Security se encargará de rechazar la petición más adelante si la ruta era protegida)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraer el token (quitando los primeros 7 caracteres: "Bearer ")
        jwt = authHeader.substring(7);
        
        // 4. Extraer el nombre de usuario (tu usu_login) desde el token
        userLogin = jwtService.extractUsername(jwt);

        // 5. Si el token tiene un usuario y ese usuario aún no está autenticado en el contexto actual...
        if (userLogin != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            // Buscamos al usuario en la base de datos
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userLogin);

            // Validamos que el token pertenezca a este usuario y no haya expirado
            if (jwtService.isTokenValid(jwt, userDetails)) {
                
                // Creamos el objeto de autenticación de Spring
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null, // No pasamos credenciales (password) aquí por seguridad
                        userDetails.getAuthorities()
                );
                
                // Le agregamos detalles extra de la petición web (como la IP o la sesión)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Registramos al usuario como "Autenticado" en el contexto de seguridad
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        
        // 6. Pasamos la petición al siguiente filtro
        filterChain.doFilter(request, response);
    }
}