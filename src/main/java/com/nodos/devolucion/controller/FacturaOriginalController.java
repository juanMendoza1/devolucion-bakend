package com.nodos.devolucion.controller;

import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facturas")
public class FacturaOriginalController {

    private final FacturaOriginalRepository repository;

    public FacturaOriginalController(FacturaOriginalRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<Page<FacturaOriginal>> obtenerFacturas(
            @PageableDefault(page = 0, size = 10) Pageable pageable,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String factura,
            @RequestParam(required = false) String codigoDataease) {

        Page<FacturaOriginal> resultado;

        if (jobId != null && !jobId.isBlank()) {
            resultado = repository.findByJobId(jobId, pageable);
        } else if (factura != null && !factura.isBlank()) {
            resultado = repository.findByFacturaContainingIgnoreCase(factura, pageable);
        } else if (codigoDataease != null && !codigoDataease.isBlank()) {
            resultado = repository.findByCodigoDataeaseContainingIgnoreCase(codigoDataease, pageable);
        } else {
            resultado = repository.findAll(pageable);
        }

        return ResponseEntity.ok(resultado);
    }
}