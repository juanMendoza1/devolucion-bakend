package com.nodos.devolucion.repository;

import com.nodos.devolucion.entity.TasaInteres;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TasaInteresRepository extends JpaRepository<TasaInteres, Long> {
    @Query("SELECT t FROM TasaInteres t WHERE CONCAT(t.ano, '-', t.mes) IN :periodos")
    List<TasaInteres> buscarPorPeriodos(@Param("periodos") List<String> periodos);    

    boolean existsByMesAndAno(Integer mes, Integer ano);   

    boolean existsByMesAndAnoAndIdNot(Integer mes, Integer ano, Long id);
}