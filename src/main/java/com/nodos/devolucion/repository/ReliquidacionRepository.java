package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.Reliquidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReliquidacionRepository extends JpaRepository<Reliquidacion, Long> {
    
    // Nivel Pro: Si el usuario vuelve a ejecutar en Producción un lote que ya había ejecutado,
    // usamos este método para borrar el cálculo anterior y no duplicar datos.
    void deleteByJobIdAndConcepto(String jobId, String concepto);
}