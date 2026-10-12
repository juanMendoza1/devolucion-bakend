package com.nodos.devolucion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HistorialCodigoDTO {
    private String nombreDevolucion; 
    private String nombreTarifa;
    private LocalDateTime fechaCalculo; 
    private String concepto;
    private Integer mesBase;
    private Integer anioBase;
    private BigDecimal capitalReliquidado;
    private BigDecimal interesesCalculados;
    private BigDecimal totalDevolucion;
}