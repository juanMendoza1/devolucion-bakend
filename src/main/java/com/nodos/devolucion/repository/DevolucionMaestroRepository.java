package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.DevolucionMaestro;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionMaestroRepository extends JpaRepository<DevolucionMaestro, Long> {
    Optional<DevolucionMaestro> findByNombreDevolucion(String nombreDevolucion);
    
    @Transactional
    @Modifying
    void deleteByNombreDevolucion(String nombreDevolucion);
    
    List<DevolucionMaestro> findAllByOrderByFechaCalculoDesc();
}