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
}