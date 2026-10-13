package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.LoteTarifasDTO;
import com.nodos.devolucion.dto.ParametrosConfigDTO;
import com.nodos.devolucion.dto.ReliquidacionRequestDTO;
import com.nodos.devolucion.dto.ReliquidacionResultadoDTO;
import com.nodos.devolucion.entity.DevolucionMaestro;
import com.nodos.devolucion.entity.DevolucionMaestroTarifa;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.DevolucionMaestroRepository;
import com.nodos.devolucion.repository.DevolucionMaestroTarifaRepository;
import com.nodos.devolucion.repository.ReliquidacionJdbcRepository;
import com.nodos.devolucion.repository.VersionTarifaRepository;
import com.nodos.devolucion.strategy.MotorReliquidacionFactory;
import com.nodos.devolucion.strategy.ReliquidacionConceptoStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReliquidacionOrchestratorService {

    private final MotorReliquidacionFactory motorFactory;
    private final DevolucionMaestroRepository maestroRepository;
    private final DevolucionMaestroTarifaRepository maestroTarifaRepository; 
    private final ReliquidacionJdbcRepository reliquidacionJdbcRepository;
    private final VersionTarifaRepository versionTarifaRepository;
    private final DevolTarifasRepository devolTarifasRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DataSource dataSource;

    /** Máximo de filas que se devuelven al frontend por tarifa (la tabla completa puede tener millones). */
    private static final int LIMITE_FILAS_UI = 500;

    /** Mensaje de avance sin porcentaje (el frontend solo actualiza el texto). */
    private static final int SIN_PORCENTAJE = -1;

    /**
     * Procesa las tarifas solicitadas. Devuelve { resultados, resumen }.
     * - Simulador (esDemo): todo corre en una transacción que termina en ROLLBACK; no queda nada guardado
     *   (ni el maestro, ni la bitácora, ni las reliquidaciones).
     * - Producción: persiste, lleva bitácora por tarifa y recalcula los totales del maestro al terminar cada tarifa.
     */
    public Map<String, Object> procesarReliquidacion(ReliquidacionRequestDTO request) {
        if (request.getNombreDevolucion() == null || request.getNombreDevolucion().isBlank()) {
            throw new IllegalArgumentException("Debe indicar el nombre de la devolución.");
        }
        if (request.getTarifasConfig() == null || request.getTarifasConfig().isEmpty()) {
            throw new IllegalArgumentException("Debe configurar al menos una tarifa.");
        }

        log.info("Iniciando orquestación. Devolución: {}, Tarifas configuradas: {}, Demo: {}",
                request.getNombreDevolucion(), request.getTarifasConfig().size(), request.isEsDemo());

        List<ReliquidacionRequestDTO.TarifaConfigDTO> configuraciones = request.getTarifasConfig().stream()
                .filter(config -> request.isEsDemo() ||
                        (request.getTarifasAProcesar() != null && request.getTarifasAProcesar().contains(config.getJobId())))
                .toList();

        for (ReliquidacionRequestDTO.TarifaConfigDTO config : configuraciones) {
            if (config.getPeriodosOrdenados() == null || config.getPeriodosOrdenados().isEmpty()) {
                throw new IllegalArgumentException("La tarifa '" + config.getNombreVisible() + "' no tiene periodos configurados.");
            }
        }

        return request.isEsDemo()
                ? procesarSimulacion(request, configuraciones)
                : procesarProduccion(request, configuraciones);
    }

    // =========================================================
    // SIMULADOR: transacción con rollback
    // =========================================================

    private Map<String, Object> procesarSimulacion(ReliquidacionRequestDTO request,
                                                   List<ReliquidacionRequestDTO.TarifaConfigDTO> configuraciones) {
        if (request.getCodigosPrueba() == null || request.getCodigosPrueba().isEmpty()) {
            throw new IllegalArgumentException("En el simulador debe ingresar al menos un código Dataease.");
        }

        String usuarioActual = obtenerUsuarioActual();
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        List<ReliquidacionResultadoDTO> resultadosGlobales = new ArrayList<>();
        Map<String, Object> resumenFinal = Map.of();
        int total = configuraciones.size();

        // Una transacción por tarifa: el procedimiento crea una tabla temporal que vive hasta el fin de la transacción
        for (int i = 0; i < total; i++) {
            ReliquidacionRequestDTO.TarifaConfigDTO config = configuraciones.get(i);
            String nombreTarifaUi = "Tarifa " + (i + 1) + " de " + total + " (" + config.getNombreVisible() + ")";
            notificar(request, SIN_PORCENTAJE, "Simulando " + nombreTarifaUi + "...");

            Map<String, Object> salida = tx.execute(status -> {
                // Todo lo que pase aquí se deshace al final (setRollbackOnly)
                status.setRollbackOnly();

                Long maestroId = reliquidacionJdbcRepository.obtenerOCrearMaestroId(request.getNombreDevolucion(), usuarioActual);
                DevolucionMaestro maestroSimulado = DevolucionMaestro.builder()
                        .id(maestroId)
                        .nombreDevolucion(request.getNombreDevolucion())
                        .build();

                ReliquidacionConceptoStrategy estrategia = motorFactory.obtenerEstrategia(config.getConcepto());
                estrategia.calcularDevolucion(request.getNombreDevolucion(), config, true, request.getCodigosPrueba(), maestroSimulado);

                Map<String, Object> resumen = new LinkedHashMap<>(reliquidacionJdbcRepository.resumenTarifa(maestroId, config.getJobId()));
                resumen.put("nombreTarifa", config.getNombreVisible());

                return Map.of(
                        "resultados", reliquidacionJdbcRepository.listarResultados(maestroId, config.getJobId(), LIMITE_FILAS_UI),
                        "resumen", resumen);
            });

            @SuppressWarnings("unchecked")
            List<ReliquidacionResultadoDTO> filas = (List<ReliquidacionResultadoDTO>) salida.get("resultados");
            resultadosGlobales.addAll(filas);
            @SuppressWarnings("unchecked")
            Map<String, Object> resumen = (Map<String, Object>) salida.get("resumen");
            resumenFinal = resumen;

            notificar(request, 100, nombreTarifaUi + " simulada (sin guardar).");
        }

        return Map.of("resultados", resultadosGlobales, "resumen", resumenFinal);
    }

    // =========================================================
    // PRODUCCIÓN: persistente, con bitácora y totales actualizados
    // =========================================================

    private Map<String, Object> procesarProduccion(ReliquidacionRequestDTO request,
                                                   List<ReliquidacionRequestDTO.TarifaConfigDTO> configuraciones) {
        // Una tarifa solo se liquida una vez: si ya está COMPLETADA en cualquier devolución, no se reprocesa.
        for (ReliquidacionRequestDTO.TarifaConfigDTO config : configuraciones) {
            reliquidacionJdbcRepository.devolucionQueLiquidoTarifa(config.getJobId()).ifPresent(nombre -> {
                throw new IllegalArgumentException("La tarifa '" + config.getNombreVisible()
                        + "' ya fue liquidada en la devolución '" + nombre + "'. No se puede procesar nuevamente.");
            });
        }

        DevolucionMaestro maestro = obtenerOCrearMaestro(request.getNombreDevolucion());

        List<ReliquidacionResultadoDTO> resultadosGlobales = new ArrayList<>();
        Map<String, Object> resumenFinal = Map.of();
        int total = configuraciones.size();

        for (int i = 0; i < total; i++) {
            ReliquidacionRequestDTO.TarifaConfigDTO config = configuraciones.get(i);
            String nombreTarifaUi = "Tarifa " + (i + 1) + " de " + total + " (" + config.getNombreVisible() + ")";

            DevolucionMaestroTarifa bitacora = maestroTarifaRepository
                    .findByDevolucionMaestroIdAndJobId(maestro.getId(), config.getJobId())
                    .orElseGet(() -> maestroTarifaRepository.save(DevolucionMaestroTarifa.builder()
                            .devolucionMaestro(maestro)
                            .jobId(config.getJobId())
                            .nombreTarifa(config.getNombreVisible())
                            .estado("PENDIENTE")
                            .registrosProcesados(0)
                            .build()));

            Map<String, Object> resumenTarifa;
            try {
                bitacora.setEstado("PROCESANDO");
                bitacora.setMensajeError(null);
                maestroTarifaRepository.save(bitacora);

                // Mensajes sin porcentaje: el cálculo corre en una sola sentencia y no reporta avance intermedio
                notificar(request, SIN_PORCENTAJE, "Calculando " + nombreTarifaUi + "...");

                // El procedimiento borra las filas previas de (maestro, job) antes de insertar
                ReliquidacionConceptoStrategy estrategia = motorFactory.obtenerEstrategia(config.getConcepto());
                estrategia.calcularDevolucion(request.getNombreDevolucion(), config, false, List.of(), maestro);

                // Estadísticas al día antes de resumir: evita planes de horas tras cargas masivas
                notificar(request, SIN_PORCENTAJE, "Consolidando resultados de " + nombreTarifaUi + "...");
                reliquidacionJdbcRepository.actualizarEstadisticas();

                resumenTarifa = reliquidacionJdbcRepository.resumenTarifa(maestro.getId(), config.getJobId());
                bitacora.setEstado("COMPLETADO");
                bitacora.setRegistrosProcesados(((Number) resumenTarifa.get("usuarios")).intValue());
                maestroTarifaRepository.save(bitacora);

                recalcularTotalesMaestro(maestro);

                notificar(request, 100, nombreTarifaUi + " procesada.");
            } catch (Exception e) {
                log.error("Error procesando {}: {}", nombreTarifaUi, e.getMessage());
                bitacora.setEstado("ERROR");
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Error interno desconocido";
                bitacora.setMensajeError(errorMsg.substring(0, Math.min(errorMsg.length(), 500)));
                maestroTarifaRepository.save(bitacora);
                throw new IllegalStateException("Fallo en " + nombreTarifaUi + ": " + errorMsg, e);
            }

            Map<String, Object> resumen = new LinkedHashMap<>(resumenTarifa);
            resumen.put("nombreTarifa", config.getNombreVisible());
            resumenFinal = resumen;
            // En producción solo se devuelve el resumen; el detalle se consulta en Consolidados / Visor
        }

        return Map.of("resultados", resultadosGlobales, "resumen", resumenFinal);
    }

    private DevolucionMaestro obtenerOCrearMaestro(String nombreDevolucion) {
        DevolucionMaestro maestro = maestroRepository.findByNombreDevolucion(nombreDevolucion).orElse(null);
        if (maestro == null) {
            log.info("Creando nuevo registro Maestro para '{}'", nombreDevolucion);
            maestro = DevolucionMaestro.builder()
                    .nombreDevolucion(nombreDevolucion)
                    .fechaCalculo(LocalDateTime.now())
                    .usuario(obtenerUsuarioActual())
                    .esDemo(false)
                    .cantidadTarifas(0)
                    .cantidadRegistros(0)
                    .totalDevolucion(BigDecimal.ZERO)
                    .totalCapital(BigDecimal.ZERO)
                    .totalIntereses(BigDecimal.ZERO)
                    .build();
        } else {
            log.info("Retomando ejecución del Maestro '{}'", nombreDevolucion);
            maestro.setEsDemo(false);
            maestro.setFechaCalculo(LocalDateTime.now());
        }
        return maestroRepository.save(maestro);
    }

    /**
     * Recalcula los totales del maestro desde las reliquidaciones guardadas.
     * Capital = devolucion_aplicar del primer periodo de cada tarifa (0 si estaba desocupado); total = capital + intereses.
     */
    public void recalcularTotalesMaestro(DevolucionMaestro maestro) {
        Object[] totales = reliquidacionJdbcRepository.totalesMaestro(maestro.getId());
        BigDecimal capital = (BigDecimal) totales[1];
        BigDecimal intereses = (BigDecimal) totales[2];

        maestro.setCantidadRegistros(((Number) totales[0]).intValue());
        maestro.setTotalCapital(capital);
        maestro.setTotalIntereses(intereses);
        maestro.setTotalDevolucion(capital.add(intereses));
        maestro.setCantidadTarifas((int) maestroTarifaRepository.findByDevolucionMaestroId(maestro.getId()).stream()
                .filter(t -> "COMPLETADO".equals(t.getEstado()))
                .count());
        maestroRepository.save(maestro);
    }

    public DevolucionMaestro recalcularTotalesMaestro(Long maestroId) {
        DevolucionMaestro maestro = maestroRepository.findById(maestroId)
                .orElseThrow(() -> new IllegalArgumentException("La devolución " + maestroId + " no existe."));
        recalcularTotalesMaestro(maestro);
        return maestro;
    }

    private void notificar(ReliquidacionRequestDTO request, int porcentaje, String mensaje) {
        messagingTemplate.convertAndSend("/topic/reliquidacion-progress/" + request.getNombreDevolucion(),
                Map.of("porcentaje", porcentaje, "mensaje", mensaje));
    }

    public ParametrosConfigDTO obtenerParametrosConfiguracion() {
        ParametrosConfigDTO config = new ParametrosConfigDTO();

        List<String> jobsCompletados = maestroTarifaRepository.findAll().stream()
                .filter(t -> "COMPLETADO".equals(t.getEstado()))
                .map(t -> t.getJobId())
                .toList();

        List<com.nodos.devolucion.entity.VersionTarifa> versiones = versionTarifaRepository.findAll().stream()
                .filter(v -> !jobsCompletados.contains(v.getJobId()))
                .toList();
                
        List<ParametrosConfigDTO.LoteInfo> lotesInfo = versiones.stream()
                .map(v -> new ParametrosConfigDTO.LoteInfo(v.getJobId(), v.getNovedad() + " (" + v.getVersion() + ")"))
                .toList();

        if (lotesInfo.isEmpty()) {
            List<LoteTarifasDTO> resumenLotes = devolTarifasRepository.obtenerResumenLotes();
            lotesInfo = resumenLotes.stream()
                    .map(l -> new ParametrosConfigDTO.LoteInfo(l.getJobId(), (l.getNovedad() != null ? l.getNovedad() : "Tarifa") + " - " + l.getMes() + "/" + l.getAnio()))
                    .toList();
        }

        config.setLotes(lotesInfo);
        List<String> conceptos = devolTarifasRepository.findDistinctConceptos();
        config.setTiposInteres(List.of("NINGUNO", "SIMPLE", "COMPUESTO"));
        config.setConceptos(conceptos.isEmpty() ? List.of("TRT", "TA") : conceptos);

        return config;
    }

    private String obtenerUsuarioActual() {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
        return "Sistema";
    }
    
    /** Solo lectura: los totales se actualizan al terminar cada tarifa en producción (recalcularTotalesMaestro). */
    public List<DevolucionMaestro> obtenerConsolidados() {
        return maestroRepository.findAllByOrderByFechaCalculoDesc();
    }
}