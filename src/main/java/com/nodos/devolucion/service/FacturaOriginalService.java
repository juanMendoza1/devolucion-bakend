package com.nodos.devolucion.service;

import com.nodos.devolucion.entity.FacturaOriginal;
import com.nodos.devolucion.repository.FacturaOriginalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FacturaOriginalService {

    private final FacturaOriginalRepository repository;

    public FacturaOriginalService(FacturaOriginalRepository repository) {
        this.repository = repository;
    }

    // =========================================================
    // MÉTODOS DE CONSULTA Y NEGOCIO TRADICIONALES
    // =========================================================

    @Transactional(readOnly = true)
    public List<FacturaOriginal> obtenerTodas() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<FacturaOriginal> obtenerPorId(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public FacturaOriginal guardar(FacturaOriginal factura) {
        return repository.save(factura);
    }

    @Transactional
    public void eliminarPorId(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void eliminarTodas() {
        repository.deleteAll();
    }
}