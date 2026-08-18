package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.Reliquidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReliquidacionRepository extends JpaRepository<Reliquidacion, Long> {
    
    // Método original: Útil si en algún momento se necesita borrar por lote individual
    void deleteByJobIdAndConcepto(String jobId, String concepto);

    // --- NUEVOS MÉTODOS PARA EL ENFOQUE DE CONSOLIDADO MULTI-LOTE ---

    // Nivel Pro: Si el usuario vuelve a ejecutar una campaña/consolidado completo,
    // borramos el cálculo anterior de ese grupo para no duplicar datos.
    void deleteByConsolidadoTarifasAndConcepto(String consolidadoTarifas, String concepto);

    // Para la inspección parcial "al vuelo" del frontend mediante WebSockets
    List<Reliquidacion> findTop50ByConsolidadoTarifasOrderByIdDesc(String consolidadoTarifas);
}