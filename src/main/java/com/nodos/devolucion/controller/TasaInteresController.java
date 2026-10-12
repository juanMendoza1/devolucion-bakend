package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.TasaInteresDTO;
import com.nodos.devolucion.entity.TasaInteres;
import com.nodos.devolucion.service.TasaInteresService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasas-interes")
@RequiredArgsConstructor
public class TasaInteresController {

    private final TasaInteresService service;

    @GetMapping
    public ResponseEntity<Page<TasaInteres>> listarTasas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        // Ordenamos por año desc y mes desc por defecto
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "ano", "mes"));
        return ResponseEntity.ok(service.obtenerTodas(pageRequest));
    }

    @PostMapping
    public ResponseEntity<TasaInteres> crear(@RequestBody TasaInteresDTO dto) {
        return ResponseEntity.ok(service.crear(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TasaInteres> actualizar(@PathVariable Long id, @RequestBody TasaInteresDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.ok(Map.of("mensaje", "Tasa eliminada correctamente"));
    }
}