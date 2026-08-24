package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.AuthRequest;
import com.nodos.devolucion.dto.AuthResponse;
import com.nodos.devolucion.dto.RegisterRequest;
import com.nodos.devolucion.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        // Si el usuario existe, el AuthService lanza IllegalArgumentException 
        // y el GlobalExceptionHandler se encarga de retornar el error 400.
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticate(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }
}