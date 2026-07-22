package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.AuthRequest;
import com.nodos.devolucion.dto.AuthResponse;
import com.nodos.devolucion.dto.RegisterRequest;
import com.nodos.devolucion.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.register(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@RequestBody AuthRequest request) {
        // Si las credenciales son incorrectas, Spring Security lanzará automáticamente 
        // un error 403 Forbidden o 401 Unauthorized, lo cual es el comportamiento estándar.
        return ResponseEntity.ok(authService.authenticate(request));
    }
}