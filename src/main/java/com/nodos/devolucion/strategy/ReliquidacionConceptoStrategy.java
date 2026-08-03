package com.nodos.devolucion.strategy;

import com.nodos.devolucion.entity.Reliquidacion;
import java.util.List;
import java.util.Map;

public interface ReliquidacionConceptoStrategy {
    
    // Este método nos dirá a qué concepto responde esta estrategia ("TA", "TRT", etc.)
    String obtenerConcepto(); 

    // Aquí irá la lógica pesada. Recibe el Lote, el rango de meses, y si aplica interés.
    List<Reliquidacion> calcularDevolucion(
            String jobId, 
            String tipoInteres, 
            boolean esDemo,
            List<Map<String, Integer>> periodosOrdenados // [{mes: 2, anio: 2026}, {mes: 3, anio: 2026}]
    );
}