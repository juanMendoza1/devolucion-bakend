package com.nodos.devolucion.service;

import com.nodos.devolucion.dto.TasaInteresDTO;
import com.nodos.devolucion.entity.TasaInteres;
import com.nodos.devolucion.repository.TasaInteresRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TasaInteresService {

    private final TasaInteresRepository repository;

    @Transactional(readOnly = true)
    public Page<TasaInteres> obtenerTodas(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional
    public TasaInteres crear(TasaInteresDTO dto) {
        if (repository.existsByMesAndAno(dto.getMes(), dto.getAno())) {
            throw new IllegalArgumentException("Ya existe una tasa registrada para el periodo " + dto.getMes() + "/" + dto.getAno());
        }

        TasaInteres tasa = TasaInteres.builder()
                .mes(dto.getMes())
                .ano(dto.getAno())
                .tsi(dto.getTsi())
                .build();

        return repository.save(tasa);
    }

    @Transactional
    public TasaInteres actualizar(Long id, TasaInteresDTO dto) {
        TasaInteres tasa = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La tasa no existe"));

        if (repository.existsByMesAndAnoAndIdNot(dto.getMes(), dto.getAno(), id)) {
            throw new IllegalArgumentException("Ya existe otra tasa para el periodo " + dto.getMes() + "/" + dto.getAno());
        }

        tasa.setMes(dto.getMes());
        tasa.setAno(dto.getAno());
        tasa.setTsi(dto.getTsi());

        return repository.save(tasa);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("La tasa no existe");
        }
        repository.deleteById(id);
    }
}