package com.nodos.devolucion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditoriaDTO {
    private Long id;
    private String usuarioEmail;
    private LocalDateTime fechaAsignacion;
    private Integer cantidadTarifas;
    private String aprovechadorNit;
    private String aprovechadorNombre;
    private String detallesAdicionales;
    private String accion;
    private String jobId;
    private String novedad;
    private Integer mes;
    private Integer anio;
}
