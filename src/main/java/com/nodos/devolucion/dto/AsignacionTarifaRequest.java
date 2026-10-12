package com.nodos.devolucion.dto;
import java.util.List;

public record AsignacionTarifaRequest(
	List<Long> aprovechadorIds,
    List<Long> tarifasIds
) {}