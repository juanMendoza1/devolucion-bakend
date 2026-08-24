package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.ParametrosConfigDTO;
import com.nodos.devolucion.dto.ReliquidacionRequestDTO;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.repository.ReliquidacionRepository;
import com.nodos.devolucion.service.ReliquidacionOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // <-- Importante agregar esto

@RestController
@RequestMapping("/api/reliquidaciones")
@RequiredArgsConstructor
public class ReliquidacionController {

    private final ReliquidacionOrchestratorService orquestadorService;
    private final ReliquidacionRepository repository; // Inyectado para inspección parcial

    @PostMapping("/calcular")
    public ResponseEntity<?> calcular(@RequestBody ReliquidacionRequestDTO request) {
        if (request.isEsDemo()) {
            return ResponseEntity.ok(orquestadorService.procesarReliquidacion(request));
        } else {
            orquestadorService.procesarReliquidacionAsync(request);
            return ResponseEntity.accepted().body(Map.of("mensaje", "Proceso encolado en segundo plano."));
        }
    }
    
    @GetMapping("/parametros")
    public ResponseEntity<ParametrosConfigDTO> getParametros() {
        ParametrosConfigDTO parametros = orquestadorService.obtenerParametrosConfiguracion();
        return ResponseEntity.ok(parametros);
    }

    // --- NUEVO ENDPOINT: Inspección al vuelo para el Frontend (Modo Producción) ---
    @GetMapping("/parcial/{consolidadoTarifas}")
    public ResponseEntity<List<Reliquidacion>> obtenerResultadosParciales(@PathVariable String consolidadoTarifas) {
        // Traemos una muestra rápida (ej: los últimos 50 registros guardados) 
        // para que la tabla auditoría muestre cómo va el cálculo en tiempo real
        List<Reliquidacion> parciales = repository.findTop50ByConsolidadoTarifasOrderByIdDesc(consolidadoTarifas);
        return ResponseEntity.ok(parciales);
    }
}