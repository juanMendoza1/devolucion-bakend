package com.nodos.devolucion.service.strategy;

import java.io.File; // <--- Importante

public interface CsvImportStrategy {
    String getTipoArchivo();
    
    // Cambiamos MultipartFile por File
    void procesarArchivo(File file, String jobId, String usuIdRegistro, String novedad, String version) throws Exception; 
}