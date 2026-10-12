package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class AbstractCsvImportStrategy<T> implements CsvImportStrategy {

    protected final AuditoriaImportacionRepository auditoriaRepository;
    protected final SimpMessagingTemplate messagingTemplate;
    
    // Tamaño del lote para inserciones en base de datos
    private static final int BATCH_SIZE = 2000;

    public AbstractCsvImportStrategy(AuditoriaImportacionRepository auditoriaRepository, 
                                      SimpMessagingTemplate messagingTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // =========================================================
    // MÉTODOS ABSTRACTOS (Las clases hijas DEBEN implementarlos)
    // =========================================================
    
    protected abstract String[] getExpectedHeaders();
    
    protected abstract T mapearFila(String[] linea, Map<String, Integer> headerMap, String jobId) throws Exception;
    
    protected abstract void guardarBatch(List<T> batch);

    // =========================================================
    // HOOKS (Las clases hijas PUEDEN sobreescribirlos si quieren)
    // =========================================================
    
    protected void preProcesar(String jobId, String novedad, String version) {
        // Por defecto no hace nada. Si un hijo necesita hacer algo antes de leer el CSV, lo usa.
    }

    // =========================================================
    // MÉTODO PLANTILLA PRINCIPAL (El motor de lectura)
    // =========================================================
    
    @Override
    public void procesarArchivo(File file, String jobId, String usuIdRegistro, String novedad, String version) throws Exception {
        
        // 1. Ejecutar acciones previas si existen
        preProcesar(jobId, novedad, version);
        
        // 2. Crear auditoría
        AuditoriaImportacion auditoria = crearAuditoriaInicial(file, jobId, usuIdRegistro);
        List<T> loteObjetos = new ArrayList<>();
        int totalLineasProcesadas = 0;

        // 3. Lectura del archivo CSV
        try (Reader reader = new InputStreamReader(new FileInputStream(file));
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            String[] headers = csvReader.readNext();
            Map<String, Integer> headerMap = construirMapaEncabezados(headers, getExpectedHeaders(), jobId);

            String[] linea;
            enviarProgreso(jobId, 5, "Iniciando lectura e inserción dinámica...");

            while ((linea = csvReader.readNext()) != null) {
                if (linea.length == 0 || (linea.length == 1 && linea[0].isBlank())) continue;

                try {
                    // Delegamos la transformación a la clase hija
                    T entidad = mapearFila(linea, headerMap, jobId);
                    loteObjetos.add(entidad);
                    totalLineasProcesadas++;

                    // Control del Batch (Lote)
                    if (loteObjetos.size() >= BATCH_SIZE) {
                        guardarBatch(loteObjetos);
                        loteObjetos.clear(); 
                        enviarProgreso(jobId, -1, "Insertados " + totalLineasProcesadas + " registros...");
                    }

                } catch (Exception e) {
                    int lineaRealExcel = totalLineasProcesadas + 2;
                    enviarErrorYDetener(jobId, "Error en la fila " + lineaRealExcel + ". Detalle: " + e.getMessage());
                }
            }

            // Guardar el remanente final
            if (!loteObjetos.isEmpty()) {
                guardarBatch(loteObjetos);
                loteObjetos.clear();
            }

            registrarExitoAuditoria(auditoria, totalLineasProcesadas, "Cargue completado. Total registros: " + totalLineasProcesadas);

        } catch (Exception e) {
            registrarErrorAuditoria(auditoria, totalLineasProcesadas, e.getMessage());
            throw e; 
        }
    }

    // =========================================================
    // MÉTODOS DE UTILIDAD (Los que ya tenías perfectamente armados)
    // =========================================================

    protected AuditoriaImportacion crearAuditoriaInicial(File file, String jobId, String usuIdRegistro) {
        AuditoriaImportacion auditoria = AuditoriaImportacion.builder()
                .jobId(jobId)
                .nombreArchivo(file.getName())
                .usuIdRegistro(usuIdRegistro)
                .fechaInicio(LocalDateTime.now())
                .estado("EN_PROCESO")
                .build();
        return auditoriaRepository.save(auditoria);
    }

    protected Map<String, Integer> construirMapaEncabezados(String[] headers, String[] expectedHeaders, String jobId) {
        if (headers == null || headers.length == 0) {
            enviarErrorYDetener(jobId, "El archivo está vacío o no tiene encabezados.");
        }

        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null) {
                headerMap.put(headers[i].trim().toLowerCase(), i);
            }
        }

        List<String> faltantes = new ArrayList<>();
        for (String expected : expectedHeaders) {
            if (!headerMap.containsKey(expected.toLowerCase())) {
                faltantes.add(expected);
            }
        }

        if (!faltantes.isEmpty()) {
            enviarErrorYDetener(jobId, "Faltan las siguientes columnas requeridas: " + String.join(", ", faltantes));
        }

        return headerMap;
    }

    protected String getValor(String[] linea, Map<String, Integer> headerMap, String columna) {
        Integer index = headerMap.get(columna.toLowerCase());
        if (index == null || index >= linea.length || linea[index] == null) {
            return null;
        }
        return linea[index].trim();
    }

    protected void registrarExitoAuditoria(AuditoriaImportacion auditoria, int totalRegistros, String mensajeExito) {
        auditoria.setFechaFin(LocalDateTime.now());
        auditoria.setTotalRegistros(totalRegistros);
        auditoria.setEstado("COMPLETADO");
        auditoriaRepository.save(auditoria);
        enviarProgreso(auditoria.getJobId(), 100, mensajeExito);
    }

    protected void registrarErrorAuditoria(AuditoriaImportacion auditoria, int totalRegistros, String errorMsg) {
        auditoria.setFechaFin(LocalDateTime.now());
        auditoria.setEstado("ERROR");
        auditoria.setTotalRegistros(totalRegistros);
        auditoria.setMensajeError(errorMsg);
        auditoriaRepository.save(auditoria);
    }

    protected void enviarErrorYDetener(String jobId, String detalleError) {
        String mensajeCompleto = "ERROR: " + detalleError;
        enviarProgreso(jobId, 0, mensajeCompleto);
        throw new IllegalArgumentException(mensajeCompleto);
    }

    protected void enviarProgreso(String jobId, int porcentaje, String mensaje) {
        messagingTemplate.convertAndSend("/topic/import-progress/" + jobId, 
            Map.of("porcentaje", porcentaje, "mensaje", mensaje)
        );
    }

    protected Integer parseInteger(String val) {
        if (val == null || val.isEmpty()) return null;
        String cleanVal = val.toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return Integer.parseInt(val);
    }

    protected Double parseDouble(String val) {
        if (val == null || val.isEmpty()) return null;
        String cleanVal = val.toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return Double.parseDouble(val);
    }

    protected BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isEmpty()) return null;
        String cleanVal = val.toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return new BigDecimal(val);
    }

    protected Boolean parseBoolean(String val) {
        if (val == null || val.isEmpty()) return false;
        String cleanVal = val.toLowerCase();
        return cleanVal.equals("true") || cleanVal.equals("1") || cleanVal.equals("si") || cleanVal.equals("sí");
    }

    protected LocalDate parseLocalDate(String val) {
        if (val == null || val.isEmpty()) return null;
        String cleanVal = val.toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A")) return null;
        if (cleanVal.contains(" ")) cleanVal = cleanVal.split(" ")[0];
        return LocalDate.parse(cleanVal, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}