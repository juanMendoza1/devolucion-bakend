package com.nodos.devolucion.controller;

import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facturas")
@CrossOrigin(origins = "*")
public class FacturaOriginalController {

    private final FacturaOriginalRepository repository;

    public FacturaOriginalController(FacturaOriginalRepository repository) {
        this.repository = repository;
    }

    /**
     * Endpoint para el Visor de Facturas.
     * Soporta paginación y filtros dinámicos.
     */
    @GetMapping
    public ResponseEntity<Page<FacturaOriginal>> obtenerFacturas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String factura,
            @RequestParam(required = false) String codigoDataease) {

        // Spring crea el objeto de paginación (ej: página 0, tamaño 10 registros)
        Pageable pageable = PageRequest.of(page, size);
        Page<FacturaOriginal> resultado;

        // Lógica de filtrado simple
        if (jobId != null && !jobId.isBlank()) {
            resultado = repository.findByJobId(jobId, pageable);
        } else if (factura != null && !factura.isBlank()) {
            resultado = repository.findByFacturaContainingIgnoreCase(factura, pageable);
        } else if (codigoDataease != null && !codigoDataease.isBlank()) {
            resultado = repository.findByCodigoDataeaseContainingIgnoreCase(codigoDataease, pageable);
        } else {
            // Si no hay filtros, devuelve todo paginado
            resultado = repository.findAll(pageable);
        }

        return ResponseEntity.ok(resultado);
    }
}