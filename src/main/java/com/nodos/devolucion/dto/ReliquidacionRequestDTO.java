package com.nodos.devolucion.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReliquidacionRequestDTO {

    private String nombreDevolucion;
    private boolean esDemo;
    private List<String> codigosPrueba;      
    private List<String> tarifasAProcesar;   
    private List<TarifaConfigDTO> tarifasConfig;

    @Data
    public static class TarifaConfigDTO {
        private String jobId;
        private String nombreVisible;
        private String concepto;
        private String tipoInteres;
        private List<Map<String, Integer>> periodosOrdenados;
    }
}