package com.nodos.devolucion.strategy;

import com.nodos.devolucion.dto.ReliquidacionRequestDTO.TarifaConfigDTO;
import com.nodos.devolucion.entity.DevolucionMaestro;
import com.nodos.devolucion.entity.Reliquidacion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReliquidacionTAStrategy implements ReliquidacionConceptoStrategy {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String obtenerConcepto() {
        return "TA";
    }

    @Override
    public List<Reliquidacion> calcularDevolucion(
            String nombreDevolucion,
            TarifaConfigDTO config,
            boolean esDemo,
            List<String> codigosPrueba,
            DevolucionMaestro maestro) {

        if (config.getPeriodosOrdenados() == null || config.getPeriodosOrdenados().isEmpty()) {
            return List.of();
        }

        // Extraemos los meses y años en arrays ordenados
        Integer[] meses = config.getPeriodosOrdenados().stream().map(p -> p.get("mes")).toArray(Integer[]::new);
        Integer[] anios = config.getPeriodosOrdenados().stream().map(p -> p.get("ano")).toArray(Integer[]::new);

        log.info("🚀 Delegando cálculo a PostgreSQL. JobId: {}. Total periodos: {}", config.getJobId(), meses.length);

        jdbcTemplate.execute((Connection conn) -> {
            String[] codigosArray = codigosPrueba != null ? codigosPrueba.toArray(new String[0]) : new String[0];
            
            // Convertimos a tipos de PostgreSQL
            Array sqlMeses = conn.createArrayOf("INT4", meses);
            Array sqlAnios = conn.createArrayOf("INT4", anios);
            Array sqlCodigos = conn.createArrayOf("TEXT", codigosArray);

            String sql = "CALL proc_reliquidar_ta(?, ?, ?, ?, ?, ?, ?, ?)";
            try (var ps = conn.prepareStatement(sql)) {
                ps.setLong(1, maestro.getId());
                ps.setString(2, config.getJobId());
                ps.setString(3, "TA");
                ps.setString(4, config.getTipoInteres());
                ps.setArray(5, sqlMeses);
                ps.setArray(6, sqlAnios);
                ps.setBoolean(7, esDemo);
                ps.setArray(8, sqlCodigos);
                ps.execute();
            }
            return null;
        });

        return List.of(); 
    }
}