package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "devol_tarifas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolTarifas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mes")
    private Integer mes;

    @Column(name = "anio")
    private Integer anio;

    @Column(name = "estrato")
    private String estrato;

    @Column(name = "concepto")
    private String concepto;

    @Column(name = "aplicado")
    private BigDecimal aplicado;

    @Column(name = "ajustado")
    private BigDecimal ajustado;

    @Column(name = "devolucion")
    private BigDecimal devolucion;

    @Column(name = "porsubcon")
    private BigDecimal porsubcon;

    @Column(name = "subcon")
    private BigDecimal subcon;

    @Column(name = "devolucion_aplicar")
    private BigDecimal devolucionAplicar;

    @Column(name = "liquidacion")
    private String liquidacion;

    @Column(name = "esp")
    private Integer esp;

    // Campo vital para vincular con la auditoría
    @Column(name = "job_id")
    private String jobId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovechador_id")
    private Aprovechador aprovechador;
    
}