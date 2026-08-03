package com.nodos.devolucion.dto;
import java.util.List;

public record AsignacionTarifaRequest(
    Long aprovechadorId,
    List<Long> tarifasIds
) {}