package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.FacturaOriginal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FacturaOriginalRepository extends JpaRepository<FacturaOriginal, Long> {
    
    // 1. Ver todas las facturas de un lote de importación específico
    Page<FacturaOriginal> findByJobId(String jobId, Pageable pageable);

    // 2. Buscar por número de factura (con LIKE %factura% e ignorando mayúsculas)
    Page<FacturaOriginal> findByFacturaContainingIgnoreCase(String factura, Pageable pageable);

    // 3. Buscar por código de usuario (Dataease)
    Page<FacturaOriginal> findByCodigoDataeaseContainingIgnoreCase(String codigoDataease, Pageable pageable);
    
    @Query("SELECT f FROM FacturaOriginal f WHERE CONCAT(f.ano, '-', f.mes) IN :periodos")
    List<FacturaOriginal> buscarPorPeriodos(@Param("periodos") List<String> periodos);
}