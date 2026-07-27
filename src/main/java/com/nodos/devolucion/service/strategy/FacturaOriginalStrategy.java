package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class FacturaOriginalStrategy implements CsvImportStrategy {

    private final FacturaOriginalRepository repository;
    private final AuditoriaImportacionRepository auditoriaRepository; // Inyectado
    private final SimpMessagingTemplate messagingTemplate;

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
        this.repository = repository;
        this.auditoriaRepository = auditoriaRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public String getTipoArchivo() {
        return "FACTURAS_ORIGINALES";
    }

    @Override
    public void procesarArchivo(MultipartFile file, String jobId, String usuIdRegistro) throws Exception {
        
        // 1. REGISTRO INICIAL DE AUDITORÍA
        AuditoriaImportacion auditoria = AuditoriaImportacion.builder()
                .jobId(jobId)
                .nombreArchivo(file.getOriginalFilename())
                .usuIdRegistro(usuIdRegistro) // Guardamos quién hizo la importación
                .fechaInicio(LocalDateTime.now())
                .estado("EN_PROCESO")
                .build();
        auditoriaRepository.save(auditoria);

        List<FacturaOriginal> loteFacturas = new ArrayList<>();
        int totalLineasProcesadas = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            // 2. VALIDACIÓN DE ENCABEZADOS
            String[] headers = csvReader.readNext();
            if (headers == null || headers.length == 0) {
                enviarErrorYDetener(jobId, "El archivo está vacío o no tiene encabezados.");
            }

            for (int i = 0; i < headers.length; i++) {
                headers[i] = headers[i].trim().toLowerCase();
            }

            List<String> expectedList = Arrays.asList(EXPECTED_HEADERS);
            List<String> actualList = Arrays.asList(headers);
            List<String> columnasFaltantes = new ArrayList<>();

            for (String expected : expectedList) {
                if (!actualList.contains(expected)) {
                    columnasFaltantes.add(expected);
                }
            }

            if (!columnasFaltantes.isEmpty()) {
                enviarErrorYDetener(jobId, "Faltan las siguientes columnas: " + String.join(", ", columnasFaltantes));
            }

            if (!Arrays.equals(headers, EXPECTED_HEADERS)) {
                enviarErrorYDetener(jobId, "Las columnas están presentes pero no respetan el orden exacto de la plantilla.");
            }

            // 3. PROCESAMIENTO Y GUARDADO POR BATCH (LOTES)
            String[] linea;
            enviarProgreso(jobId, 5, "Iniciando lectura e inserción de datos...");

            while ((linea = csvReader.readNext()) != null) {
                if (linea.length < 18) continue;

                try {
                    FacturaOriginal factura = FacturaOriginal.builder()
                            .codigoDataease(linea[0].trim())
                            .esp(linea[1].trim())
                            .usuario(linea[2].trim())
                            .factura(linea[3].trim())
                            .uso(linea[4].trim())
                            .estrato(linea[5] != null ? linea[5].trim() : null)
                            .desocupa(parseBoolean(linea[6]))
                            .desctoPap(parseBoolean(linea[7]))
                            .medicionAforo(linea[8].trim())
                            .estado(linea[9].trim())
                            .porcentaje(parseDouble(linea[10]))
                            .pagada(parseBoolean(linea[11]))
                            .fechaPago(parseLocalDate(linea[12]))
                            .vlrFactu(parseBigDecimal(linea[13]))
                            .vlrPagado(parseBigDecimal(linea[14]))
                            .mes(parseInteger(linea[15]))
                            .ano(parseInteger(linea[16]))
                            .toneladas(parseDouble(linea[17]))
                            .jobId(jobId) // <--- VINCULACIÓN A LA AUDITORÍA
                            .build();

                    loteFacturas.add(factura);
                    totalLineasProcesadas++;

                    // Guardado por lotes cada 2000 registros
                    if (loteFacturas.size() >= BATCH_SIZE) {
                        repository.saveAllAndFlush(loteFacturas);
                        loteFacturas.clear(); // Libera RAM
                        enviarProgreso(jobId, -1, "Insertados " + totalLineasProcesadas + " registros en BD...");
                    }

                } catch (NumberFormatException e) {
                    int lineaRealExcel = totalLineasProcesadas + 2; 
                    enviarErrorYDetener(jobId, "Error de formato numérico en la fila " + lineaRealExcel + " del CSV. Detalle: " + e.getMessage());
                } catch (Exception e) {
                    int lineaRealExcel = totalLineasProcesadas + 2;
                    enviarErrorYDetener(jobId, "Error en la fila " + lineaRealExcel + ". Detalle: " + e.getMessage());
                }
            }

            // Guardar el último lote sobrante
            if (!loteFacturas.isEmpty()) {
                repository.saveAllAndFlush(loteFacturas);
                loteFacturas.clear();
            }

            // 4. FINALIZACIÓN Y AUDITORÍA (ÉXITO)
            auditoria.setFechaFin(LocalDateTime.now());
            auditoria.setTotalRegistros(totalLineasProcesadas);
            auditoria.setEstado("COMPLETADO");
            auditoriaRepository.save(auditoria);

            enviarProgreso(jobId, 100, "Cargue completado exitosamente. Total registros: " + totalLineasProcesadas);

        } catch (Exception e) {
            // 5. REGISTRO DE ERROR EN LA AUDITORÍA
            auditoria.setFechaFin(LocalDateTime.now());
            auditoria.setEstado("ERROR");
            auditoria.setTotalRegistros(totalLineasProcesadas); // Lo que alcanzó a procesar
            auditoria.setMensajeError(e.getMessage());
            auditoriaRepository.save(auditoria);
            
            throw e; // Lanzamos la excepción para el log interno
        }
    }

    private void enviarErrorYDetener(String jobId, String detalleError) {
        String mensajeCompleto = "ERROR: " + detalleError;
        enviarProgreso(jobId, 0, mensajeCompleto);
        throw new IllegalArgumentException(mensajeCompleto); // Esto es capturado por el catch general
    }

    private void enviarProgreso(String jobId, int porcentaje, String mensaje) {
        messagingTemplate.convertAndSend("/topic/import-progress/" + jobId, 
            Map.of("porcentaje", porcentaje, "mensaje", mensaje)
        );
    }

    // --- MÉTODOS DE CONVERSIÓN ---

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String cleanVal = val.trim().toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return Integer.parseInt(val.trim());
    }

    private Double parseDouble(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String cleanVal = val.trim().toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return Double.parseDouble(val.trim());
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String cleanVal = val.trim().toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return new BigDecimal(val.trim());
    }

    private Boolean parseBoolean(String val) {
        if (val == null || val.trim().isEmpty()) return false;
        String cleanVal = val.trim().toLowerCase();
        return cleanVal.equals("true") || cleanVal.equals("1") || cleanVal.equals("si") || cleanVal.equals("sí");
    }

    private LocalDate parseLocalDate(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String cleanVal = val.trim().toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A")) return null;
        
        if (cleanVal.contains(" ")) {
            cleanVal = cleanVal.split(" ")[0];
        }
        
        return LocalDate.parse(cleanVal, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}