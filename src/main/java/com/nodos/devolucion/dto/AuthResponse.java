package com.nodos.devolucion.dto;

public record AuthResponse(
        String token,
        String message
) {}