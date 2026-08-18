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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DevolTarifasStrategy extends AbstractCsvImportStrategy {

    private final DevolTarifasRepository repository;
    private final VersionTarifaRepository versionTarifaRepository;
    private static final int BATCH_SIZE = 2000;

    private final String[] EXPECTED_HEADERS = {
        "mes", "anio", "estrato", "concepto", "aplicado", 
        "ajustado", "devolucion", "porsubcon", "subcon", 
        "devolucion_aplicar", "liquidacion", "esp"
    };

    public DevolTarifasStrategy(DevolTarifasRepository repository, 
                                AuditoriaImportacionRepository auditoriaRepository,
                                SimpMessagingTemplate messagingTemplate,
                                VersionTarifaRepository versionTarifaRepository) {
        super(auditoriaRepository, messagingTemplate);
        this.repository = repository;
        this.versionTarifaRepository = versionTarifaRepository;
    }

    @Override
    public String getTipoArchivo() {
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
        
        AuditoriaImportacion auditoria = crearAuditoriaInicial(file, jobId, usuIdRegistro);
        List<DevolTarifas> loteTarifas = new ArrayList<>();
        int totalLineasProcesadas = 0;

        try (Reader reader = new InputStreamReader(new FileInputStream(file));
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {

            String[] headers = csvReader.readNext();
            Map<String, Integer> headerMap = construirMapaEncabezados(headers, EXPECTED_HEADERS, jobId);

            String[] linea;
            enviarProgreso(jobId, 5, "Iniciando lectura e inserción dinámica de tarifas...");

            while ((linea = csvReader.readNext()) != null) {
                if (linea.length == 0 || (linea.length == 1 && linea[0].isBlank())) continue;

                try {
                    DevolTarifas tarifa = DevolTarifas.builder()
                            .mes(parseInteger(getValor(linea, headerMap, "mes")))
                            .anio(parseInteger(getValor(linea, headerMap, "anio")))
                            .estrato(getValor(linea, headerMap, "estrato"))
                            .concepto(getValor(linea, headerMap, "concepto"))
                            .aplicado(parseBigDecimal(getValor(linea, headerMap, "aplicado")))
                            .ajustado(parseBigDecimal(getValor(linea, headerMap, "ajustado")))
                            .devolucion(parseBigDecimal(getValor(linea, headerMap, "devolucion")))
                            .porsubcon(parseBigDecimal(getValor(linea, headerMap, "porsubcon")))
                            .subcon(parseBigDecimal(getValor(linea, headerMap, "subcon")))
                            .devolucionAplicar(parseBigDecimal(getValor(linea, headerMap, "devolucion_aplicar")))
                            .liquidacion(getValor(linea, headerMap, "liquidacion"))
                            .esp(parseInteger(getValor(linea, headerMap, "esp")))
                            .jobId(jobId)
                            .build();

                    loteTarifas.add(tarifa);
                    totalLineasProcesadas++;

                    if (loteTarifas.size() >= BATCH_SIZE) {
                        repository.saveAllAndFlush(loteTarifas);
                        loteTarifas.clear(); 
                        enviarProgreso(jobId, -1, "Insertados " + totalLineasProcesadas + " registros de Tarifas...");
                    }

                } catch (Exception e) {
                    int lineaRealExcel = totalLineasProcesadas + 2;
                    enviarErrorYDetener(jobId, "Error en la fila " + lineaRealExcel + ". Detalle: " + e.getMessage());
                }
            }

            if (!loteTarifas.isEmpty()) {
                repository.saveAllAndFlush(loteTarifas);
                loteTarifas.clear();
            }

            registrarExitoAuditoria(auditoria, totalLineasProcesadas, "Cargue de Tarifas completado. Total registros: " + totalLineasProcesadas);

        } catch (Exception e) {
            registrarErrorAuditoria(auditoria, totalLineasProcesadas, e.getMessage());
            throw e; 
        }
    }
}