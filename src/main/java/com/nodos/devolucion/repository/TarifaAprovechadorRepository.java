package com.nodos.devolucion.repository;

import com.nodos.devolucion.dto.TarifaAsignadaDTO;
import com.nodos.devolucion.entity.TarifaAprovechador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TarifaAprovechadorRepository extends JpaRepository<TarifaAprovechador, Long> {

    /** Vínculos activos entre un conjunto de tarifas y un conjunto de aprovechadores. */
    @Query("SELECT ta FROM TarifaAprovechador ta " +
           "WHERE ta.tarifa.id IN :tarifasIds AND ta.aprovechador.id IN :aprovechadorIds AND ta.activo = true")
    List<TarifaAprovechador> findActivos(@Param("tarifasIds") Collection<Long> tarifasIds,
                                         @Param("aprovechadorIds") Collection<Long> aprovechadorIds);

    /** Detalle de tarifas de un lote vinculadas (activas) a un aprovechador, con el momento de asignación. */
    @Query("SELECT new com.nodos.devolucion.dto.TarifaAsignadaDTO(" +
           "t.id, t.mes, t.anio, t.estrato, t.concepto, t.aplicado, t.ajustado, t.devolucion, t.porsubcon, " +
           "t.subcon, t.devolucionAplicar, t.liquidacion, t.esp, ta.fechaAsignacion, ta.usuarioAsignacion) " +
           "FROM TarifaAprovechador ta JOIN ta.tarifa t " +
           "WHERE t.jobId = :jobId AND ta.aprovechador.id = :aprovechadorId AND ta.activo = true " +
           "ORDER BY t.estrato ASC, t.id ASC")
    List<TarifaAsignadaDTO> findDetalleAsignacion(@Param("jobId") String jobId,
                                                  @Param("aprovechadorId") Long aprovechadorId);
}
