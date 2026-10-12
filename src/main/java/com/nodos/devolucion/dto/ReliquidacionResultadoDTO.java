package com.nodos.devolucion.dto;

import java.math.BigDecimal;

/**
 * Fila de resultado de reliquidación que se envía al frontend (simulación o vista previa de producción).
 * Los nombres coinciden con ReliquidacionResponse del frontend.
 */
public record ReliquidacionResultadoDTO(
        Long id,
        String jobId,
        Long facturaOriginalId,
        String factura,
        String codigoDataease,
        String concepto,
        Integer mesBase,
        Integer anioBase,
        Boolean desocupadoMesBase,
        String tipoInteres,
        BigDecimal valorBaseTarifa,
        BigDecimal capitalReliquidado,
        BigDecimal interesesCalculados,
        BigDecimal totalDevolucion,
        Boolean esDemo,
        String detalleTasas,
        BigDecimal aplicado,
        BigDecimal ajustado,
        BigDecimal devolucion,
        BigDecimal porsubcon,
        BigDecimal subcon
) {}
