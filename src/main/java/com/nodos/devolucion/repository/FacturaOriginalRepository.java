package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.FacturaOriginal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface FacturaOriginalRepository extends JpaRepository<FacturaOriginal, Long> {
    
    @Query("SELECT DISTINCT new map(f.ano as anio, f.mes as mes) FROM FacturaOriginal f ORDER BY f.ano DESC, f.mes DESC")
    List<Map<String, Integer>> findDistinctPeriodos();

    @Query("SELECT f FROM FacturaOriginal f WHERE " +
            "(:factura = '' OR LOWER(f.factura) LIKE LOWER(CONCAT('%', :factura, '%'))) AND " +
            "(:codigo = '' OR LOWER(f.codigoDataease) LIKE LOWER(CONCAT('%', :codigo, '%'))) AND " +
            "(:mes IS NULL OR f.mes = :mes) AND " +
            "(:anio IS NULL OR f.ano = :anio)")
     Page<FacturaOriginal> buscarConFiltros(@Param("factura") String factura, 
                                            @Param("codigo") String codigo, 
                                            @Param("mes") Integer mes, 
                                            @Param("anio") Integer anio, 
                                            Pageable pageable);
    
    @Query("SELECT f FROM FacturaOriginal f WHERE CONCAT(f.ano, '-', f.mes) IN :periodos")
    List<FacturaOriginal> buscarPorPeriodos(@Param("periodos") List<String> periodos);
    
    @Query("SELECT DISTINCT f.jobId FROM FacturaOriginal f WHERE f.jobId IS NOT NULL")
    List<String> findDistinctJobIds();
    
    @Query("SELECT DISTINCT f.codigoDataease FROM FacturaOriginal f WHERE f.ano = :ano AND f.mes = :mes")
    List<String> findDistinctUsuariosPorPeriodo(@Param("ano") Integer ano, @Param("mes") Integer mes);
    
    @Query("SELECT f FROM FacturaOriginal f WHERE CONCAT(f.ano, '-', f.mes) IN :periodos AND f.codigoDataease IN :codigos")
    List<FacturaOriginal> buscarPorPeriodosYUsuarios(
            @Param("periodos") List<String> periodos, 
            @Param("codigos") List<String> codigos
    );
}