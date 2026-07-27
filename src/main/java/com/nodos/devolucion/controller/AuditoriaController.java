package com.nodos.devolucion.controller;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
@CrossOrigin(origins = "*") // Recuerda ajustar esto en producción según tu dominio de React
public class AuditoriaController {

    private final AuditoriaImportacionRepository auditoriaRepository;

    public AuditoriaController(AuditoriaImportacionRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    /**
     * Obtiene el listado completo del historial de importaciones.
     * Ordenado por fecha de inicio descendente (las más nuevas primero).
     */
    @GetMapping
    public ResponseEntity<List<AuditoriaImportacion>> listarAuditorias() {
        List<AuditoriaImportacion> historial = auditoriaRepository.findAll(
                Sort.by(Sort.Direction.DESC, "fechaInicio")
        );
        return ResponseEntity.ok(historial);
    }

    /**
     * Obtiene el registro de auditoría específico usando su jobId.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<AuditoriaImportacion> obtenerAuditoriaPorJobId(@PathVariable String jobId) {
        return auditoriaRepository.findByJobId(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}