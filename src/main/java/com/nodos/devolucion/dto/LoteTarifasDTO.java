package com.nodos.devolucion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoteTarifasDTO {

    private String jobId;
    private Integer mes;
    private Integer anio;
    private String novedad;
    private Long totalTarifas;
    private Long totalAsignadas;
    private Long pendientes;

    // Constructor que hace match exacto con el SELECT del Repositorio
    public LoteTarifasDTO(String jobId, Integer mes, Integer anio, String novedad, Long totalTarifas, Long totalAsignadas, Long pendientes) {
        this.jobId = jobId;
        this.mes = mes;
        this.anio = anio;
        this.novedad = novedad;
        this.totalTarifas = totalTarifas != null ? totalTarifas : 0L;
        this.totalAsignadas = totalAsignadas != null ? totalAsignadas : 0L;
        this.pendientes = pendientes != null ? pendientes : 0L;
    }
}