package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "versiones_tarifas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VersionTarifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true)
    private String jobId;

    @Column(name = "novedad", nullable = false)
    private String novedad; // Ej: "Ajuste IPC 2026"

    @Column(name = "version", nullable = false)
    private String version; // Ej: "V.1.0"

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "estado")
    private String estado; // Ej: "ACTIVA", "INACTIVA"
}