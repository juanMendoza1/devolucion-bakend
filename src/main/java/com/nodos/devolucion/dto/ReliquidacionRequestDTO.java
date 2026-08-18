package com.nodos.devolucion.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReliquidacionRequestDTO {
    
    // --- NUEVOS CAMPOS PARA EL CONSOLIDADO MULTI-LOTE ---
    private String consolidadoTarifas; // Nombre de la campaña, Ej: "Consolidado Semestre B"
    private List<String> jobIds;       // Ahora es una lista para recibir varios lotes

    // --- CAMPOS EXISTENTES QUE MANTENEMOS INTACTOS ---
    private String concepto; // "TA", "TRT"
    private String tipoInteres; // "NINGUNO", "SIMPLE", "COMPUESTO"
    private boolean esDemo;   

    private List<Map<String, Integer>> periodosOrdenados; 
}