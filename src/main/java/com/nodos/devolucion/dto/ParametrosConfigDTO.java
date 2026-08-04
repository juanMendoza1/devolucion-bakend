package com.nodos.devolucion.dto;

import java.util.List;

public class ParametrosConfigDTO {
    
    private List<LoteInfo> lotes;
    private List<String> conceptos;
    private List<String> tiposInteres;

    // Clase interna para mapear el ID y la descripción del lote
    public static class LoteInfo {
        private String jobId;
        private String descripcion;

        public LoteInfo(String jobId, String descripcion) {
            this.jobId = jobId;
            this.descripcion = descripcion;
        }

        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    }

    // Getters y Setters
    public List<LoteInfo> getLotes() { return lotes; }
    public void setLotes(List<LoteInfo> lotes) { this.lotes = lotes; }
    public List<String> getConceptos() { return conceptos; }
    public void setConceptos(List<String> conceptos) { this.conceptos = conceptos; }
    public List<String> getTiposInteres() { return tiposInteres; }
    public void setTiposInteres(List<String> tiposInteres) { this.tiposInteres = tiposInteres; }
}