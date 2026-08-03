package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.Aprovechador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AprovechadorRepository extends JpaRepository<Aprovechador, Long> {
    // Para listar en el frontend solo los que están activos y poder asignarlos
    List<Aprovechador> findByEstadoTrue();
}
