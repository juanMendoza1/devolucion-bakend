package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "devolucion_maestro_tarifas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionMaestroTarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucion_maestro_id", nullable = false)
    private DevolucionMaestro devolucionMaestro;

    @Column(name = "job_id", nullable = false)
    private String jobId; 

    @Column(name = "nombre_tarifa")
    private String nombreTarifa;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado; 

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    @Column(name = "registros_procesados")
    private Integer registrosProcesados;
}