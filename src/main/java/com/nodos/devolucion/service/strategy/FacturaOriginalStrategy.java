package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FacturaOriginalStrategy extends AbstractCsvImportStrategy<FacturaOriginal> {

    private final FacturaOriginalRepository repository;

    private final String[] EXPECTED_HEADERS = {
        "codigo_dataease", "esp", "usuario", "factura", "uso", 
        "estrato", "desocupa", "descto_pap", "medicion_aforo", 
        "estado", "porcentaje", "pagada", "fecha_pago", "vlr_factu", 
        "vlr_pagado", "mes", "ano", "toneladas"
    };

    public FacturaOriginalStrategy(FacturaOriginalRepository repository, 
                                   AuditoriaImportacionRepository auditoriaRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        super(auditoriaRepository, messagingTemplate);
        this.repository = repository;
    }

    @Override
    public String getTipoArchivo() {
        return "FACTURAS_ORIGINALES";
    }

    @Override
    protected String[] getExpectedHeaders() {
        return EXPECTED_HEADERS;
    }

    @Override
    protected FacturaOriginal mapearFila(String[] linea, Map<String, Integer> headerMap, String jobId) {
        return FacturaOriginal.builder()
                .codigoDataease(getValor(linea, headerMap, "codigo_dataease"))
                .esp(getValor(linea, headerMap, "esp"))
                .usuario(getValor(linea, headerMap, "usuario"))
                .factura(getValor(linea, headerMap, "factura"))
                .uso(getValor(linea, headerMap, "uso"))
                .estrato(getValor(linea, headerMap, "estrato"))
                .desocupa(parseBoolean(getValor(linea, headerMap, "desocupa")))
                .desctoPap(parseBoolean(getValor(linea, headerMap, "descto_pap")))
                .medicionAforo(getValor(linea, headerMap, "medicion_aforo"))
                .estado(getValor(linea, headerMap, "estado"))
                .porcentaje(parseDouble(getValor(linea, headerMap, "porcentaje")))
                .pagada(parseBoolean(getValor(linea, headerMap, "pagada")))
                .fechaPago(parseLocalDate(getValor(linea, headerMap, "fecha_pago")))
                .vlrFactu(parseBigDecimal(getValor(linea, headerMap, "vlr_factu")))
                .vlrPagado(parseBigDecimal(getValor(linea, headerMap, "vlr_pagado")))
                .mes(parseInteger(getValor(linea, headerMap, "mes")))
                .ano(parseInteger(getValor(linea, headerMap, "ano")))
                .toneladas(parseDouble(getValor(linea, headerMap, "toneladas")))
                .jobId(jobId)
                .build();
    }

    @Override
    protected void guardarBatch(List<FacturaOriginal> batch) {
        repository.saveAllAndFlush(batch);
    }
}