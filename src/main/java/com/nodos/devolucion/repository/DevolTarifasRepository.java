package com.nodos.devolucion.repository;

import com.nodos.devolucion.dto.LoteTarifasDTO;
import com.nodos.devolucion.entity.DevolTarifas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevolTarifasRepository extends JpaRepository<DevolTarifas, Long> {

    /**
     * Resumen por lote/periodo. LEFT JOIN con la versión para no perder lotes sin cabecera,
     * y conteo de tarifas con al menos un vínculo ACTIVO a un aprovechador.
     */
    @Query("SELECT new com.nodos.devolucion.dto.LoteTarifasDTO(" +
            "t.jobId, t.mes, t.anio, v.novedad, COUNT(DISTINCT t.id), COUNT(DISTINCT ta.tarifa.id)) " +
            "FROM DevolTarifas t " +
            "LEFT JOIN VersionTarifa v ON t.jobId = v.jobId " +
            "LEFT JOIN TarifaAprovechador ta ON ta.tarifa = t AND ta.activo = true " +
            "WHERE t.jobId NOT IN (SELECT dmt.jobId FROM DevolucionMaestroTarifa dmt WHERE dmt.estado = 'COMPLETADO') " +
            "GROUP BY t.jobId, t.mes, t.anio, v.novedad " +
            "ORDER BY t.anio DESC, t.mes DESC")
    List<LoteTarifasDTO> obtenerResumenLotes();

    org.springframework.data.domain.Page<DevolTarifas> findByJobId(String jobId, org.springframework.data.domain.Pageable pageable);

    // Spring Data crea automáticamente el "WHERE job_id = ? AND mes = ? AND anio = ?"
    org.springframework.data.domain.Page<DevolTarifas> findByJobIdAndMesAndAnio(String jobId, Integer mes, Integer anio, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT t.id FROM DevolTarifas t WHERE t.jobId = :jobId AND t.mes = :mes AND t.anio = :anio")
    List<Long> findIdsByLote(@Param("jobId") String jobId, @Param("mes") Integer mes, @Param("anio") Integer anio);

    List<DevolTarifas> findByJobId(String jobId);

    @Query("SELECT DISTINCT d.concepto FROM DevolTarifas d WHERE d.concepto IS NOT NULL")
    List<String> findDistinctConceptos();

    /** Resumen de vínculos ACTIVOS por lote y aprovechador, con el rango de fechas de asignación. */
    @Query("SELECT new com.nodos.devolucion.dto.AsignacionResumenDTO(" +
            "t.jobId, COALESCE(v.novedad, 'Sin Novedad'), t.mes, t.anio, " +
            "a.id, a.nit, a.nombreRazonSocial, COUNT(t), MIN(ta.fechaAsignacion), MAX(ta.fechaAsignacion)) " +
            "FROM TarifaAprovechador ta " +
            "JOIN ta.tarifa t " +
            "JOIN ta.aprovechador a " +
            "LEFT JOIN VersionTarifa v ON t.jobId = v.jobId " +
            "WHERE ta.activo = true " +
            "GROUP BY t.jobId, v.novedad, t.mes, t.anio, a.id, a.nit, a.nombreRazonSocial " +
            "ORDER BY t.anio DESC, t.mes DESC, a.nombreRazonSocial ASC")
    List<com.nodos.devolucion.dto.AsignacionResumenDTO> obtenerResumenAsignaciones();
}
