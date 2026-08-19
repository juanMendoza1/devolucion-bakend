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

        if (periodosOrdenados == null || periodosOrdenados.isEmpty()) {
            return List.of();
        }

        List<Reliquidacion> resultadosGlobales = new ArrayList<>();

        List<String> periodosConsulta = periodosOrdenados.stream()
                .map(p -> p.get("ano") + "-" + p.get("mes"))
                .collect(Collectors.toList());

        List<TasaInteres> tasasInteres = tasaInteresRepository.buscarPorPeriodos(periodosConsulta);
        Map<String, BigDecimal> mapaTasasPorPeriodo = tasasInteres.stream()
                .collect(Collectors.toMap(
                        t -> t.getAno() + "-" + t.getMes(), 
                        TasaInteres::getTsi, 
                        (t1, t2) -> t1
                ));

        List<FacturaOriginal> todasLasFacturas = facturaRepository.buscarPorPeriodos(periodosConsulta);
        Map<String, List<FacturaOriginal>> facturasPorUsuarioMap = todasLasFacturas.stream()
                .collect(Collectors.groupingBy(FacturaOriginal::getCodigoDataease));

        for (int indexLote = 0; indexLote < jobIdsTarifas.size(); indexLote++) {
            String currentJobId = jobIdsTarifas.get(indexLote);

            List<DevolTarifas> tarifasDelLote = tarifasRepository.findByJobId(currentJobId);
            if (tarifasDelLote.isEmpty()) continue;

            Integer mesBaseLote = periodosOrdenados.get(0).get("mes");
            Integer anoBaseLote = periodosOrdenados.get(0).get("ano");

            Map<String, DevolTarifas> mapaTarifasPorEstrato = tarifasDelLote.stream()
                    .collect(Collectors.toMap(DevolTarifas::getEstrato, t -> t, (t1, t2) -> t1));

            Stream<Map.Entry<String, List<FacturaOriginal>>> usuariosStream = facturasPorUsuarioMap.entrySet().stream();
            int totalUsuarios = facturasPorUsuarioMap.size();

            if (esDemo) {
                String codigoPrueba = "060570905201";
                usuariosStream = usuariosStream
                        .filter(entry -> codigoPrueba.equals(entry.getKey()))
                        .limit(10);
            }

            List<Reliquidacion> loteParaGuardar = new ArrayList<>();
            int procesados = 0;

            for (Map.Entry<String, List<FacturaOriginal>> entry : usuariosStream.toList()) {
                List<FacturaOriginal> historialUsuario = entry.getValue();

                FacturaOriginal facturaBase = historialUsuario.stream()
                        .filter(f -> f.getMes().equals(mesBaseLote) && f.getAno().equals(anoBaseLote))
                        .findFirst()
                        .orElse(null);

                if (facturaBase != null) {
                    DevolTarifas tarifaAplicada = mapaTarifasPorEstrato.get(facturaBase.getEstrato());
                    
                    if (tarifaAplicada != null && tarifaAplicada.getDevolucionAplicar() != null) {
                        
                        BigDecimal capitalCongelado = tarifaAplicada.getDevolucionAplicar();
                        boolean estabaDesocupado = Boolean.TRUE.equals(facturaBase.getDesocupa());
                        if (estabaDesocupado) {
                            capitalCongelado = BigDecimal.ZERO;
                        }

                        BigDecimal capitalParaInteres = capitalCongelado; 
                        boolean empezoAPagar = false; 

                        for (Map<String, Integer> periodo : periodosOrdenados) {
                            Integer mesActual = periodo.get("mes");
                            Integer anoActual = periodo.get("ano");
                            
                            FacturaOriginal facturaDelMes = historialUsuario.stream()
                                    .filter(f -> f.getMes().equals(mesActual) && f.getAno().equals(anoActual))
                                    .findFirst()
                                    .orElse(null);

                            if (facturaDelMes != null && Boolean.TRUE.equals(facturaDelMes.getPagada())) {
                                empezoAPagar = true;
                            }

                            // Usamos la factura del mes actual, si no existe usamos la base como fallback
                            FacturaOriginal facturaUsada = (facturaDelMes != null) ? facturaDelMes : facturaBase;

                            String llaveTasa = anoActual + "-" + mesActual;
                            BigDecimal tasaDelMes = mapaTasasPorPeriodo.getOrDefault(llaveTasa, BigDecimal.ZERO);
                            
                            BigDecimal interesDelMes = BigDecimal.ZERO;
                            String estadoTasa = ""; // Por defecto vacío si no aplica
                            BigDecimal capitalInicioMes = capitalParaInteres;

                            // Si cumple las condiciones para aplicar interés
                            if (capitalCongelado.compareTo(BigDecimal.ZERO) != 0 && 
                                !"NINGUNO".equalsIgnoreCase(tipoInteres) && 
                                empezoAPagar && 
                                tasaDelMes.compareTo(BigDecimal.ZERO) != 0) {
                                
                                estadoTasa = tasaDelMes.toString(); // Solo enviamos el número de la tasa
                                
                                if ("SIMPLE".equalsIgnoreCase(tipoInteres)) {
                                    interesDelMes = capitalCongelado.multiply(tasaDelMes);
                                } else if ("COMPUESTO".equalsIgnoreCase(tipoInteres)) {
                                    interesDelMes = capitalParaInteres.multiply(tasaDelMes);
                                }
                                capitalParaInteres = capitalParaInteres.add(interesDelMes); 
                            }

                            Reliquidacion reliquidacion = Reliquidacion.builder()
                                    .consolidadoTarifas(consolidadoTarifas)
                                    .jobId(currentJobId)
                                    .facturaOriginalId(facturaUsada.getId())
                                    .factura(facturaUsada.getFactura()) // <-- Factura Real
                                    .codigoDataease(facturaUsada.getCodigoDataease()) // <-- Dataease Real
                                    .aprovechador(tarifaAplicada.getAprovechador())
                                    .tarifaAplicada(tarifaAplicada)
                                    .concepto("TA")
                                    .mesBase(mesActual) 
                                    .anioBase(anoActual) 
                                    .desocupadoMesBase(estabaDesocupado)
                                    .tipoInteres(tipoInteres)
                                    .valorBaseTarifa(capitalCongelado)    
                                    .capitalReliquidado(capitalInicioMes) 
                                    .interesesCalculados(interesDelMes)   
                                    .totalDevolucion(capitalParaInteres)  
                                    .esDemo(esDemo)
                                    .fechaCalculo(LocalDateTime.now())
                                    .detalleTasas(estadoTasa) // Estará vacío o tendrá solo la tasa
                                    .build();

                            loteParaGuardar.add(reliquidacion);
                            if (esDemo) resultadosGlobales.add(reliquidacion);
                        }
                    }
                }

                procesados++;

                if (!esDemo) {
                    if (loteParaGuardar.size() >= BATCH_SIZE) {
                        reliquidacionRepository.saveAllAndFlush(loteParaGuardar);
                        loteParaGuardar.clear();
                    }

                    if (procesados % 100 == 0 || procesados == totalUsuarios) {
                        int porcentaje = (int) (((double) procesados / totalUsuarios) * 100);
                        String msj = String.format("Procesando lote %d/%d: %d de %d usuarios...", 
                                (indexLote + 1), jobIdsTarifas.size(), procesados, totalUsuarios);
                        messagingTemplate.convertAndSend("/topic/reliquidacion-progress/" + consolidadoTarifas, 
                            Map.of("porcentaje", porcentaje, "mensaje", msj)
                        );
                    }
                }
            }

            if (!esDemo && !loteParaGuardar.isEmpty()) {
                reliquidacionRepository.saveAllAndFlush(loteParaGuardar);
            }
        }

        if (!esDemo) {
            messagingTemplate.convertAndSend("/topic/reliquidacion-progress/" + consolidadoTarifas, 
                Map.of("porcentaje", 100, "mensaje", "¡Consolidado completado exitosamente!")
            );
            return List.of();
        }

        return resultadosGlobales;
    }
}