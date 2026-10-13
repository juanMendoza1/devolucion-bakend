package com.nodos.devolucion.repository;

import com.nodos.devolucion.dto.ReliquidacionResultadoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas SQL directas sobre reliquidaciones (tabla de millones de filas).
 *
 * Regla de capital (la misma del procedimiento proc_reliquidar_ta): el capital de un usuario en una tarifa es
 * "devolucion_aplicar", tomado UNA sola vez, en el primer periodo liquidado; si el usuario estaba desocupado en el
 * mes base el capital es 0 y no genera intereses. El procedimiento ya deja ese valor en capital_reliquidado del
 * primer periodo (y 0 en los demás), así que capital = SUM(capital_reliquidado) y total = SUM(total_devolucion).
 * Esta misma regla se usa en el resumen del modal, en los totales del maestro y en el desglose mensual,
 * para que todas las pantallas cuadren con el detalle fila a fila.
 *
 * Rendimiento: se evita COUNT(DISTINCT) sobre toda la tabla (40 s con ~10 M filas por maestro).
 * Como el procedimiento inserta exactamente una fila por usuario/tarifa/mes, COUNT(*) del grupo = usuarios.
 */
@Repository
@RequiredArgsConstructor
public class ReliquidacionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /** Primer periodo (anio*12+mes) de cada tarifa dentro de un maestro. */
    private static final String CTE_BASE =
            "WITH base AS ( " +
            "  SELECT job_id, MIN(anio_base * 12 + mes_base) AS p0 " +
            "  FROM reliquidaciones WHERE devolucion_maestro_id = ? %s GROUP BY job_id ) ";

    /** Usuarios = filas del primer periodo (el procedimiento inserta una fila por usuario, tarifa y mes). */
    private static final String ES_PRIMER_PERIODO = "r.anio_base * 12 + r.mes_base = b.p0";

    /** capital_reliquidado solo trae valor en el primer periodo (devolucion_aplicar, 0 si estaba desocupado). */
    private static final String CAPITAL = "COALESCE(SUM(r.capital_reliquidado), 0)";

    private static final RowMapper<ReliquidacionResultadoDTO> RESULTADO_MAPPER = (rs, i) -> new ReliquidacionResultadoDTO(
            rs.getLong("id"), rs.getString("job_id"), rs.getLong("factura_original_id"), rs.getString("factura"),
            rs.getString("codigo_dataease"), rs.getString("concepto"),
            (Integer) rs.getObject("mes_base"), (Integer) rs.getObject("anio_base"),
            (Boolean) rs.getObject("desocupado_mes_base"), rs.getString("tipo_interes"),
            rs.getBigDecimal("valor_base_tarifa"), rs.getBigDecimal("capital_reliquidado"),
            rs.getBigDecimal("intereses_calculados"), rs.getBigDecimal("total_devolucion"),
            (Boolean) rs.getObject("es_demo"), rs.getString("detalle_tasas"),
            rs.getBigDecimal("aplicado"), rs.getBigDecimal("ajustado"), rs.getBigDecimal("devolucion"),
            rs.getBigDecimal("porsubcon"), rs.getBigDecimal("subcon"));

    /** Filas de una tarifa dentro de un maestro (limitado para no saturar el navegador). */
    public List<ReliquidacionResultadoDTO> listarResultados(Long maestroId, String jobId, int limite) {
        return jdbcTemplate.query(
                "SELECT id, job_id, factura_original_id, factura, codigo_dataease, concepto, mes_base, anio_base, " +
                "desocupado_mes_base, tipo_interes, valor_base_tarifa, capital_reliquidado, intereses_calculados, " +
                "total_devolucion, es_demo, detalle_tasas, aplicado, ajustado, devolucion, porsubcon, subcon " +
                "FROM reliquidaciones WHERE devolucion_maestro_id = ? AND job_id = ? " +
                "ORDER BY codigo_dataease, anio_base, mes_base LIMIT ?",
                RESULTADO_MAPPER, maestroId, jobId, limite);
    }

    /** Resumen de una tarifa (modal): concepto, usuarios, capital, intereses y total. */
    public Map<String, Object> resumenTarifa(Long maestroId, String jobId) {
        String sql = String.format(CTE_BASE, "AND job_id = ?") +
                "SELECT MIN(r.concepto) AS concepto, COUNT(*) FILTER (WHERE " + ES_PRIMER_PERIODO + ") AS usuarios, " +
                CAPITAL + " AS capital, COALESCE(SUM(r.intereses_calculados), 0) AS intereses " +
                "FROM reliquidaciones r JOIN base b ON b.job_id = r.job_id " +
                "WHERE r.devolucion_maestro_id = ? AND r.job_id = ?";
        return jdbcTemplate.queryForObject(sql, (rs, i) -> {
            BigDecimal capital = rs.getBigDecimal("capital");
            BigDecimal intereses = rs.getBigDecimal("intereses");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("concepto", rs.getString("concepto"));
            m.put("usuarios", rs.getLong("usuarios"));
            m.put("capital", capital);
            m.put("intereses", intereses);
            m.put("total", capital.add(intereses));
            return m;
        }, maestroId, jobId, maestroId, jobId);
    }

    /** Totales del maestro: [usuarios distintos, capital, intereses]. */
    public Object[] totalesMaestro(Long maestroId) {
        String sql = String.format(CTE_BASE, "") +
                "SELECT COUNT(DISTINCT r.codigo_dataease) FILTER (WHERE " + ES_PRIMER_PERIODO + ") AS usuarios, " + CAPITAL + " AS capital, " +
                "COALESCE(SUM(r.intereses_calculados), 0) AS intereses " +
                "FROM reliquidaciones r JOIN base b ON b.job_id = r.job_id " +
                "WHERE r.devolucion_maestro_id = ?";
        return jdbcTemplate.queryForObject(sql, (rs, i) -> new Object[]{
                rs.getLong("usuarios"), rs.getBigDecimal("capital"), rs.getBigDecimal("intereses")
        }, maestroId, maestroId);
    }

    /** Desglose mensual por tarifa del maestro (pantalla Consolidados). */
    public List<Map<String, Object>> desgloseMensual(Long maestroId) {
        String sql = String.format(CTE_BASE, "") +
                "SELECT r.job_id, r.mes_base, r.anio_base, r.concepto, COUNT(*) AS usuarios, " +
                CAPITAL + " AS capital, COALESCE(SUM(r.intereses_calculados), 0) AS intereses " +
                "FROM reliquidaciones r JOIN base b ON b.job_id = r.job_id " +
                "WHERE r.devolucion_maestro_id = ? " +
                "GROUP BY r.job_id, r.mes_base, r.anio_base, r.concepto " +
                "ORDER BY r.job_id, r.anio_base, r.mes_base";
        return jdbcTemplate.query(sql, (rs, i) -> {
            BigDecimal capital = rs.getBigDecimal("capital");
            BigDecimal intereses = rs.getBigDecimal("intereses");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jobId", rs.getString("job_id"));
            m.put("mes", rs.getInt("mes_base"));
            m.put("anio", rs.getInt("anio_base"));
            m.put("concepto", rs.getString("concepto"));
            m.put("usuarios", rs.getLong("usuarios"));
            m.put("capital", capital);
            m.put("intereses", intereses);
            m.put("total", capital.add(intereses));
            return m;
        }, maestroId, maestroId);
    }

    /**
     * Actualiza las estadísticas del planificador de PostgreSQL sobre reliquidaciones.
     * Imprescindible después de cada carga masiva del procedimiento: si la tabla venía vacía (p. ej. tras un
     * reset) y no se analiza, el planificador cree que tiene 0 filas y escoge planes de bucle anidado que
     * convierten el resumen (1-2 s) en horas.
     */
    public void actualizarEstadisticas() {
        jdbcTemplate.execute("ANALYZE reliquidaciones");
    }

    /** Nombre de la devolución que ya liquidó (COMPLETADO) esta tarifa, si existe. Una tarifa solo se liquida una vez. */
    public java.util.Optional<String> devolucionQueLiquidoTarifa(String jobId) {
        return jdbcTemplate.queryForList(
                "SELECT m.nombre_devolucion FROM devolucion_maestro_tarifas t " +
                "JOIN devolucion_maestro m ON m.id = t.devolucion_maestro_id " +
                "WHERE t.job_id = ? AND t.estado = 'COMPLETADO' LIMIT 1", String.class, jobId)
                .stream().findFirst();
    }

    /**
     * Historial de devoluciones aplicadas a un código Dataease, mes a mes, para el visor.
     * "pagada" indica si el usuario facturó y pagó ese mes (null = no facturó ese mes).
     */
    public List<Map<String, Object>> historialPorCodigo(String codigoDataease) {
        return jdbcTemplate.query(
                "SELECT m.id AS maestro_id, m.nombre_devolucion, m.fecha_calculo, " +
                "       COALESCE(t.nombre_tarifa, r.job_id) AS nombre_tarifa, r.job_id, r.concepto, r.tipo_interes, " +
                "       r.mes_base, r.anio_base, r.desocupado_mes_base, r.factura, r.detalle_tasas, " +
                "       r.valor_base_tarifa, r.capital_reliquidado, r.intereses_calculados, r.total_devolucion, " +
                "       CASE WHEN f.id IS NULL THEN NULL ELSE f.pagada END AS pagada " +
                "FROM reliquidaciones r " +
                "JOIN devolucion_maestro m ON m.id = r.devolucion_maestro_id " +
                "LEFT JOIN devolucion_maestro_tarifas t ON t.devolucion_maestro_id = m.id AND t.job_id = r.job_id " +
                "LEFT JOIN factura_original f ON f.id = r.factura_original_id AND f.mes = r.mes_base AND f.ano = r.anio_base " +
                "WHERE r.codigo_dataease = ? " +
                "ORDER BY m.fecha_calculo DESC, nombre_tarifa, r.anio_base, r.mes_base",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("maestroId", rs.getLong("maestro_id"));
                    m.put("nombreDevolucion", rs.getString("nombre_devolucion"));
                    m.put("fechaCalculo", rs.getTimestamp("fecha_calculo"));
                    m.put("nombreTarifa", rs.getString("nombre_tarifa"));
                    m.put("jobId", rs.getString("job_id"));
                    m.put("concepto", rs.getString("concepto"));
                    m.put("tipoInteres", rs.getString("tipo_interes"));
                    m.put("mes", rs.getInt("mes_base"));
                    m.put("anio", rs.getInt("anio_base"));
                    m.put("desocupadoMesBase", rs.getObject("desocupado_mes_base"));
                    m.put("factura", rs.getString("factura"));
                    m.put("tasa", rs.getString("detalle_tasas"));
                    m.put("valorBaseTarifa", rs.getBigDecimal("valor_base_tarifa"));
                    m.put("capital", rs.getBigDecimal("capital_reliquidado"));
                    m.put("intereses", rs.getBigDecimal("intereses_calculados"));
                    m.put("total", rs.getBigDecimal("total_devolucion"));
                    m.put("pagada", rs.getObject("pagada"));
                    return m;
                }, codigoDataease);
    }

    /** Busca el id de un maestro por nombre; si no existe lo inserta (usado dentro de la transacción del simulador). */
    public Long obtenerOCrearMaestroId(String nombreDevolucion, String usuario) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM devolucion_maestro WHERE nombre_devolucion = ?", Long.class, nombreDevolucion);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbcTemplate.queryForObject(
                "INSERT INTO devolucion_maestro (nombre_devolucion, fecha_calculo, usuario, es_demo, cantidad_tarifas, " +
                "cantidad_registros, total_devolucion, total_capital, total_intereses) " +
                "VALUES (?, now(), ?, true, 0, 0, 0, 0, 0) RETURNING id",
                Long.class, nombreDevolucion, usuario);
    }
}
