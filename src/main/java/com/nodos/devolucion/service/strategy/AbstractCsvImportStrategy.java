package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class AbstractCsvImportStrategy implements CsvImportStrategy {

    protected final AuditoriaImportacionRepository auditoriaRepository;
    protected final SimpMessagingTemplate messagingTemplate;

    public AbstractCsvImportStrategy(AuditoriaImportacionRepository auditoriaRepository, 
                                      SimpMessagingTemplate messagingTemplate) {
        this.auditoriaRepository = auditoriaRepository;
        this.messagingTemplate = messagingTemplate;
    }

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

    /**
     * Mapea los encabezados del CSV a sus índices de columna correspondientes 
     * y valida que existan todas las columnas requeridas sin importar su orden.
     */
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

    /**
     * Extrae de forma segura el valor de una fila dada la clave del encabezado.
     */
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

    // --- Métodos Utilidades ---
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