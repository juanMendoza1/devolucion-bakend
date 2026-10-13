package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.AprovechadorDTO;
import com.nodos.devolucion.service.AprovechadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aprovechadores")
@RequiredArgsConstructor
public class AprovechadorController {

    private final AprovechadorService service;

    /** Todos (activos e inactivos). Con ?soloActivos=true devuelve solo los asignables. */
    @GetMapping
    public ResponseEntity<List<AprovechadorDTO>> listar(@RequestParam(defaultValue = "false") boolean soloActivos) {
        return ResponseEntity.ok(soloActivos ? service.listarActivos() : service.listar());
    }

    /** Estadísticas por aprovechador para el panel general. */
    @GetMapping("/estadisticas")
    public ResponseEntity<Map<String, Object>> estadisticas() {
        return ResponseEntity.ok(service.estadisticas());
    }

    @PostMapping
    public ResponseEntity<AprovechadorDTO> crear(@RequestBody AprovechadorDTO dto) {
        return ResponseEntity.ok(service.crear(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AprovechadorDTO> actualizar(@PathVariable Long id, @RequestBody AprovechadorDTO dto) {
        return ResponseEntity.ok(service.actualizar(id, dto));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<AprovechadorDTO> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean activo = body.get("activo");
        if (activo == null) {
            throw new IllegalArgumentException("Debe indicar el campo 'activo'.");
        }
        return ResponseEntity.ok(service.cambiarEstado(id, activo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.ok(Map.of("mensaje", "Aprovechador eliminado."));
    }
}
