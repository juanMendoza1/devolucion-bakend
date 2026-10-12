package com.nodos.devolucion.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "job_id")
    private String jobId;
    
    /**
     * Historial de vínculos con aprovechadores (activos y retirados).
     * No se serializa; el frontend recibe solo los activos en "aprovechadores".
     */
    @JsonIgnore
    @OneToMany(mappedBy = "tarifa", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @Builder.Default
    private List<TarifaAprovechador> asignaciones = new ArrayList<>();

    /** Aprovechadores con vínculo activo (mantiene el contrato JSON que usa el frontend). */
    @JsonProperty("aprovechadores")
    public List<Aprovechador> getAprovechadoresActivos() {
        if (asignaciones == null) return List.of();
        return asignaciones.stream()
                .filter(a -> Boolean.TRUE.equals(a.getActivo()))
                .map(TarifaAprovechador::getAprovechador)
                .toList();
    }

}