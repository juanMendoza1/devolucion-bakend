package com.nodos.devolucion.repository;

import com.nodos.devolucion.dto.LoteTarifasDTO;
import com.nodos.devolucion.entity.Aprovechador;
import com.nodos.devolucion.entity.DevolTarifas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevolTarifasRepository extends JpaRepository<DevolTarifas, Long> {    

    @Modifying
    @Query("UPDATE DevolTarifas t SET t.aprovechador = :aprovechador WHERE t.id IN :tarifasIds")
    int asignarAprovechadorMasivo(
            @Param("aprovechador") Aprovechador aprovechador, 
            @Param("tarifasIds") List<Long> tarifasIds
    );
    
    @Query("SELECT new com.nodos.devolucion.dto.LoteTarifasDTO(" +
            "t.jobId, t.mes, t.anio, v.novedad, COUNT(t), " +
            "CAST(SUM(CASE WHEN t.aprovechador IS NOT NULL THEN 1 ELSE 0 END) AS java.lang.Long), " +
            "CAST(SUM(CASE WHEN t.aprovechador IS NULL THEN 1 ELSE 0 END) AS java.lang.Long)) " +
            "FROM DevolTarifas t " +
            "JOIN VersionTarifa v ON t.jobId = v.jobId " +
            "GROUP BY t.jobId, t.mes, t.anio, v.novedad " +
            "ORDER BY t.anio DESC, t.mes DESC")
     List<LoteTarifasDTO> obtenerResumenLotes();
    
 
    org.springframework.data.domain.Page<DevolTarifas> findByJobId(String jobId, org.springframework.data.domain.Pageable pageable);
    
 // Spring Data crea automáticamente el "WHERE job_id = ? AND mes = ? AND anio = ?"
    org.springframework.data.domain.Page<DevolTarifas> findByJobIdAndMesAndAnio(String jobId, Integer mes, Integer anio, org.springframework.data.domain.Pageable pageable);
    
    @Query("SELECT t.id FROM DevolTarifas t WHERE t.jobId = :jobId AND t.mes = :mes AND t.anio = :anio")
    List<Long> findIdsByLote(String jobId, Integer mes, Integer anio);
}