package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.VersionTarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VersionTarifaRepository extends JpaRepository<VersionTarifa, Long> {
    Optional<VersionTarifa> findByJobId(String jobId);
}