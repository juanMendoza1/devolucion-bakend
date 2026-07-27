package com.nodos.devolucion.service.strategy;

import org.springframework.web.multipart.MultipartFile;

public interface CsvImportStrategy {
    String getTipoArchivo();
    
    // Agregamos el parámetro usuIdRegistro
    void procesarArchivo(MultipartFile file, String jobId, String usuIdRegistro) throws Exception; 
}