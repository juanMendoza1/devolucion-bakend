package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.AsignacionGlobalDTO;
import com.nodos.devolucion.dto.AsignacionTarifaRequest;
import com.nodos.devolucion.entity.Aprovechador;
import com.nodos.devolucion.service.TarifaAsignacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tarifas")
@RequiredArgsConstructor
public class TarifaAsignacionController {

    private final TarifaAsignacionService asignacionService;

    // Endpoint 1: Para llenar la lista desplegable en tu Frontend
    @GetMapping("/aprovechadores/activos")
    public ResponseEntity<List<Aprovechador>> listarAprovechadoresActivos() {
        return ResponseEntity.ok(asignacionService.obtenerAprovechadoresActivos());
    }

    @PostMapping("/asignar-aprovechador")
    public ResponseEntity<?> asignarAprovechador(@RequestBody AsignacionTarifaRequest request) {
        int cantidad = asignacionService.asignarAprovechador(request);
        return ResponseEntity.ok(Map.of(
            "mensaje", "Asignación exitosa",
            "registrosActualizados", cantidad
        ));
    }
    
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.nodos.devolucion.entity.DevolTarifas>> obtenerTarifas(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        return ResponseEntity.ok(asignacionService.obtenerTarifas(page, size));
    }
    
    @GetMapping("/lotes")
    public ResponseEntity<List<com.nodos.devolucion.dto.LoteTarifasDTO>> obtenerResumenLotes() {
        return ResponseEntity.ok(asignacionService.obtenerResumenLotes());
    }
    
    @GetMapping("/lote/{jobId}")
    public ResponseEntity<org.springframework.data.domain.Page<com.nodos.devolucion.entity.DevolTarifas>> obtenerTarifasPorLote(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        return ResponseEntity.ok(asignacionService.obtenerTarifasPorLote(jobId, page, size));
    }
    
    @GetMapping("/auditoria")
    public ResponseEntity<List<com.nodos.devolucion.dto.AuditoriaDTO>> obtenerHistorialAuditoria() {
        return ResponseEntity.ok(asignacionService.obtenerHistorialAuditoria());
    }
    
    @GetMapping("/lote/{jobId}/{mes}/{anio}")
    public ResponseEntity<org.springframework.data.domain.Page<com.nodos.devolucion.entity.DevolTarifas>> obtenerTarifasPorLote(
            @PathVariable String jobId,
            @PathVariable Integer mes,
            @PathVariable Integer anio,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
            
        return ResponseEntity.ok(asignacionService.obtenerTarifasPorLote(jobId, mes, anio, page, size));
    }
    
    @PostMapping("/asignar-lote-completo")
    public ResponseEntity<?> asignarLoteCompleto(@RequestBody AsignacionGlobalDTO dto) {
        return ResponseEntity.ok(asignacionService.asignarLoteCompleto(dto));
    }
}