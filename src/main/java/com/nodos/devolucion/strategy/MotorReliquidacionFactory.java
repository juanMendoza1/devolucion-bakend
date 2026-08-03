package com.nodos.devolucion.strategy;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MotorReliquidacionFactory {

    // Spring inyecta automáticamente TODAS las clases que implementen la interfaz
    private final Map<String, ReliquidacionConceptoStrategy> estrategias;

    public MotorReliquidacionFactory(List<ReliquidacionConceptoStrategy> strategyList) {
        // Convertimos la lista en un Mapa para buscar rapidísimo por nombre de concepto
        this.estrategias = strategyList.stream()
            .collect(Collectors.toMap(ReliquidacionConceptoStrategy::obtenerConcepto, s -> s));
    }

    public ReliquidacionConceptoStrategy obtenerEstrategia(String concepto) {
        ReliquidacionConceptoStrategy estrategia = estrategias.get(concepto.toUpperCase());
        if (estrategia == null) {
            throw new IllegalArgumentException("No existe una regla de cálculo para el concepto: " + concepto);
        }
        return estrategia;
    }
}