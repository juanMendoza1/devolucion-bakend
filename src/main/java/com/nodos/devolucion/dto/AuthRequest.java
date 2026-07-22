package com.nodos.devolucion.dto;

public record AuthRequest(
        String login,
        String password
) {}