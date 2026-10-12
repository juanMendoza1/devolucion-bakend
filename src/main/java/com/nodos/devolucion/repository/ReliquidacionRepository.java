package com.nodos.devolucion.repository;

import com.nodos.devolucion.dto.ConsolidadoMesDTO;
import com.nodos.devolucion.dto.HistorialCodigoDTO;
import com.nodos.devolucion.entity.Reliquidacion;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReliquidacionRepository extends JpaRepository<Reliquidacion, Long> {    
    
    void deleteByJobIdAndConcepto(String jobId, String concepto);

    void deleteByConsolidadoTarifasAndConcepto(String consolidadoTarifas, String concepto);
    
    List<Reliquidacion> findTop50ByConsolidadoTarifasOrderByIdDesc(String consolidadoTarifas);
    
    @Transactional
    @Modifying
    void deleteByConsolidadoTarifas(String consolidadoTarifas);
    
    @Query("SELECT new com.nodos.devolucion.dto.ConsolidadoMesDTO(" +
            "t.nombreTarifa, r.mesBase, r.anioBase, SUM(r.capitalReliquidado), SUM(r.interesesCalculados), SUM(r.totalDevolucion)) " +
            "FROM Reliquidacion r " +
            "JOIN DevolucionMaestroTarifa t ON r.devolucionMaestro.id = t.devolucionMaestro.id AND r.jobId = t.jobId " +
            "WHERE r.devolucionMaestro.id = :maestroId " +
            "GROUP BY t.nombreTarifa, r.anioBase, r.mesBase " +
            "ORDER BY t.nombreTarifa ASC, r.anioBase ASC, r.mesBase ASC")
     List<ConsolidadoMesDTO> obtenerDetalleMensualPorMaestro(@Param("maestroId") Long maestroId);
    
    
    @Transactional
    @Modifying
    @Query("DELETE FROM Reliquidacion r WHERE r.devolucionMaestro.id = :maestroId AND r.jobId = :jobId")
    void deleteByMaestroIdAndJobId(@Param("maestroId") Long maestroId, @Param("jobId") String jobId);    

     
     @Query("SELECT new com.nodos.devolucion.dto.HistorialCodigoDTO(" +
             "m.nombreDevolucion, t.nombreTarifa, m.fechaCalculo, r.concepto, r.mesBase, r.anioBase, " +
             "r.capitalReliquidado, r.interesesCalculados, r.totalDevolucion) " +
             "FROM Reliquidacion r " +
             "JOIN r.devolucionMaestro m " +
             "JOIN DevolucionMaestroTarifa t ON m.id = t.devolucionMaestro.id AND r.jobId = t.jobId " +
             "WHERE r.codigoDataease = :codigoDataease " +
             "ORDER BY m.fechaCalculo DESC, t.nombreTarifa ASC, r.anioBase DESC, r.mesBase DESC")
      List<HistorialCodigoDTO> obtenerHistorialPorCodigo(@Param("codigoDataease") String codigoDataease);
    

     
     



}