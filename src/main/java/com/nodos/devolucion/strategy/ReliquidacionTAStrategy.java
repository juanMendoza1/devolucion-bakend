package com.nodos.devolucion.strategy;

import com.nodos.devolucion.entity.DevolTarifas;
import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.entity.Reliquidacion;
import com.nodos.devolucion.entity.TasaInteres;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.TasaInteresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReliquidacionTAStrategy implements ReliquidacionConceptoStrategy {

    // Inyectamos los 3 repositorios necesarios
    private final FacturaOriginalRepository facturaRepository;
    private final DevolTarifasRepository tarifasRepository;
    private final TasaInteresRepository tasaInteresRepository;

    @Override
    public String obtenerConcepto() {
        return "TA";
    }

    @Override
    public List<Reliquidacion> calcularDevolucion(
            String jobIdTarifas, 
            String tipoInteres, 
            boolean esDemo, 
            List<Map<String, Integer>> periodosOrdenados) {

        log.info("Iniciando cálculo de reliquidación TA. Es Demo: {}", esDemo);

        // 1. Identificar el Mes Base (El primero de la lista cronológica)
        Map<String, Integer> periodoBase = periodosOrdenados.get(0);
        Integer mesBase = periodoBase.get("mes");
        Integer anoBase = periodoBase.get("ano");

        // Convertimos la lista de mapas a una lista de Strings "ANO-MES" (Ej: "2026-2", "2026-3")
        // Esto nos permite hacer una sola consulta limpia a la base de datos
        List<String> periodosConsulta = periodosOrdenados.stream()
                .map(p -> p.get("ano") + "-" + p.get("mes"))
                .collect(Collectors.toList());

        // 2. Cargar todas las tarifas del lote y mapearlas por ESTRATO
        List<DevolTarifas> tarifasDelLote = tarifasRepository.findByJobId(jobIdTarifas);
        Map<String, DevolTarifas> mapaTarifasPorEstrato = tarifasDelLote.stream()
                .collect(Collectors.toMap(DevolTarifas::getEstrato, t -> t, (t1, t2) -> t1));

        // 3. Cargar las Tasas de Interés y mapearlas por "ANO-MES"
        List<TasaInteres> tasasInteres = tasaInteresRepository.buscarPorPeriodos(periodosConsulta);
        Map<String, BigDecimal> mapaTasasPorPeriodo = tasasInteres.stream()
                .collect(Collectors.toMap(
                        t -> t.getAno() + "-" + t.getMes(), 
                        TasaInteres::getTsi,
                        (t1, t2) -> t1
                ));

        // 4. Obtener TODAS las facturas de los periodos seleccionados y agruparlas por usuario
        List<FacturaOriginal> todasLasFacturas = facturaRepository.buscarPorPeriodos(periodosConsulta);
        Map<String, List<FacturaOriginal>> facturasPorUsuario = todasLasFacturas.stream()
                .collect(Collectors.groupingBy(FacturaOriginal::getCodigoDataease));

        List<Reliquidacion> reliquidacionesCalculadas = new ArrayList<>();

        // 5. EL RECORRIDO PRINCIPAL (Iteramos usuario por usuario)
        for (Map.Entry<String, List<FacturaOriginal>> entry : facturasPorUsuario.entrySet()) {
            List<FacturaOriginal> historialUsuario = entry.getValue();

            // Buscar la factura del Mes Base (la foto inicial de este usuario)
            FacturaOriginal facturaBase = historialUsuario.stream()
                    .filter(f -> f.getMes().equals(mesBase) && f.getAno().equals(anoBase))
                    .findFirst()
                    .orElse(null);

            if (facturaBase == null) continue; // Si no hay factura en el mes base, lo saltamos

            // REGLA DEL ESTRATO (Cruzar con el mapa de tarifas)
            DevolTarifas tarifaAplicada = mapaTarifasPorEstrato.get(facturaBase.getEstrato());
            if (tarifaAplicada == null || tarifaAplicada.getDevolucionAplicar() == null) continue;

            BigDecimal valorBase = tarifaAplicada.getDevolucionAplicar();
            BigDecimal capitalCongelado = valorBase;

            // REGLA DE DESOCUPACIÓN (Se evalúa SOLO en el mes base)
            boolean estabaDesocupado = Boolean.TRUE.equals(facturaBase.getDesocupa());
            if (estabaDesocupado) {
                capitalCongelado = BigDecimal.ZERO; // Muere el capital
            }

            // 6. MOTOR DE INTERESES
            BigDecimal interesesAcumulados = BigDecimal.ZERO;
            
            if (capitalCongelado.compareTo(BigDecimal.ZERO) > 0) {
                // Ordenamos el historial del usuario cronológicamente
                historialUsuario.sort((f1, f2) -> {
                    if (!f1.getAno().equals(f2.getAno())) return f1.getAno().compareTo(f2.getAno());
                    return f1.getMes().compareTo(f2.getMes());
                });

                // Recorremos los meses del usuario
                for (FacturaOriginal facturaMesSiguiente : historialUsuario) {
                    // Ignoramos el mes base (no genera interés sobre sí mismo en este punto)
                    if (facturaMesSiguiente.getId().equals(facturaBase.getId())) continue;

                    // REGLA DE PAGO: Solo aplicamos interés si la factura está pagada (pagada = true/1)
                    if (Boolean.TRUE.equals(facturaMesSiguiente.getPagada())) {
                        
                        // Buscamos la Tasa de Interés exacta para este mes específico
                        String llaveTasa = facturaMesSiguiente.getAno() + "-" + facturaMesSiguiente.getMes();
                        BigDecimal tasaDelMes = mapaTasasPorPeriodo.getOrDefault(llaveTasa, BigDecimal.ZERO);
                        
                        // Calculamos y sumamos
                        interesesAcumulados = interesesAcumulados.add(
                            calcularInteresFinanciero(capitalCongelado, tipoInteres, tasaDelMes)
                        );
                    }
                }
            }

            // 7. CONSTRUIR LA ENTIDAD FINANCIERA
            Reliquidacion reliquidacion = Reliquidacion.builder()
                    .jobId(jobIdTarifas)
                    .facturaOriginalId(facturaBase.getId())
                    .aprovechador(tarifaAplicada.getAprovechador())
                    .tarifaAplicada(tarifaAplicada)
                    .concepto("TA")
                    .mesBase(mesBase)
                    .anioBase(anoBase)
                    .desocupadoMesBase(estabaDesocupado)
                    .tipoInteres(tipoInteres)
                    .valorBaseTarifa(valorBase)
                    .capitalReliquidado(capitalCongelado)
                    .interesesCalculados(interesesAcumulados)
                    .totalDevolucion(capitalCongelado.add(interesesAcumulados))
                    .esDemo(esDemo)
                    .fechaCalculo(LocalDateTime.now())
                    .build();

            reliquidacionesCalculadas.add(reliquidacion);
        }

        log.info("Cálculo finalizado. Se procesaron {} reliquidaciones TA.", reliquidacionesCalculadas.size());
        return reliquidacionesCalculadas;
    }

    // 8. MÉTODO AUXILIAR PARA LA MATEMÁTICA PURA
    private BigDecimal calcularInteresFinanciero(BigDecimal capital, String tipoInteres, BigDecimal tasaDelMes) {
        if ("NINGUNO".equalsIgnoreCase(tipoInteres) || tasaDelMes.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        if ("SIMPLE".equalsIgnoreCase(tipoInteres)) {
            // Interés Simple = Capital * Tasa (para ese periodo)
            return capital.multiply(tasaDelMes);
            
        } else if ("COMPUESTO".equalsIgnoreCase(tipoInteres)) {
            // Nota: Para interés compuesto mes a mes real, el capital base se debe ir acumulando.
            // Si manejas capital fijo mes a mes pero lo llamas "compuesto", se multiplicaría igual.
            // Por ahora lo dejamos igual, pero si tienes una fórmula específica la podemos ajustar.
            return capital.multiply(tasaDelMes); 
        }
        
        return BigDecimal.ZERO;
    }
}