package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "factura_original")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaOriginal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_dataease")
    private String codigoDataease;

    @Column(name = "esp")
    private String esp;

    @Column(name = "usuario")
    private String usuario;

    @Column(name = "factura")
    private String factura;

    @Column(name = "uso")
    private String uso;

    @Column(name = "estrato")
    private String estrato; // Soportar "C", "1", "2", etc.

    @Column(name = "desocupa")
    private Boolean desocupa;

    @Column(name = "descto_pap")
    private Boolean desctoPap; // En CSV viene "NO"/"SI"

    @Column(name = "medicion_aforo")
    private String medicionAforo;

    @Column(name = "estado")
    private String estado;

    @Column(name = "porcentaje")
    private Double porcentaje;

    @Column(name = "pagada")
    private Boolean pagada; // En CSV viene 0 o 1

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    @Column(name = "vlr_factu")
    private BigDecimal vlrFactu;

    @Column(name = "vlr_pagado")
    private BigDecimal vlrPagado;

    @Column(name = "mes")
    private Integer mes;

    @Column(name = "ano")
    private Integer ano;

    @Column(name = "toneladas")
    private Double toneladas;
    
 // Añadir este campo a los que ya tienes (estrato, uso, etc.)
    @Column(name = "job_id")
    private String jobId;
}