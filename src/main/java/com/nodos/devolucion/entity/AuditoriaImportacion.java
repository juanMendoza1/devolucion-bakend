package com.nodos.devolucion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_importacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditoriaImportacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true)
    private String jobId; // El UUID para WebSockets y relación con FacturaOriginal

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Column(name = "usu_ideregistro")
    private String usuIdRegistro; // <--- ¡AQUÍ ESTÁ EL USUARIO QUE IMPORTA!

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "total_registros")
    private Integer totalRegistros;

    @Column(name = "estado")
    private String estado; // Ej: "EN_PROCESO", "COMPLETADO", "ERROR"

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError; 
}