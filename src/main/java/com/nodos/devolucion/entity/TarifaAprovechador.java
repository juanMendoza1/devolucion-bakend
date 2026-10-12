package com.nodos.devolucion.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Vínculo histórico entre una tarifa (devol_tarifas) y un aprovechador.
 * Reemplaza la tabla puente sin trazabilidad (devol_tarifas_aprovechadores):
 * cada vínculo guarda cuándo y quién lo asignó y, si se retira, cuándo y quién lo retiró.
 * Un retiro NO borra el registro: lo deja con activo = false.
 */
@Entity
@Table(name = "tarifa_aprovechador", indexes = {
        @Index(name = "idx_tarifa_aprov_tarifa", columnList = "tarifa_id"),
        @Index(name = "idx_tarifa_aprov_aprovechador", columnList = "aprovechador_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarifaAprovechador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tarifa_id", nullable = false)
    private DevolTarifas tarifa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aprovechador_id", nullable = false)
    private Aprovechador aprovechador;

    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDateTime fechaAsignacion;

    @Column(name = "usuario_asignacion", length = 100)
    private String usuarioAsignacion;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "fecha_retiro")
    private LocalDateTime fechaRetiro;

    @Column(name = "usuario_retiro", length = 100)
    private String usuarioRetiro;

    /** Evento de auditoría (auditoria_asignacion) que originó el vínculo. */
    @Column(name = "auditoria_asignacion_id")
    private Long auditoriaAsignacionId;
}
