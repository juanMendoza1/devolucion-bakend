package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.LoteTarifasDTO;
import com.nodos.devolucion.dto.ParametrosConfigDTO;
import com.nodos.devolucion.dto.ReliquidacionRequestDTO;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import com.nodos.devolucion.repository.ReliquidacionRepository;
import com.nodos.devolucion.repository.VersionTarifaRepository;
import com.nodos.devolucion.strategy.MotorReliquidacionFactory;
import com.nodos.devolucion.strategy.ReliquidacionConceptoStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReliquidacionOrchestratorService {

    private final MotorReliquidacionFactory motorFactory;
    private final ReliquidacionRepository reliquidacionRepository;
    private final VersionTarifaRepository versionTarifaRepository;
    private final FacturaOriginalRepository facturaOriginalRepository;
    private final DevolTarifasRepository devolTarifasRepository;

    @Transactional // Garantiza que si hay un fallo crítico, se haga rollback completo
    public List<Reliquidacion> procesarReliquidacion(ReliquidacionRequestDTO request) {
        
        log.info("Iniciando orquestación. Consolidado: {}, Lotes Seleccionados: {}, Concepto: {}, Demo: {}", 
                request.getConsolidadoTarifas(), request.getJobIds().size(), request.getConcepto(), request.isEsDemo());

        // 1. El Factory descubre mágicamente qué clase debe usar ("TA", etc.)
        ReliquidacionConceptoStrategy estrategia = motorFactory.obtenerEstrategia(request.getConcepto());

        // 2. Limpieza previa EN PRODUCCIÓN
        // Si el usuario reprocesa la misma campaña, borramos lo viejo antes de recalcular
        if (!request.isEsDemo()) {
            log.info("Modo Producción: Limpiando cálculos anteriores para el consolidado '{}'...", request.getConsolidadoTarifas());
            reliquidacionRepository.deleteByConsolidadoTarifasAndConcepto(request.getConsolidadoTarifas(), request.getConcepto());
        }

        // 3. Ejecutamos la lógica del motor
        // OJO: Si es Producción, la estrategia misma irá guardando por lotes y retornará una lista vacía.
        // Si es Demo, la estrategia calculará todo en RAM, NO guardará en BD y retornará la lista para previsualizar.
        List<Reliquidacion> resultados = estrategia.calcularDevolucion(
                request.getConsolidadoTarifas(),
                request.getJobIds(),
                request.getTipoInteres(),
                request.isEsDemo(),
                request.getPeriodosOrdenados()
        );

        // 4. Registro final de auditoría en consola
        if (request.isEsDemo()) {
            log.info("Modo Demo completado. Se retornan {} registros calculados en RAM.", resultados.size());
        } else {
            log.info("Modo Producción completado. El motor finalizó el guardado por lotes exitosamente.");
        }

        return resultados;
    }

    public ParametrosConfigDTO obtenerParametrosConfiguracion() {
        ParametrosConfigDTO config = new ParametrosConfigDTO();

        // 1. Obtener los lotes reales directamente desde la tabla versiones_tarifas
        List<com.nodos.devolucion.entity.VersionTarifa> versiones = versionTarifaRepository.findAll();
        
        List<ParametrosConfigDTO.LoteInfo> lotesInfo = versiones.stream()
                .map(v -> new ParametrosConfigDTO.LoteInfo(
                        v.getJobId(), 
                        v.getNovedad() + " (" + v.getVersion() + ")"
                ))
                .toList();

        // Si por alguna razón versiones_tarifas estuviera vacía, hacemos un fallback a devol_tarifas
        if (lotesInfo.isEmpty()) {
            List<LoteTarifasDTO> resumenLotes = devolTarifasRepository.obtenerResumenLotes();
            lotesInfo = resumenLotes.stream()
                    .map(l -> new ParametrosConfigDTO.LoteInfo(
                            l.getJobId(), 
                            (l.getNovedad() != null ? l.getNovedad() : "Lote") + " - " + l.getMes() + "/" + l.getAnio()
                    ))
                    .toList();
        }

        config.setLotes(lotesInfo);

        // 2. Obtener Conceptos dinámicos desde la tabla de tarifas
        List<String> conceptos = devolTarifasRepository.findDistinctConceptos();
        config.setConceptos(conceptos.isEmpty() ? List.of("TRT", "TA") : conceptos);

        // 3. Tipos de Interés
        config.setTiposInteres(List.of("NINGUNO", "SIMPLE", "COMPUESTO"));

        return config;
    }
}