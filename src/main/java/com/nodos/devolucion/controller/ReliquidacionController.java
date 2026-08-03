package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.ReliquidacionRequestDTO;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.service.ReliquidacionOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reliquidaciones")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Nivel Pro: Permite que el frontend se conecte sin bloqueos de CORS
public class ReliquidacionController {

    private final ReliquidacionOrchestratorService orquestadorService;

    @PostMapping("/calcular")
    public ResponseEntity<List<Reliquidacion>> calcular(@RequestBody ReliquidacionRequestDTO request) {
        
        try {
            List<Reliquidacion> calculoFinanciero = orquestadorService.procesarReliquidacion(request);
            return ResponseEntity.ok(calculoFinanciero);
            
        } catch (IllegalArgumentException e) {
            // Si mandan un concepto que no existe, devolvemos error 400 (Bad Request)
            return ResponseEntity.badRequest().build();
            
        } catch (Exception e) {
            // Si algo explota (ej: base de datos caída), devolvemos error 500
            return ResponseEntity.internalServerError().build();
        }
    }
}