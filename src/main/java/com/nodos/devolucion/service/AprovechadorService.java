package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.AprovechadorDTO;
import com.nodos.devolucion.entity.Aprovechador;
import com.nodos.devolucion.repository.AprovechadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Catálogo de aprovechadores (asociaciones). Reglas:
 * - NIT y nombre obligatorios; el NIT no se repite.
 * - Desactivar lo saca de la lista de asignación, pero conserva su historial.
 * - Solo se puede eliminar si nunca se usó (sin asignaciones, auditoría ni reliquidaciones).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AprovechadorService {

    private final AprovechadorRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public List<AprovechadorDTO> listar() {
        return repository.findAllByOrderByIdAsc().stream().map(AprovechadorDTO::de).toList();
    }

    public List<AprovechadorDTO> listarActivos() {
        return repository.findByEstadoTrueOrderByNombreRazonSocialAsc().stream().map(AprovechadorDTO::de).toList();
    }

    @Transactional
    public AprovechadorDTO crear(AprovechadorDTO dto) {
        String nit = normalizarNit(dto.nit());
        String nombre = normalizarNombre(dto.nombre());
        if (repository.existsByNit(nit)) {
            throw new IllegalArgumentException("Ya existe un aprovechador con el NIT " + nit + ".");
        }
        Aprovechador nuevo = repository.save(Aprovechador.builder()
                .nit(nit)
                .nombreRazonSocial(nombre)
                .estado(true)
                .build());
        log.info("Aprovechador creado: {} - {}", nuevo.getNit(), nuevo.getNombreRazonSocial());
        return AprovechadorDTO.de(nuevo);
    }

    @Transactional
    public AprovechadorDTO actualizar(Long id, AprovechadorDTO dto) {
        Aprovechador existente = buscar(id);
        String nit = normalizarNit(dto.nit());
        if (repository.existsByNitAndIdNot(nit, id)) {
            throw new IllegalArgumentException("Ya existe otro aprovechador con el NIT " + nit + ".");
        }
        existente.setNit(nit);
        existente.setNombreRazonSocial(normalizarNombre(dto.nombre()));
        return AprovechadorDTO.de(repository.save(existente));
    }

    @Transactional
    public AprovechadorDTO cambiarEstado(Long id, boolean activo) {
        Aprovechador existente = buscar(id);
        existente.setEstado(activo);
        return AprovechadorDTO.de(repository.save(existente));
    }

    @Transactional
    public void eliminar(Long id) {
        Aprovechador existente = buscar(id);
        Long usos = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM tarifa_aprovechador WHERE aprovechador_id = ?) " +
                "     + (SELECT COUNT(*) FROM auditoria_asignacion WHERE aprovechador_id = ?) " +
                "     + (SELECT COUNT(*) FROM (SELECT 1 FROM reliquidaciones WHERE aprovechador_id = ? LIMIT 1) r)",
                Long.class, id, id, id);
        if (usos != null && usos > 0) {
            throw new IllegalArgumentException("El aprovechador " + existente.getNombreRazonSocial()
                    + " ya tiene asignaciones o historial. Desactívelo en lugar de eliminarlo.");
        }
        repository.delete(existente);
        log.info("Aprovechador eliminado: {} - {}", existente.getNit(), existente.getNombreRazonSocial());
    }

    /**
     * Estadísticas para el panel general: por aprovechador (vínculos activos) tarifas asignadas y liquidadas,
     * usuarios beneficiados, capital, intereses y total; más la fila "Sin aprovechador" para lo liquidado
     * sin asociación, y los totales globales de las devoluciones en producción.
     * Una tarifa vinculada a varias asociaciones suma en cada una de ellas.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> estadisticas() {
        String porTarifa =
                "WITH por_tarifa AS ( " +
                "  SELECT r.tarifa_aplicada_id AS tarifa_id, " +
                "         COUNT(*) FILTER (WHERE r.capital_reliquidado <> 0) AS usuarios, " +
                "         COALESCE(SUM(r.capital_reliquidado), 0) AS capital, " +
                "         COALESCE(SUM(r.intereses_calculados), 0) AS intereses " +
                "  FROM reliquidaciones r WHERE NOT COALESCE(r.es_demo, false) " +
                "  GROUP BY r.tarifa_aplicada_id ) ";

        List<Map<String, Object>> filas = jdbcTemplate.query(porTarifa +
                "SELECT a.id, a.nit, a.nombre_razon_social AS nombre, COALESCE(a.estado, false) AS activo, " +
                "       COUNT(DISTINCT t.job_id) AS tarifas_asignadas, " +
                "       COUNT(DISTINCT CASE WHEN pt.tarifa_id IS NOT NULL THEN t.job_id END) AS tarifas_liquidadas, " +
                "       COALESCE(SUM(pt.usuarios), 0) AS usuarios, COALESCE(SUM(pt.capital), 0) AS capital, " +
                "       COALESCE(SUM(pt.intereses), 0) AS intereses " +
                "FROM aprovechadores a " +
                "LEFT JOIN tarifa_aprovechador ta ON ta.aprovechador_id = a.id AND ta.activo " +
                "LEFT JOIN devol_tarifas t ON t.id = ta.tarifa_id " +
                "LEFT JOIN por_tarifa pt ON pt.tarifa_id = ta.tarifa_id " +
                "GROUP BY a.id, a.nit, a.nombre_razon_social, a.estado " +
                "UNION ALL " +
                "SELECT NULL, NULL, 'SIN APROVECHADOR', NULL, " +
                "       COUNT(DISTINCT t.job_id), COUNT(DISTINCT t.job_id), " +
                "       COALESCE(SUM(pt.usuarios), 0), COALESCE(SUM(pt.capital), 0), COALESCE(SUM(pt.intereses), 0) " +
                "FROM por_tarifa pt JOIN devol_tarifas t ON t.id = pt.tarifa_id " +
                "WHERE NOT EXISTS (SELECT 1 FROM tarifa_aprovechador x WHERE x.tarifa_id = pt.tarifa_id AND x.activo) " +
                "HAVING COUNT(*) > 0",
                (rs, i) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getObject("id"));
                    m.put("nit", rs.getString("nit"));
                    m.put("nombre", rs.getString("nombre"));
                    m.put("activo", rs.getObject("activo"));
                    m.put("tarifasAsignadas", rs.getLong("tarifas_asignadas"));
                    m.put("tarifasLiquidadas", rs.getLong("tarifas_liquidadas"));
                    m.put("usuarios", rs.getLong("usuarios"));
                    java.math.BigDecimal capital = rs.getBigDecimal("capital");
                    java.math.BigDecimal intereses = rs.getBigDecimal("intereses");
                    m.put("capital", capital);
                    m.put("intereses", intereses);
                    m.put("total", capital.add(intereses));
                    return m;
                });

        Map<String, Object> totales = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS devoluciones, COALESCE(SUM(cantidad_registros), 0) AS usuarios, " +
                "       COALESCE(SUM(total_capital), 0) AS capital, COALESCE(SUM(total_intereses), 0) AS intereses, " +
                "       COALESCE(SUM(total_capital + total_intereses), 0) AS total, " +
                "       (SELECT COUNT(DISTINCT job_id) FROM devol_tarifas) AS tarifas_cargadas, " +
                "       (SELECT COUNT(DISTINCT job_id) FROM devolucion_maestro_tarifas WHERE estado = 'COMPLETADO') AS tarifas_liquidadas " +
                "FROM devolucion_maestro WHERE NOT COALESCE(es_demo, false)");

        return Map.of("aprovechadores", filas, "totales", totales);
    }

    private Aprovechador buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El aprovechador " + id + " no existe."));
    }

    private static String normalizarNit(String nit) {
        if (nit == null || nit.isBlank()) {
            throw new IllegalArgumentException("El NIT es obligatorio.");
        }
        String limpio = nit.trim();
        if (limpio.length() > 20) {
            throw new IllegalArgumentException("El NIT no puede tener más de 20 caracteres.");
        }
        return limpio;
    }

    private static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio.");
        }
        return nombre.trim().toUpperCase();
    }
}
