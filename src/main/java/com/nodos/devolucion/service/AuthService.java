package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.AuthRequest;
import com.nodos.devolucion.dto.AuthResponse;
import com.nodos.devolucion.dto.RegisterRequest;
import com.nodos.devolucion.entity.User;
import com.nodos.devolucion.repository.UserRepository;
import com.nodos.devolucion.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        // 1. Validar si el usuario ya existe
        if (userRepository.existsByLogin(request.login())) {
            throw new IllegalArgumentException("El usuario de login ya está registrado");
        }

        // 2. Crear el usuario encriptando la contraseña
        User user = User.builder()
                .login(request.login())
                .password(passwordEncoder.encode(request.password()))
                .nombre(request.nombre())
                .email(request.email())
                .swtActivo(true) // Lo activamos por defecto
                .build();

        // 3. Guardar en BD
        userRepository.save(user);

        // 4. Generar el Token y devolverlo
        String jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken, "Usuario registrado exitosamente");
    }

    public AuthResponse authenticate(AuthRequest request) {
        // 1. El AuthenticationManager de Spring verifica que el login y clave coincidan
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.login(),
                        request.password()
                )
        );

        // 2. Si pasa la línea anterior, las credenciales son correctas. Buscamos al usuario.
        User user = userRepository.findByLogin(request.login())
                .orElseThrow();

        // 3. Generamos el token y lo devolvemos
        String jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken, "Inicio de sesión exitoso");
    }
}