package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tasas_interes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TasaInteres {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mes", nullable = false)
    private Integer mes;

    @Column(name = "ano", nullable = false)
    private Integer ano; // Mantengo "ano" para ir acorde a tu estándar en FacturaOriginal

    // TSI (Tasa de Interés). Le damos buena escala para porcentajes con decimales
    @Column(name = "tsi", precision = 10, scale = 6, nullable = false) 
    private BigDecimal tsi; 
}