package com.nodos.devolucion.strategy;

import com.nodos.devolucion.entity.DevolTarifas;
import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.entity.TasaInteres;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import com.nodos.devolucion.repository.ReliquidacionRepository;
import com.nodos.devolucion.repository.TasaInteresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReliquidacionTAStrategy implements ReliquidacionConceptoStrategy {

    private final FacturaOriginalRepository facturaRepository;
    private final DevolTarifasRepository tarifasRepository;
    private final TasaInteresRepository tasaInteresRepository;
    // INYECCIONES NUEVAS
    private final ReliquidacionRepository reliquidacionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int BATCH_SIZE = 1000;

    @Override
    public String obtenerConcepto() {
        return "TA";
    }

    @Override
    public List<Reliquidacion> calcularDevolucion(
            String consolidadoTarifas, 
            List<String> jobIdsTarifas, 
            String tipoInteres, 
            boolean esDemo, 
            List<Map<String, Integer>> periodosOrdenados) {

        log.info("Iniciando cálculo TA. Consolidado: {}, Lotes: {}, Modo Demo: {}", consolidadoTarifas, jobIdsTarifas.size(), esDemo);

        if (periodosOrdenados == null || periodosOrdenados.isEmpty()) {
            return List.of();
        }

        List<Reliquidacion> resultadosGlobales = new ArrayList<>();

        List<String> periodosConsulta = periodosOrdenados.stream()
                .map(p -> p.get("ano") + "-" + p.get("mes"))
                .collect(Collectors.toList());

        // 1. Cargar Tasas de Interés una sola vez para optimizar
        List<TasaInteres> tasasInteres = tasaInteresRepository.buscarPorPeriodos(periodosConsulta);
        Map<String, BigDecimal> mapaTasasPorPeriodo = tasasInteres.stream()
                .collect(Collectors.toMap(
                        t -> t.getAno() + "-" + t.getMes(), 
                        TasaInteres::getTsi,
                        (t1, t2) -> t1
                ));

        // 2. Obtener TODAS las facturas una sola vez
        List<FacturaOriginal> todasLasFacturas = facturaRepository.buscarPorPeriodos(periodosConsulta);
        Map<String, List<FacturaOriginal>> facturasPorUsuarioMap = todasLasFacturas.stream()
                .collect(Collectors.groupingBy(FacturaOriginal::getCodigoDataease));

        // 3. Iteramos sobre cada lote de tarifas seleccionado
        for (int indexLote = 0; indexLote < jobIdsTarifas.size(); indexLote++) {
            String currentJobId = jobIdsTarifas.get(indexLote);

            // Cargar tarifas de este lote específico
            List<DevolTarifas> tarifasDelLote = tarifasRepository.findByJobId(currentJobId);
            if (tarifasDelLote.isEmpty()) continue;

            // Identificar el Mes Base de este lote (usando el primer registro como referencia)
            Integer mesBaseLote = tarifasDelLote.get(0).getMes();
            Integer anoBaseLote = tarifasDelLote.get(0).getAnio();

            Map<String, DevolTarifas> mapaTarifasPorEstrato = tarifasDelLote.stream()
                    .collect(Collectors.toMap(DevolTarifas::getEstrato, t -> t, (t1, t2) -> t1));

            Stream<Map.Entry<String, List<FacturaOriginal>>> usuariosStream = facturasPorUsuarioMap.entrySet().stream();
            int totalUsuarios = facturasPorUsuarioMap.size();

            // 🚀 MODO DEMO: Limitamos a 10 usuarios para velocidad instantánea
            if (esDemo) {
                log.info("Modo Demo activado para lote {}: Muestreando solo los primeros 10 usuarios.", currentJobId);
                usuariosStream = usuariosStream.limit(10); 
            }

            List<Reliquidacion> loteParaGuardar = new ArrayList<>();
            int procesados = 0;

            for (Map.Entry<String, List<FacturaOriginal>> entry : usuariosStream.toList()) {
                List<FacturaOriginal> historialUsuario = entry.getValue();

                // Buscar la factura del Mes Base para este lote
                FacturaOriginal facturaBase = historialUsuario.stream()
                        .filter(f -> f.getMes().equals(mesBaseLote) && f.getAno().equals(anoBaseLote))
                        .findFirst()
                        .orElse(null);

                if (facturaBase != null) {
                    DevolTarifas tarifaAplicada = mapaTarifasPorEstrato.get(facturaBase.getEstrato());
                    
                    if (tarifaAplicada != null && tarifaAplicada.getDevolucionAplicar() != null) {
                        BigDecimal valorBase = tarifaAplicada.getDevolucionAplicar();
                        BigDecimal capitalCongelado = valorBase;

                        // Regla de Desocupación en mes base
                        boolean estabaDesocupado = Boolean.TRUE.equals(facturaBase.getDesocupa());
                        if (estabaDesocupado) {
                            capitalCongelado = BigDecimal.ZERO;
                        }

                        // Motor de Intereses
                        BigDecimal interesesAcumulados = BigDecimal.ZERO;
                        if (capitalCongelado.compareTo(BigDecimal.ZERO) > 0) {
                            historialUsuario.sort((f1, f2) -> {
                                if (!f1.getAno().equals(f2.getAno())) return f1.getAno().compareTo(f2.getAno());
                                return f1.getMes().compareTo(f2.getMes());
                            });

                            for (FacturaOriginal facturaMesSiguiente : historialUsuario) {
                                if (facturaMesSiguiente.getId().equals(facturaBase.getId())) continue;

                                if (Boolean.TRUE.equals(facturaMesSiguiente.getPagada())) {
                                    String llaveTasa = facturaMesSiguiente.getAno() + "-" + facturaMesSiguiente.getMes();
                                    BigDecimal tasaDelMes = mapaTasasPorPeriodo.getOrDefault(llaveTasa, BigDecimal.ZERO);
                                    
                                    interesesAcumulados = interesesAcumulados.add(
                                        calcularInteresFinanciero(capitalCongelado, tipoInteres, tasaDelMes)
                                    );
                                }
                            }
                        }

                        // Construcción de la entidad
                        Reliquidacion reliquidacion = Reliquidacion.builder()
                                .consolidadoTarifas(consolidadoTarifas) // Agrupador general
                                .jobId(currentJobId) // Lote específico
                                .facturaOriginalId(facturaBase.getId())
                                .aprovechador(tarifaAplicada.getAprovechador())
                                .tarifaAplicada(tarifaAplicada)
                                .concepto("TA")
                                .mesBase(mesBaseLote)
                                .anioBase(anoBaseLote)
                                .desocupadoMesBase(estabaDesocupado)
                                .tipoInteres(tipoInteres)
                                .valorBaseTarifa(valorBase)
                                .capitalReliquidado(capitalCongelado)
                                .interesesCalculados(interesesAcumulados)
                                .totalDevolucion(capitalCongelado.add(interesesAcumulados))
                                .esDemo(esDemo)
                                .fechaCalculo(LocalDateTime.now())
                                .build();

                        loteParaGuardar.add(reliquidacion);
                        if (esDemo) resultadosGlobales.add(reliquidacion);
                    }
                }

                procesados++;

                // 4. LÓGICA DE PRODUCCIÓN: Guardado por baches y emisión de WebSockets
                if (!esDemo) {
                    if (loteParaGuardar.size() >= BATCH_SIZE) {
                        reliquidacionRepository.saveAllAndFlush(loteParaGuardar);
                        loteParaGuardar.clear();
                    }

                    // Enviar progreso cada 100 usuarios procesados
                    if (procesados % 100 == 0 || procesados == totalUsuarios) {
                        int porcentaje = (int) (((double) procesados / totalUsuarios) * 100);
                        String msj = String.format("Procesando lote %d/%d (%s): %d de %d usuarios...", 
                                (indexLote + 1), jobIdsTarifas.size(), currentJobId, procesados, totalUsuarios);
                        
                        messagingTemplate.convertAndSend("/topic/reliquidacion-progress/" + consolidadoTarifas, 
                            Map.of("porcentaje", porcentaje, "mensaje", msj)
                        );
                    }
                }
            }

            // Guardar el remanente del lote actual si es producción
            if (!esDemo && !loteParaGuardar.isEmpty()) {
                reliquidacionRepository.saveAllAndFlush(loteParaGuardar);
            }
        }

        // 5. Mensaje final y retorno
        if (!esDemo) {
            messagingTemplate.convertAndSend("/topic/reliquidacion-progress/" + consolidadoTarifas, 
                Map.of("porcentaje", 100, "mensaje", "¡Consolidado completado exitosamente!")
            );
            return List.of(); // En PROD no retornamos todo en memoria, el frontend consume el endpoint
        }

        log.info("Cálculo DEMO finalizado. Se procesaron {} reliquidaciones TA en total.", resultadosGlobales.size());
        return resultadosGlobales;
    }

    private BigDecimal calcularInteresFinanciero(BigDecimal capital, String tipoInteres, BigDecimal tasaDelMes) {
        if ("NINGUNO".equalsIgnoreCase(tipoInteres) || tasaDelMes.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if ("SIMPLE".equalsIgnoreCase(tipoInteres) || "COMPUESTO".equalsIgnoreCase(tipoInteres)) {
            return capital.multiply(tasaDelMes);
        }
        
        return BigDecimal.ZERO;
    }
}