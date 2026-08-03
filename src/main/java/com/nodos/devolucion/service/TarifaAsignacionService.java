package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.AsignacionGlobalDTO;
import com.nodos.devolucion.dto.AsignacionTarifaRequest;
import com.nodos.devolucion.entity.Aprovechador;
import com.nodos.devolucion.entity.AuditoriaAsignacion;
import com.nodos.devolucion.repository.AprovechadorRepository;
import com.nodos.devolucion.repository.AuditoriaAsignacionRepository;
import com.nodos.devolucion.repository.DevolTarifasRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TarifaAsignacionService {

    private final AprovechadorRepository aprovechadorRepository;
    private final DevolTarifasRepository devolTarifasRepository;
    // 👇 Inyectamos el repositorio de auditoría
    private final AuditoriaAsignacionRepository auditoriaRepository;

    /**
     * Devuelve solo los aprovechadores activos para poblar el select del Frontend
     */
    @Transactional(readOnly = true)
    public List<Aprovechador> obtenerAprovechadoresActivos() {
        return aprovechadorRepository.findByEstadoTrue();
    }

    /**
     * Asignación de alto rendimiento usando Bulk Update.
     */
    @Transactional
    public int asignarAprovechador(AsignacionTarifaRequest request) {
        
        if (request.tarifasIds() == null || request.tarifasIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una tarifa para asignar.");
        }

        // 1. Validamos que el aprovechador exista y esté activo
        Aprovechador aprovechador = aprovechadorRepository.findById(request.aprovechadorId())
                .orElseThrow(() -> new IllegalArgumentException("El aprovechador no existe."));
                
        if (!Boolean.TRUE.equals(aprovechador.getEstado())) {
            throw new IllegalArgumentException("No se pueden asignar tarifas a un aprovechador inactivo.");
        }

        // 2. Ejecutamos la actualización masiva
        int registrosActualizados = devolTarifasRepository.asignarAprovechadorMasivo(
                aprovechador, 
                request.tarifasIds()
        );

        // 👇 3. REGISTRO DE AUDITORÍA
        if (registrosActualizados > 0) {
            // Obtenemos el usuario del token JWT (con una validación de seguridad por si acaso)
            String usuarioActual = "Sistema"; 
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                usuarioActual = SecurityContextHolder.getContext().getAuthentication().getName();
            }

            AuditoriaAsignacion auditoria = AuditoriaAsignacion.builder()
                    .usuarioEmail(usuarioActual)
                    .fechaAsignacion(LocalDateTime.now())
                    .cantidadTarifas(registrosActualizados)
                    .aprovechador(aprovechador)
                    .detallesAdicionales("Asignación masiva desde panel Maestro-Detalle")
                    .build();

            auditoriaRepository.save(auditoria);
        }

        log.info("✅ Se asignaron {} tarifas al aprovechador ID: {}", registrosActualizados, aprovechador.getId());
        
        return registrosActualizados;
    }
    
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.nodos.devolucion.entity.DevolTarifas> obtenerTarifas(int page, int size) {
        return devolTarifasRepository.findAll(org.springframework.data.domain.PageRequest.of(page, size));
    }
    
    @Transactional(readOnly = true)
    public List<com.nodos.devolucion.dto.LoteTarifasDTO> obtenerResumenLotes() {
        return devolTarifasRepository.obtenerResumenLotes();
    }
    
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.nodos.devolucion.entity.DevolTarifas> obtenerTarifasPorLote(String jobId, int page, int size) {
        return devolTarifasRepository.findByJobId(
            jobId, 
            org.springframework.data.domain.PageRequest.of(page, size)
        );
    }
    
    @Transactional(readOnly = true)
    public List<com.nodos.devolucion.dto.AuditoriaDTO> obtenerHistorialAuditoria() {
        return auditoriaRepository.obtenerHistorial();
    }
    
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.nodos.devolucion.entity.DevolTarifas> obtenerTarifasPorLote(String jobId, Integer mes, Integer anio, int page, int size) {
        return devolTarifasRepository.findByJobIdAndMesAndAnio(
            jobId, mes, anio, 
            org.springframework.data.domain.PageRequest.of(page, size)
        );
    }
    
    @Transactional
    public Map<String, Object> asignarLoteCompleto(AsignacionGlobalDTO dto) {
        // 1. Buscamos TODOS los IDs que coinciden con este lote y periodo
        List<Long> todosLosIds = devolTarifasRepository.findIdsByLote(dto.getJobId(), dto.getMes(), dto.getAnio());
        
        if (todosLosIds.isEmpty()) {
            throw new RuntimeException("No hay tarifas para este lote y periodo.");
        }

        // 2. Instanciamos tu Record pasando los argumentos directamente al constructor
        // (Asumimos el orden: id del aprovechador, y la lista de tarifas)
        AsignacionTarifaRequest asignacionNormal = new AsignacionTarifaRequest(
            dto.getAprovechadorId(), 
            todosLosIds
        );
        
        // 3. Reutilizamos tu método original para que haga la auditoría y asigne
        int cantidad = asignarAprovechador(asignacionNormal); 
        
        // 4. Retornamos el mismo formato de Map que espera tu Frontend
        return Map.of(
            "mensaje", "Asignación masiva exitosa",
            "registrosActualizados", cantidad
        );
    }
    
}