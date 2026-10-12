package com.nodos.devolucion.service.strategy;

import com.nodos.devolucion.entity.DevolTarifas;
import com.nodos.devolucion.entity.VersionTarifa;
import com.nodos.devolucion.repository.AuditoriaImportacionRepository;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.VersionTarifaRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DevolTarifasStrategy extends AbstractCsvImportStrategy<DevolTarifas> {

    private final DevolTarifasRepository repository;
    private final VersionTarifaRepository versionTarifaRepository;

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
    protected String[] getExpectedHeaders() {
        return EXPECTED_HEADERS;
    }

    @Override
    protected void preProcesar(String jobId, String novedad, String version) {
        VersionTarifa cabecera = VersionTarifa.builder()
                .jobId(jobId)
                .novedad(novedad != null ? novedad : "Sin novedad")
                .version(version != null ? version : "1.0")
                .fechaCreacion(LocalDateTime.now())
                .estado("ACTIVA")
                .build();
        versionTarifaRepository.save(cabecera);
    }

    @Override
    protected DevolTarifas mapearFila(String[] linea, Map<String, Integer> headerMap, String jobId) {
        return DevolTarifas.builder()
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
    }

    @Override
    protected void guardarBatch(List<DevolTarifas> batch) {
        repository.saveAllAndFlush(batch);
    }
}