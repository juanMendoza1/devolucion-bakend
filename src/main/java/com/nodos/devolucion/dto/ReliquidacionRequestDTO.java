package com.nodos.devolucion.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReliquidacionRequestDTO {
    private String jobId;
    private String concepto; // "TA", "TRT"
    private String tipoInteres; // "NINGUNO", "SIMPLE", "COMPUESTO"
    private boolean esDemo;
    
    // Lista de meses ordenados que mandará el frontend
    // Ejemplo: [{"mes": 2, "ano": 2026}, {"mes": 3, "ano": 2026}]
    private List<Map<String, Integer>> periodosOrdenados; 
}