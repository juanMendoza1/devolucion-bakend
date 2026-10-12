package com.nodos.devolucion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fila de tarifa vinculada a un aprovechador, con el momento en que se hizo el vínculo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TarifaAsignadaDTO {
    private Long id;
    private Integer mes;
    private Integer anio;
    private String estrato;
    private String concepto;
    private BigDecimal aplicado;
    private BigDecimal ajustado;
    private BigDecimal devolucion;
    private BigDecimal porsubcon;
    private BigDecimal subcon;
    private BigDecimal devolucionAplicar;
    private String liquidacion;
    private Integer esp;
    private LocalDateTime fechaAsignacion;
    private String usuarioAsignacion;
}
