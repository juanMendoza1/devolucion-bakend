package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reliquidaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reliquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- ADN del Origen ---
    @Column(name = "job_id", nullable = false)
    private String jobId; // Ej: Lote de Febrero 2026

    @Column(name = "factura_original_id", nullable = false)
    private Long facturaOriginalId; // Relación con la factura original

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovechador_id")
    private Aprovechador aprovechador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarifa_aplicada_id")
    private DevolTarifas tarifaAplicada;

    @Column(name = "concepto", nullable = false, length = 50)
    private String concepto; // "TA", "TRT", etc. (No quemado)

    // --- Parámetros Aplicados ---
    @Column(name = "mes_base")
    private Integer mesBase;

    @Column(name = "anio_base")
    private Integer anioBase;

    @Column(name = "desocupado_mes_base")
    private Boolean desocupadoMesBase; // La foto congelada del mes 1

    @Column(name = "tipo_interes", length = 20)
    private String tipoInteres; // "NINGUNO", "SIMPLE", "COMPUESTO"

    // --- Resultado Financiero ---
    @Column(name = "valor_base_tarifa", precision = 15, scale = 2)
    private BigDecimal valorBaseTarifa; // devolucion_aplicar del estrato

    @Column(name = "capital_reliquidado", precision = 15, scale = 2)
    private BigDecimal capitalReliquidado; // Si estaba desocupado = 0, sino = valorBase

    @Column(name = "intereses_calculados", precision = 15, scale = 2)
    private BigDecimal interesesCalculados;

    @Column(name = "total_devolucion", precision = 15, scale = 2)
    private BigDecimal totalDevolucion; // capital + intereses

    // --- Auditoría ---
    @Column(name = "es_demo")
    private Boolean esDemo; // Fundamental para saber si es prueba o producción

    @Column(name = "fecha_calculo")
    private LocalDateTime fechaCalculo;
}