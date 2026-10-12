package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "devolucion_maestro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionMaestro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_devolucion", nullable = false, length = 150)
    private String nombreDevolucion;

    @Column(name = "fecha_calculo")
    private LocalDateTime fechaCalculo;

    @Column(name = "usuario", length = 100)
    private String usuario;

    @Column(name = "es_demo")
    private Boolean esDemo;

    @Column(name = "cantidad_tarifas")
    private Integer cantidadTarifas;

    @Column(name = "cantidad_registros")
    private Integer cantidadRegistros;

    @Column(name = "total_devolucion", precision = 15, scale = 2)
    private BigDecimal totalDevolucion;    

    @Column(name = "total_capital", precision = 20, scale = 2)
    private BigDecimal totalCapital = BigDecimal.ZERO;

    @Column(name = "total_intereses", precision = 20, scale = 2)
    private BigDecimal totalIntereses = BigDecimal.ZERO;
}