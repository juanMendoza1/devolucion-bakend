package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.ParametrosConfigDTO;
import com.nodos.devolucion.dto.ReliquidacionRequestDTO;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import com.nodos.devolucion.repository.ReliquidacionRepository;
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
    
    // 👇 Nuevas dependencias inyectadas automáticamente por @RequiredArgsConstructor
    private final FacturaOriginalRepository facturaOriginalRepository;
    private final DevolTarifasRepository devolTarifasRepository;

    @Transactional // Garantiza que si falla la base de datos, no se guarde nada a medias
    public List<Reliquidacion> procesarReliquidacion(ReliquidacionRequestDTO request) {
        
        log.info("Iniciando orquestación para JobId: {}, Concepto: {}, Demo: {}", 
                request.getJobId(), request.getConcepto(), request.isEsDemo());

        // 1. El Factory descubre mágicamente qué clase debe usar ("TA", etc.)
        ReliquidacionConceptoStrategy estrategia = motorFactory.obtenerEstrategia(request.getConcepto());

        // 2. Ejecutamos toda la lógica pesada en RAM
        List<Reliquidacion> resultados = estrategia.calcularDevolucion(
                request.getJobId(),
                request.getTipoInteres(),
                request.isEsDemo(),
                request.getPeriodosOrdenados()
        );

        // 3. Decisión de Producción vs Demo
        if (!request.isEsDemo()) {
            log.info("Modo Producción detectado. Limpiando cálculos anteriores y guardando en BD...");
            
            // Limpiamos por si están reprocesando el mismo lote
            reliquidacionRepository.deleteByJobIdAndConcepto(request.getJobId(), request.getConcepto());
            
            // Guardado masivo (Batch Insert)
            reliquidacionRepository.saveAll(resultados);
            log.info("Se guardaron {} registros en firme en la base de datos.", resultados.size());
        } else {
            log.info("Modo Demo. Se calculó pero NO se guardó en BD.");
        }

        return resultados;
    }

    // 👇 NUEVO MÉTODO: Alimenta los menús desplegables del frontend
    public ParametrosConfigDTO obtenerParametrosConfiguracion() {
        ParametrosConfigDTO config = new ParametrosConfigDTO();

        // 1. Obtener Lotes dinámicos y mapearlos
        List<String> jobIds = facturaOriginalRepository.findDistinctJobIds();
        List<ParametrosConfigDTO.LoteInfo> lotesInfo = jobIds.stream()
                .map(jobId -> new ParametrosConfigDTO.LoteInfo(jobId, "Lote de Facturación"))
                .toList();
        config.setLotes(lotesInfo);

        // 2. Obtener Conceptos dinámicos desde la tabla de tarifas
        List<String> conceptos = devolTarifasRepository.findDistinctConceptos();
        // Si la tabla está vacía, enviamos unos por defecto por seguridad
        config.setConceptos(conceptos.isEmpty() ? List.of("TA", "TRT") : conceptos); 

        // 3. Tipos de Interés
        config.setTiposInteres(List.of("NINGUNO", "SIMPLE", "COMPUESTO"));

        return config;
    }
}