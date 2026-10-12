package com.nodos.devolucion.controller;

import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facturas")
public class FacturaOriginalController {

    private final FacturaOriginalRepository repository;

    public FacturaOriginalController(FacturaOriginalRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/periodos")
    public ResponseEntity<List<Map<String, Integer>>> obtenerPeriodos() {
        return ResponseEntity.ok(repository.findDistinctPeriodos());
    }

    @GetMapping
    public ResponseEntity<Page<FacturaOriginal>> obtenerFacturas(
            @PageableDefault(page = 0, size = 10) Pageable pageable,
            @RequestParam(required = false) String factura,
            @RequestParam(required = false) String codigoDataease,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer anio) {
        String paramFactura = (factura != null && !factura.isBlank()) ? factura : "";
        String paramCodigo = (codigoDataease != null && !codigoDataease.isBlank()) ? codigoDataease : "";

        Page<FacturaOriginal> resultado = repository.buscarConFiltros(paramFactura, paramCodigo, mes, anio, pageable);
        return ResponseEntity.ok(resultado);
    }
}