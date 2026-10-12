package com.nodos.devolucion.repository;

import com.nodos.devolucion.dto.AuditoriaDTO;
import com.nodos.devolucion.entity.AuditoriaAsignacion;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditoriaAsignacionRepository extends JpaRepository<AuditoriaAsignacion, Long> {
	@Query("SELECT new com.nodos.devolucion.dto.AuditoriaDTO(" +
	           "a.id, a.usuarioEmail, a.fechaAsignacion, a.cantidadTarifas, " +
	           "ap.nit, ap.nombreRazonSocial, a.detallesAdicionales, " +
	           "COALESCE(a.accion, 'ASIGNAR'), a.jobId, v.novedad, a.mes, a.anio) " +
	           "FROM AuditoriaAsignacion a JOIN a.aprovechador ap " +
	           "LEFT JOIN VersionTarifa v ON v.jobId = a.jobId " +
	           "ORDER BY a.fechaAsignacion DESC")
	    List<AuditoriaDTO> obtenerHistorial();
}
