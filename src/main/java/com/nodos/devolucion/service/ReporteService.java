package com.nodos.devolucion.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reporte "Facturación devolución": una fila por usuario objeto de la devolución (capital distinto de cero),
 * sumando todas las tarifas de la devolución elegida. Valores completos, sin redondear.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final JdbcTemplate jdbcTemplate;

    /** Mismo criterio del reporte: solo usuarios con devolución (capital <> 0). */
    private static final String SQL_FACTURACION =
            "SELECT codigo_dataease, SUM(capital_reliquidado) AS valor_devolucion, SUM(intereses_calculados) AS interes " +
            "FROM reliquidaciones WHERE devolucion_maestro_id = ? " +
            "GROUP BY codigo_dataease HAVING SUM(capital_reliquidado) <> 0 " +
            "ORDER BY codigo_dataease";

    /** Datos de la devolución + cantidad de usuarios y totales que tendrá el reporte. */
    public Map<String, Object> resumenFacturacion(Long maestroId) {
        Map<String, Object> maestro = buscarMaestro(maestroId);
        Map<String, Object> totales = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS usuarios, COALESCE(SUM(valor_devolucion), 0) AS valor_devolucion, " +
                "COALESCE(SUM(interes), 0) AS interes FROM (" + SQL_FACTURACION + ") x", maestroId);
        maestro.putAll(totales);
        return maestro;
    }

    public String nombreArchivo(Long maestroId) {
        return nombreArchivo(maestroId, "facturacion_devolucion_");
    }

    public String nombreArchivo(Long maestroId, String prefijo) {
        String nombre = String.valueOf(buscarMaestro(maestroId).get("nombre_devolucion"))
                .trim().replaceAll("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ_-]+", "_");
        return prefijo + nombre + ".xlsx";
    }

    // =====================================================================
    // CONSOLIDADO MES: capital (mes inicial de cada tarifa), tasa, interés del mes y acumulado.
    // El interés es el calculado por el sistema (solo usuarios que pagaron), sumado por mes.
    // =====================================================================

    public enum ModoConsolidado { CONSOLIDADO, POR_MES }

    public record FilaMes(int anio, int mes, BigDecimal capital, BigDecimal tasa, BigDecimal interes, BigDecimal acumulado) {}

    public record TablaConsolidado(String nombre, List<FilaMes> filas,
                                   BigDecimal totalCapital, BigDecimal totalInteres, BigDecimal total) {}

    /** Tablas del reporte: una sola (CONSOLIDADO) o una por tarifa (POR_MES), ordenadas por mes. */
    public List<TablaConsolidado> consolidadoMes(Long maestroId, ModoConsolidado modo) {
        buscarMaestro(maestroId);

        Map<Integer, BigDecimal> tasas = new HashMap<>();
        jdbcTemplate.query("SELECT ano, mes, MAX(tsi) AS tsi FROM tasas_interes GROUP BY ano, mes",
                rs -> { tasas.put(rs.getInt("ano") * 12 + rs.getInt("mes"), rs.getBigDecimal("tsi")); });

        // Sumas por tarifa y mes (capital solo trae valor en el mes inicial de cada tarifa)
        record Parcial(String jobId, String nombre, int anio, int mes, BigDecimal capital, BigDecimal interes) {}
        List<Parcial> parciales = jdbcTemplate.query(
                "SELECT r.job_id, COALESCE(t.nombre_tarifa, r.job_id) AS nombre, r.anio_base, r.mes_base, " +
                "       COALESCE(SUM(r.capital_reliquidado), 0) AS capital, COALESCE(SUM(r.intereses_calculados), 0) AS interes " +
                "FROM reliquidaciones r " +
                "LEFT JOIN devolucion_maestro_tarifas t ON t.devolucion_maestro_id = r.devolucion_maestro_id AND t.job_id = r.job_id " +
                "WHERE r.devolucion_maestro_id = ? " +
                "GROUP BY r.job_id, nombre, r.anio_base, r.mes_base " +
                "ORDER BY MIN(r.anio_base * 12 + r.mes_base) OVER (PARTITION BY r.job_id), nombre, r.anio_base, r.mes_base",
                (rs, i) -> new Parcial(rs.getString("job_id"), rs.getString("nombre"), rs.getInt("anio_base"),
                        rs.getInt("mes_base"), rs.getBigDecimal("capital"), rs.getBigDecimal("interes")),
                maestroId);

        // Agrupación: por tarifa (POR_MES) o todo junto (CONSOLIDADO); dentro de cada grupo, por mes
        Map<String, TreeMap<Integer, BigDecimal[]>> grupos = new LinkedHashMap<>();
        Map<String, String> nombres = new LinkedHashMap<>();
        for (Parcial p : parciales) {
            String clave = modo == ModoConsolidado.POR_MES ? p.jobId() : "CONSOLIDADO";
            nombres.putIfAbsent(clave, modo == ModoConsolidado.POR_MES ? p.nombre() : "Consolidado");
            BigDecimal[] acum = grupos.computeIfAbsent(clave, k -> new TreeMap<>())
                    .computeIfAbsent(p.anio() * 12 + p.mes(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acum[0] = acum[0].add(p.capital());
            acum[1] = acum[1].add(p.interes());
        }

        List<TablaConsolidado> tablas = new ArrayList<>();
        grupos.forEach((clave, meses) -> {
            List<FilaMes> filas = new ArrayList<>();
            BigDecimal acumulado = BigDecimal.ZERO, totalCapital = BigDecimal.ZERO, totalInteres = BigDecimal.ZERO;
            for (var e : meses.entrySet()) {
                int periodo = e.getKey();
                int anio = (periodo - 1) / 12, mes = (periodo - 1) % 12 + 1;
                BigDecimal capital = e.getValue()[0], interes = e.getValue()[1];
                acumulado = acumulado.add(capital).add(interes);
                totalCapital = totalCapital.add(capital);
                totalInteres = totalInteres.add(interes);
                filas.add(new FilaMes(anio, mes, capital, tasas.get(periodo), interes, acumulado));
            }
            tablas.add(new TablaConsolidado(nombres.get(clave), filas, totalCapital, totalInteres, acumulado));
        });
        return tablas;
    }

    /** Excel del consolidado mes: una hoja por tabla, con la estructura de la hoja "base". */
    public void escribirConsolidadoMes(Long maestroId, ModoConsolidado modo, OutputStream salida) throws IOException {
        List<TablaConsolidado> tablas = consolidadoMes(maestroId, modo);
        try (SXSSFWorkbook libro = new SXSSFWorkbook(200)) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);
            CellStyle estiloFecha = libro.createCellStyle();
            estiloFecha.setDataFormat(libro.createDataFormat().getFormat("mm/yyyy"));
            CellStyle estiloTotal = libro.createCellStyle();
            estiloTotal.setFont(negrita);

            Set<String> nombresHoja = new HashSet<>();
            if (tablas.isEmpty()) {
                tablas = List.of(new TablaConsolidado("Consolidado", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            }
            for (TablaConsolidado tabla : tablas) {
                SXSSFSheet hoja = libro.createSheet(nombreHojaUnico(tabla.nombre(), nombresHoja));
                String[] columnas = {"MES DE FACTURACION", "CAPITAL", "TASA INTERES", "INTERES", "CAPITAL + INTERES"};
                Row encabezado = hoja.createRow(0);
                for (int c = 0; c < columnas.length; c++) {
                    Cell celda = encabezado.createCell(c);
                    celda.setCellValue(columnas[c]);
                    celda.setCellStyle(estiloEncabezado);
                    hoja.setColumnWidth(c, (c == 0 ? 20 : 22) * 256);
                }
                hoja.createFreezePane(0, 1);

                int i = 1;
                for (FilaMes f : tabla.filas()) {
                    Row r = hoja.createRow(i++);
                    Cell fecha = r.createCell(0);
                    fecha.setCellValue(java.time.LocalDate.of(f.anio(), f.mes(), 1));
                    fecha.setCellStyle(estiloFecha);
                    if (f.capital().signum() != 0) escribirNumero(r.createCell(1), f.capital());
                    if (f.tasa() != null) escribirNumero(r.createCell(2), f.tasa());
                    if (f.interes().signum() != 0) escribirNumero(r.createCell(3), f.interes());
                    escribirNumero(r.createCell(4), f.acumulado());
                }
                Row total = hoja.createRow(i);
                Cell etiqueta = total.createCell(0);
                etiqueta.setCellValue("TOTAL");
                etiqueta.setCellStyle(estiloTotal);
                escribirNumero(total.createCell(1), tabla.totalCapital());
                escribirNumero(total.createCell(3), tabla.totalInteres());
                escribirNumero(total.createCell(4), tabla.total());
                for (int c : new int[]{1, 3, 4}) total.getCell(c).setCellStyle(estiloTotal);
            }
            libro.write(salida);
            libro.dispose();
        }
    }

    /** Nombre de hoja válido para Excel (máx. 31 caracteres, sin : \ / ? * [ ]) y sin repetir. */
    private static String nombreHojaUnico(String nombre, Set<String> usados) {
        String base = (nombre == null || nombre.isBlank() ? "Tarifa" : nombre).replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        if (base.length() > 28) base = base.substring(0, 28).trim();
        String candidato = base;
        for (int n = 2; usados.contains(candidato.toLowerCase()); n++) candidato = base + " " + n;
        usados.add(candidato.toLowerCase());
        return candidato;
    }

    /** Escribe el Excel en streaming (soporta cientos de miles de filas sin cargarlas en memoria). */
    public void escribirFacturacion(Long maestroId, OutputStream salida) throws IOException {
        buscarMaestro(maestroId); // valida que exista
        try (SXSSFWorkbook libro = new SXSSFWorkbook(500)) {
            libro.setCompressTempFiles(true);
            SXSSFSheet hoja = libro.createSheet("Facturacion devolucion");

            Font negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);

            Row encabezado = hoja.createRow(0);
            String[] columnas = {"codigo_dataease", "valor_devolucion", "interes"};
            for (int c = 0; c < columnas.length; c++) {
                Cell celda = encabezado.createCell(c);
                celda.setCellValue(columnas[c]);
                celda.setCellStyle(estiloEncabezado);
            }
            hoja.createFreezePane(0, 1);
            hoja.setColumnWidth(0, 18 * 256);
            hoja.setColumnWidth(1, 20 * 256);
            hoja.setColumnWidth(2, 20 * 256);

            int[] fila = {1};
            jdbcTemplate.query(con -> {
                var ps = con.prepareStatement(SQL_FACTURACION);
                ps.setFetchSize(5000);
                ps.setLong(1, maestroId);
                return ps;
            }, rs -> {
                Row r = hoja.createRow(fila[0]++);
                r.createCell(0).setCellValue(rs.getString("codigo_dataease"));
                escribirNumero(r.createCell(1), rs.getBigDecimal("valor_devolucion"));
                escribirNumero(r.createCell(2), rs.getBigDecimal("interes"));
            });

            libro.write(salida);
            libro.dispose();
            log.info("Reporte facturación devolución {}: {} usuarios", maestroId, fila[0] - 1);
        }
    }

    /** Valor numérico completo (General muestra todos los decimales significativos). */
    private static void escribirNumero(Cell celda, BigDecimal valor) {
        celda.setCellValue(valor == null ? 0d : valor.doubleValue());
    }

    private Map<String, Object> buscarMaestro(Long maestroId) {
        var filas = jdbcTemplate.queryForList(
                "SELECT id, nombre_devolucion, fecha_calculo FROM devolucion_maestro " +
                "WHERE id = ? AND NOT COALESCE(es_demo, false)", maestroId);
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("La devolución " + maestroId + " no existe.");
        }
        return new java.util.LinkedHashMap<>(filas.get(0));
    }
}
