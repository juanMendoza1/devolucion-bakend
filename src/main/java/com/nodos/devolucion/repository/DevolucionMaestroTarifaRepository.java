package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.DevolucionMaestroTarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionMaestroTarifaRepository extends JpaRepository<DevolucionMaestroTarifa, Long> {
    
    List<DevolucionMaestroTarifa> findByDevolucionMaestroId(Long maestroId);
    
    Optional<DevolucionMaestroTarifa> findByDevolucionMaestroIdAndJobId(Long maestroId, String jobId);
}