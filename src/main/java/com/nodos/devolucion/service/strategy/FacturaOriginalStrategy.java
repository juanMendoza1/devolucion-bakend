package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FacturaOriginalStrategy extends AbstractCsvImportStrategy {

    private final FacturaOriginalRepository repository;
    private static final int BATCH_SIZE = 2000;

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
    public void procesarArchivo(File file, String jobId, String usuIdRegistro, String novedad, String version) throws Exception {
        
        AuditoriaImportacion auditoria = crearAuditoriaInicial(file, jobId, usuIdRegistro);
        List<FacturaOriginal> loteFacturas = new ArrayList<>();
        int totalLineasProcesadas = 0;

        try (Reader reader = new InputStreamReader(new FileInputStream(file));
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            String[] headers = csvReader.readNext();
            Map<String, Integer> headerMap = construirMapaEncabezados(headers, EXPECTED_HEADERS, jobId);

            String[] linea;
            enviarProgreso(jobId, 5, "Iniciando lectura e inserción dinámica de facturas...");

            while ((linea = csvReader.readNext()) != null) {
                if (linea.length == 0 || (linea.length == 1 && linea[0].isBlank())) continue;

                try {
                    FacturaOriginal factura = FacturaOriginal.builder()
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

                    loteFacturas.add(factura);
                    totalLineasProcesadas++;

                    if (loteFacturas.size() >= BATCH_SIZE) {
                        repository.saveAllAndFlush(loteFacturas);
                        loteFacturas.clear();
                        enviarProgreso(jobId, -1, "Insertados " + totalLineasProcesadas + " registros de Facturas...");
                    }

                } catch (Exception e) {
                    int lineaRealExcel = totalLineasProcesadas + 2;
                    enviarErrorYDetener(jobId, "Error en la fila " + lineaRealExcel + ". Detalle: " + e.getMessage());
                }
            }

            if (!loteFacturas.isEmpty()) {
                repository.saveAllAndFlush(loteFacturas);
                loteFacturas.clear();
            }

            registrarExitoAuditoria(auditoria, totalLineasProcesadas, "Cargue completado exitosamente. Total registros: " + totalLineasProcesadas);

        } catch (Exception e) {
            registrarErrorAuditoria(auditoria, totalLineasProcesadas, e.getMessage());
            throw e;
        }
    }
}