package com.nodos.devolucion.dto;

public record RegisterRequest(
        String login,
        String password,
        String nombre,
        String email
) {}