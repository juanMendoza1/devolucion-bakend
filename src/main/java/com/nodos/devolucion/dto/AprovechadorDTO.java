package com.nodos.devolucion.dto;

import com.nodos.devolucion.entity.Aprovechador;

/**
 * Aprovechador tal como lo ve el frontend: id, NIT, nombre y si está activo.
 * Solo los activos se ofrecen al asignar tarifas.
 */
public record AprovechadorDTO(Long id, String nit, String nombre, Boolean activo) {

    public static AprovechadorDTO de(Aprovechador a) {
        return new AprovechadorDTO(a.getId(), a.getNit(), a.getNombreRazonSocial(), Boolean.TRUE.equals(a.getEstado()));
    }
}
