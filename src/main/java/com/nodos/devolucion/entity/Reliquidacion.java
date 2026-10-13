package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @Column(name = "consolidado_tarifas", length = 100)
    private String consolidadoTarifas;
    
    @Column(name = "job_id", nullable = false)
    private String jobId; // Ej: Lote de Febrero 2026

    @Column(name = "factura_original_id", nullable = false)
    private Long facturaOriginalId; // Relación con la factura original (ID interno)

    // --- NUEVOS CAMPOS PARA VISUALIZACIÓN ---
    @Column(name = "factura", length = 100)
    private String factura; // El número de factura real

    @Column(name = "codigo_dataease", length = 100)
    private String codigoDataease; // El código del usuario
    // ----------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovechador_id")
    private Aprovechador aprovechador;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarifa_aplicada_id")
    private DevolTarifas tarifaAplicada;

    @Column(name = "concepto", nullable = false, length = 50)
    private String concepto; // "TA", "TRT", etc.

    // --- Parámetros Aplicados ---
    @Column(name = "mes_base")
    private Integer mesBase;

    @Column(name = "anio_base")
    private Integer anioBase;

    @Column(name = "desocupado_mes_base")
    private Boolean desocupadoMesBase; // La foto congelada del mes

    @Column(name = "tipo_interes", length = 20)
    private String tipoInteres; // "NINGUNO", "SIMPLE", "COMPUESTO"

    // --- Resultado Financiero ---
    @Column(name = "valor_base_tarifa", precision = 15, scale = 2)
    private BigDecimal valorBaseTarifa; // devolucion_aplicar original

    @Column(name = "capital_reliquidado", precision = 15, scale = 2)
    private BigDecimal capitalReliquidado; // Saldo inicial del mes

    @Column(name = "intereses_calculados", precision = 15, scale = 2)
    private BigDecimal interesesCalculados; // Interés de este mes

    @Column(name = "total_devolucion", precision = 15, scale = 2)
    private BigDecimal totalDevolucion; // capital + intereses de este mes

    // --- Auditoría ---
    @Column(name = "es_demo")
    private Boolean esDemo; // Fundamental para saber si es prueba o producción

    @Column(name = "fecha_calculo")
    private LocalDateTime fechaCalculo;

    // --- Trazabilidad del cálculo ---
    @Column(name = "detalle_tasas", length = 1000)
    private String detalleTasas; // Guarda la tasa aplicada o el motivo de rechazo
    
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucion_maestro_id")
    private DevolucionMaestro devolucionMaestro;
    
 // --- NUEVOS CAMPOS DE LA TARIFA ORIGINAL ---
    @Column(name = "aplicado", precision = 15, scale = 2)
    private BigDecimal aplicado;

    @Column(name = "ajustado", precision = 15, scale = 2)
    private BigDecimal ajustado;

    @Column(name = "devolucion", precision = 15, scale = 2)
    private BigDecimal devolucion;

    @Column(name = "porsubcon", precision = 15, scale = 2)
    private BigDecimal porsubcon;

    @Column(name = "subcon", precision = 15, scale = 2)
    private BigDecimal subcon;
}