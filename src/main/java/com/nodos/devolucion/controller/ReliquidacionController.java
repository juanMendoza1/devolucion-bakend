package com.nodos.devolucion.controller;

import com.nodos.devolucion.dto.ConsolidadoMesDTO;
import com.nodos.devolucion.dto.ParametrosConfigDTO;
import com.nodos.devolucion.dto.ReliquidacionRequestDTO;
import com.nodos.devolucion.entity.DevolucionMaestro;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.repository.ReliquidacionJdbcRepository;
import com.nodos.devolucion.repository.ReliquidacionRepository;
import com.nodos.devolucion.service.ReliquidacionOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reliquidaciones")
@RequiredArgsConstructor
public class ReliquidacionController {

    private final ReliquidacionOrchestratorService orquestadorService;
    private final ReliquidacionRepository repository; 
    private final ReliquidacionJdbcRepository reliquidacionJdbcRepository;

    @PostMapping("/calcular")
    public ResponseEntity<?> calcular(@RequestBody ReliquidacionRequestDTO request) {
        return ResponseEntity.ok(orquestadorService.procesarReliquidacion(request));
    }
    
    @GetMapping("/parametros")
    public ResponseEntity<ParametrosConfigDTO> getParametros() {
        ParametrosConfigDTO parametros = orquestadorService.obtenerParametrosConfiguracion();
        return ResponseEntity.ok(parametros);
    }

    @GetMapping("/parcial/{consolidadoTarifas}")
    public ResponseEntity<List<Reliquidacion>> obtenerResultadosParciales(@PathVariable String consolidadoTarifas) {

        List<Reliquidacion> parciales = repository.findTop50ByConsolidadoTarifasOrderByIdDesc(consolidadoTarifas);
        return ResponseEntity.ok(parciales);
    }
    
    @GetMapping("/consolidados")
    public ResponseEntity<List<DevolucionMaestro>> obtenerConsolidados() {
        List<DevolucionMaestro> consolidados = orquestadorService.obtenerConsolidados();
        return ResponseEntity.ok(consolidados);
    }
    
    /** Recalcula los totales de un maestro desde sus reliquidaciones (capital = devolucion del primer periodo). */
    @PostMapping("/consolidados/{id}/recalcular")
    public ResponseEntity<DevolucionMaestro> recalcularTotales(@PathVariable Long id) {
        return ResponseEntity.ok(orquestadorService.recalcularTotalesMaestro(id));
    }

    @GetMapping("/consolidados/{id}/detalle-mensual")
    public ResponseEntity<List<ConsolidadoMesDTO>> obtenerDetalleMensual(@PathVariable Long id) {
        List<ConsolidadoMesDTO> detalle = repository.obtenerDetalleMensualPorMaestro(id);
        return ResponseEntity.ok(detalle);
    }
    
    @GetMapping("/historial/{codigoDataease}")
    public ResponseEntity<List<com.nodos.devolucion.dto.HistorialCodigoDTO>> obtenerHistorialUsuario(@PathVariable String codigoDataease) {
        List<com.nodos.devolucion.dto.HistorialCodigoDTO> historial = repository.obtenerHistorialPorCodigo(codigoDataease);
        return ResponseEntity.ok(historial);
    }
    
    @GetMapping("/maestro/{id}/desglose")
    public ResponseEntity<List<Map<String, Object>>> obtenerDesgloseMensual(@PathVariable Long id) {
        return ResponseEntity.ok(reliquidacionJdbcRepository.desgloseMensual(id));
    }

}