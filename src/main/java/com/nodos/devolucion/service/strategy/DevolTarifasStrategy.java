package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import com.nodos.devolucion.entity.DevolTarifas;
import com.nodos.devolucion.entity.VersionTarifa;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.VersionTarifaRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class DevolTarifasStrategy implements CsvImportStrategy {

    private final DevolTarifasRepository repository;
    private final AuditoriaImportacionRepository auditoriaRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final VersionTarifaRepository versionTarifaRepository;

    private static final int BATCH_SIZE = 2000;

    // Los mismos encabezados exactos que configuraste en el React
    private final String[] EXPECTED_HEADERS = {
        "mes", "anio", "estrato", "concepto", "aplicado", 
        "ajustado", "devolucion", "porsubcon", "subcon", 
        "devolucion_aplicar", "liquidacion", "esp"
    };

    public DevolTarifasStrategy(DevolTarifasRepository repository, 
            AuditoriaImportacionRepository auditoriaRepository,
            SimpMessagingTemplate messagingTemplate,
            VersionTarifaRepository versionTarifaRepository) {
        this.repository = repository;
        this.auditoriaRepository = auditoriaRepository;
        this.messagingTemplate = messagingTemplate;
        this.versionTarifaRepository = versionTarifaRepository;
    }

    @Override
    public String getTipoArchivo() {
        // Esta llave conecta dinámicamente con la solicitud del frontend
        return "BASE_TARIFAS";
    }

    @Override
    public void procesarArchivo(File file, String jobId, String usuIdRegistro, String novedad, String version) throws Exception {
        
    	VersionTarifa cabecera = VersionTarifa.builder()
                .jobId(jobId)
                .novedad(novedad != null ? novedad : "Sin novedad")
                .version(version != null ? version : "1.0")
                .fechaCreacion(LocalDateTime.now())
                .estado("ACTIVA")
                .build();
        versionTarifaRepository.save(cabecera);
    	
        // 1. REGISTRO INICIAL DE AUDITORÍA
        AuditoriaImportacion auditoria = AuditoriaImportacion.builder()
                .jobId(jobId)
                .nombreArchivo(file.getName()) // <--- CAMBIO: Usar getName() en lugar de getOriginalFilename()
                .usuIdRegistro(usuIdRegistro)
                .fechaInicio(LocalDateTime.now())
                .estado("EN_PROCESO")
                .build();
        auditoriaRepository.save(auditoria);

        List<DevolTarifas> loteTarifas = new ArrayList<>();
        int totalLineasProcesadas = 0;

        // <--- CAMBIO: Usar FileInputStream para leer el java.io.File
        try (Reader reader = new InputStreamReader(new FileInputStream(file));
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

            // 3. PROCESAMIENTO Y GUARDADO POR BATCH
            String[] linea;
            enviarProgreso(jobId, 5, "Iniciando lectura e inserción de datos...");

            while ((linea = csvReader.readNext()) != null) {
                if (linea.length < 12) continue; // Número de columnas esperado

                try {
                    DevolTarifas tarifa = DevolTarifas.builder()
                            .mes(parseInteger(linea[0]))
                            .anio(parseInteger(linea[1]))
                            .estrato(linea[2].trim())
                            .concepto(linea[3].trim())
                            .aplicado(parseBigDecimal(linea[4]))
                            .ajustado(parseBigDecimal(linea[5]))
                            .devolucion(parseBigDecimal(linea[6]))
                            .porsubcon(parseBigDecimal(linea[7]))
                            .subcon(parseBigDecimal(linea[8]))
                            .devolucionAplicar(parseBigDecimal(linea[9]))
                            .liquidacion(linea[10].trim())
                            .esp(parseInteger(linea[11]))
                            .jobId(jobId) // VINCULACIÓN
                            .build();

                    loteTarifas.add(tarifa);
                    totalLineasProcesadas++;

                    // Guardado por lotes cada 2000 registros
                    if (loteTarifas.size() >= BATCH_SIZE) {
                        repository.saveAllAndFlush(loteTarifas);
                        loteTarifas.clear(); 
                        enviarProgreso(jobId, -1, "Insertados " + totalLineasProcesadas + " registros de Tarifas...");
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
            if (!loteTarifas.isEmpty()) {
                repository.saveAllAndFlush(loteTarifas);
                loteTarifas.clear();
            }

            // 4. FINALIZACIÓN
            auditoria.setFechaFin(LocalDateTime.now());
            auditoria.setTotalRegistros(totalLineasProcesadas);
            auditoria.setEstado("COMPLETADO");
            auditoriaRepository.save(auditoria);

            enviarProgreso(jobId, 100, "Cargue de Tarifas completado. Total registros: " + totalLineasProcesadas);

        } catch (Exception e) {
            // 5. REGISTRO DE ERROR
            auditoria.setFechaFin(LocalDateTime.now());
            auditoria.setEstado("ERROR");
            auditoria.setTotalRegistros(totalLineasProcesadas);
            auditoria.setMensajeError(e.getMessage());
            auditoriaRepository.save(auditoria);
            
            throw e; 
        }
    }

    private void enviarErrorYDetener(String jobId, String detalleError) {
        String mensajeCompleto = "ERROR: " + detalleError;
        enviarProgreso(jobId, 0, mensajeCompleto);
        throw new IllegalArgumentException(mensajeCompleto);
    }

    private void enviarProgreso(String jobId, int porcentaje, String mensaje) {
        messagingTemplate.convertAndSend("/topic/import-progress/" + jobId, 
            Map.of("porcentaje", porcentaje, "mensaje", mensaje)
        );
    }

    // --- UTILS (Iguales a tu otra estrategia) ---
    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String cleanVal = val.trim().toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return Integer.parseInt(val.trim());
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String cleanVal = val.trim().toUpperCase();
        if (cleanVal.equals("NULL") || cleanVal.equals("N/A") || cleanVal.equals("NAN")) return null;
        return new BigDecimal(val.trim());
    }
}