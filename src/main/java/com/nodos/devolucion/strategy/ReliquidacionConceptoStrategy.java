package com.nodos.devolucion.strategy;

import com.nodos.devolucion.dto.ReliquidacionRequestDTO.TarifaConfigDTO;
import com.nodos.devolucion.entity.DevolucionMaestro;
import com.nodos.devolucion.entity.Reliquidacion;
import java.util.List;

public interface ReliquidacionConceptoStrategy {

    String obtenerConcepto();

    List<Reliquidacion> calcularDevolucion(
            String nombreDevolucion,
            TarifaConfigDTO config,
            boolean esDemo,
            List<String> codigosPrueba,
            DevolucionMaestro maestro
    );
}