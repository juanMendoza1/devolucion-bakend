package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.AuditoriaImportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuditoriaImportacionRepository extends JpaRepository<AuditoriaImportacion, Long> {
    
    // Nos servirá para actualizar el estado del lote cuando termine
    Optional<AuditoriaImportacion> findByJobId(String jobId); 
}