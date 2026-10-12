package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.AsignacionGlobalDTO;
import com.nodos.devolucion.dto.AsignacionTarifaRequest;
import com.nodos.devolucion.dto.TarifaAsignadaDTO;
import com.nodos.devolucion.entity.Aprovechador;
import com.nodos.devolucion.entity.AuditoriaAsignacion;
import com.nodos.devolucion.entity.DevolTarifas;
import com.nodos.devolucion.entity.TarifaAprovechador;
import com.nodos.devolucion.repository.AprovechadorRepository;
import com.nodos.devolucion.repository.AuditoriaAsignacionRepository;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import com.nodos.devolucion.repository.TarifaAprovechadorRepository;
import com.nodos.devolucion.repository.VersionTarifaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TarifaAsignacionService {

    public static final String ACCION_ASIGNAR = "ASIGNAR";
    public static final String ACCION_RETIRAR = "RETIRAR";

    private final AprovechadorRepository aprovechadorRepository;
    private final DevolTarifasRepository devolTarifasRepository;
    private final AuditoriaAsignacionRepository auditoriaRepository;
    private final TarifaAprovechadorRepository tarifaAprovechadorRepository;
    private final VersionTarifaRepository versionTarifaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Aprovechador> obtenerAprovechadoresActivos() {
        return aprovechadorRepository.findByEstadoTrue();
    }

    @Transactional
    public int asignarAprovechador(AsignacionTarifaRequest request) {
        return asignarAprovechadorConAuditoria(request, "Asignación desde panel Maestro-Detalle");
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DevolTarifas> obtenerTarifas(int page, int size) {
        return devolTarifasRepository.findAll(org.springframework.data.domain.PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<com.nodos.devolucion.dto.LoteTarifasDTO> obtenerResumenLotes() {
        return devolTarifasRepository.obtenerResumenLotes();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DevolTarifas> obtenerTarifasPorLote(String jobId, int page, int size) {
        return devolTarifasRepository.findByJobId(jobId, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<com.nodos.devolucion.dto.AuditoriaDTO> obtenerHistorialAuditoria() {
        return auditoriaRepository.obtenerHistorial();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DevolTarifas> obtenerTarifasPorLote(String jobId, Integer mes, Integer anio, int page, int size) {
        return devolTarifasRepository.findByJobIdAndMesAndAnio(
            jobId, mes, anio,
            org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("estrato", "id"))
        );
    }

    @Transactional
    public Map<String, Object> asignarLoteCompleto(AsignacionGlobalDTO dto) {
        List<Long> todosLosIds = devolTarifasRepository.findIdsByLote(dto.getJobId(), dto.getMes(), dto.getAnio());
        if (todosLosIds.isEmpty()) {
            throw new IllegalArgumentException("No hay tarifas para este periodo.");
        }

        String nombreImportacion = versionTarifaRepository.findByJobId(dto.getJobId())
                .map(v -> v.getNovedad())
                .orElse("Tarifas " + dto.getMes() + "/" + dto.getAnio());

        AsignacionTarifaRequest asignacionNormal = new AsignacionTarifaRequest(
            List.of(dto.getAprovechadorId()),
            todosLosIds
        );

        int cantidad = asignarAprovechadorConAuditoria(asignacionNormal, "Lote completo: " + nombreImportacion);

        return Map.of(
            "mensaje", "Asignación masiva exitosa",
            "registrosActualizados", cantidad
        );
    }

    /**
     * Agrega vínculos tarifa-aprovechador SIN reemplazar los existentes.
     * Si el vínculo ya está activo se omite. Cada aprovechador con vínculos nuevos
     * genera un evento en auditoria_asignacion (ASIGNAR) y cada vínculo guarda fecha y usuario.
     *
     * @return cantidad de vínculos nuevos creados
     */
    private int asignarAprovechadorConAuditoria(AsignacionTarifaRequest request, String detalleAuditoria) {
        validarRequest(request);

        List<Aprovechador> asociaciones = buscarAprovechadores(request.aprovechadorIds());
        List<DevolTarifas> tarifas = buscarTarifas(request.tarifasIds());

        Set<String> yaActivos = new HashSet<>();
        for (TarifaAprovechador ta : tarifaAprovechadorRepository.findActivos(request.tarifasIds(), request.aprovechadorIds())) {
            yaActivos.add(clave(ta.getTarifa().getId(), ta.getAprovechador().getId()));
        }

        String usuarioActual = obtenerUsuarioActual();
        LocalDateTime ahora = LocalDateTime.now();
        int totalNuevos = 0;

        for (Aprovechador aprovechador : asociaciones) {
            List<DevolTarifas> pendientes = tarifas.stream()
                    .filter(t -> !yaActivos.contains(clave(t.getId(), aprovechador.getId())))
                    .toList();
            if (pendientes.isEmpty()) {
                continue;
            }

            AuditoriaAsignacion evento = auditoriaRepository.save(
                    construirEvento(ACCION_ASIGNAR, aprovechador, pendientes, usuarioActual, ahora, detalleAuditoria));

            List<TarifaAprovechador> nuevos = new ArrayList<>();
            for (DevolTarifas tarifa : pendientes) {
                nuevos.add(TarifaAprovechador.builder()
                        .tarifa(tarifa)
                        .aprovechador(aprovechador)
                        .fechaAsignacion(ahora)
                        .usuarioAsignacion(usuarioActual)
                        .activo(true)
                        .auditoriaAsignacionId(evento.getId())
                        .build());
            }
            tarifaAprovechadorRepository.saveAll(nuevos);
            totalNuevos += nuevos.size();
        }

        log.info("✅ {} vínculos nuevos ({} tarifas x {} asociaciones solicitadas)", totalNuevos, tarifas.size(), asociaciones.size());
        return totalNuevos;
    }

    /**
     * Retira (desactiva) vínculos activos. No borra: deja fecha y usuario de retiro
     * y registra un evento RETIRAR por aprovechador.
     */
    @Transactional
    public Map<String, Object> retirarAprovechador(AsignacionTarifaRequest request) {
        validarRequest(request);

        List<TarifaAprovechador> activos = tarifaAprovechadorRepository.findActivos(request.tarifasIds(), request.aprovechadorIds());
        if (activos.isEmpty()) {
            return Map.of("mensaje", "No había vínculos activos para retirar.", "registrosActualizados", 0);
        }

        String usuarioActual = obtenerUsuarioActual();
        LocalDateTime ahora = LocalDateTime.now();

        for (Aprovechador aprovechador : buscarAprovechadores(request.aprovechadorIds())) {
            List<TarifaAprovechador> delAprovechador = activos.stream()
                    .filter(ta -> ta.getAprovechador().getId().equals(aprovechador.getId()))
                    .toList();
            if (delAprovechador.isEmpty()) {
                continue;
            }
            List<DevolTarifas> tarifas = delAprovechador.stream().map(TarifaAprovechador::getTarifa).toList();
            auditoriaRepository.save(construirEvento(ACCION_RETIRAR, aprovechador, tarifas, usuarioActual, ahora,
                    "Retiro desde panel Maestro-Detalle"));

            for (TarifaAprovechador ta : delAprovechador) {
                ta.setActivo(false);
                ta.setFechaRetiro(ahora);
                ta.setUsuarioRetiro(usuarioActual);
            }
        }
        tarifaAprovechadorRepository.saveAll(activos);

        log.info("↩️ {} vínculos retirados", activos.size());
        return Map.of("mensaje", "Retiro exitoso", "registrosActualizados", activos.size());
    }

    /**
     * Reset de fábrica (solo admin). TRUNCATE en un único comando para respetar las FK
     * entre tablas y no recorrer fila por fila los millones de reliquidaciones.
     * Conserva factura_original, aprovechadores, tasas_interes, usuarios y auditoria_importacion.
     */
    @Transactional
    public void resetDeFabrica() {
        log.warn("ATENCIÓN: Reset de Fábrica solicitado por '{}' (conservando Facturas Originales)", obtenerUsuarioActual());
        jdbcTemplate.execute("TRUNCATE TABLE reliquidaciones, devolucion_maestro_tarifas, devolucion_maestro, " +
                "tarifa_aprovechador, auditoria_asignacion, versiones_tarifas, devol_tarifas");
        log.info("Reset de fábrica completado con éxito.");
    }

    @Transactional(readOnly = true)
    public List<com.nodos.devolucion.dto.AsignacionResumenDTO> obtenerResumenAsignaciones() {
        return devolTarifasRepository.obtenerResumenAsignaciones();
    }

    @Transactional(readOnly = true)
    public List<TarifaAsignadaDTO> obtenerDetalleAsignacion(String jobId, Long aprovechadorId) {
        return tarifaAprovechadorRepository.findDetalleAsignacion(jobId, aprovechadorId);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void validarRequest(AsignacionTarifaRequest request) {
        if (request.tarifasIds() == null || request.tarifasIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una tarifa.");
        }
        if (request.aprovechadorIds() == null || request.aprovechadorIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una asociación.");
        }
    }

    private List<Aprovechador> buscarAprovechadores(List<Long> ids) {
        List<Aprovechador> asociaciones = aprovechadorRepository.findAllById(ids);
        if (asociaciones.size() != new HashSet<>(ids).size()) {
            throw new IllegalArgumentException("Alguna de las asociaciones seleccionadas no existe.");
        }
        return asociaciones;
    }

    private List<DevolTarifas> buscarTarifas(List<Long> ids) {
        List<DevolTarifas> tarifas = devolTarifasRepository.findAllById(ids);
        if (tarifas.isEmpty()) {
            throw new IllegalArgumentException("Las tarifas seleccionadas no existen.");
        }
        return tarifas;
    }

    /** Evento de auditoría. Lote/mes/año solo se llenan si todas las tarifas son del mismo lote y periodo. */
    private AuditoriaAsignacion construirEvento(String accion, Aprovechador aprovechador, List<DevolTarifas> tarifas,
                                                String usuario, LocalDateTime fecha, String detalle) {
        DevolTarifas primera = tarifas.get(0);
        boolean mismoLote = tarifas.stream().allMatch(t -> java.util.Objects.equals(t.getJobId(), primera.getJobId()));
        boolean mismoPeriodo = mismoLote && tarifas.stream().allMatch(t ->
                java.util.Objects.equals(t.getMes(), primera.getMes()) && java.util.Objects.equals(t.getAnio(), primera.getAnio()));

        return AuditoriaAsignacion.builder()
                .usuarioEmail(usuario)
                .fechaAsignacion(fecha)
                .cantidadTarifas(tarifas.size())
                .aprovechador(aprovechador)
                .detallesAdicionales(detalle)
                .accion(accion)
                .jobId(mismoLote ? primera.getJobId() : null)
                .mes(mismoPeriodo ? primera.getMes() : null)
                .anio(mismoPeriodo ? primera.getAnio() : null)
                .build();
    }

    private static String clave(Long tarifaId, Long aprovechadorId) {
        return tarifaId + "-" + aprovechadorId;
    }

    private String obtenerUsuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "Sistema";
    }
}
