package com.nodos.devolucion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConsolidadoMesDTO {
	private String tarifa;
    private Integer mes;
    private Integer ano;
    private BigDecimal sumaCapital;
    private BigDecimal sumaIntereses;
    private BigDecimal sumaTotal;

}