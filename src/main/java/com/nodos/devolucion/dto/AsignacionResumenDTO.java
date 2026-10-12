package com.nodos.devolucion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionResumenDTO {
    private String jobId;
    private String loteNovedad;
    private Integer mes;
    private Integer anio;
    private Long aprovechadorId;
    private String aprovechadorNit;
    private String aprovechadorNombre;
    private Long cantidadRegistros;
    private LocalDateTime primeraAsignacion;
    private LocalDateTime ultimaAsignacion;
}
