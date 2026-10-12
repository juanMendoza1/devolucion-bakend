package com.nodos.devolucion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TasaInteresDTO {
    private Long id;
    private Integer mes;
    private Integer ano;
    private BigDecimal tsi;
}