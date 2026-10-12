package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_asignacion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaAsignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail; // Quien hizo el cambio (extraído del JWT)

    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDateTime fechaAsignacion;

    @Column(name = "cantidad_tarifas", nullable = false)
    private Integer cantidadTarifas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprovechador_id", nullable = false)
    private Aprovechador aprovechador;

    @Column(name = "detalles_adicionales")
    private String detallesAdicionales; // Opcional: Para guardar notas o el JobId

    /** ASIGNAR o RETIRAR */
    @Column(name = "accion", length = 20)
    private String accion;

    /** Lote (importación) al que pertenecen las tarifas afectadas. Null si la operación abarcó varios lotes. */
    @Column(name = "job_id")
    private String jobId;

    @Column(name = "mes")
    private Integer mes;

    @Column(name = "anio")
    private Integer anio;
}