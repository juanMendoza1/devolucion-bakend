package com.nodos.devolucion.controller;

import com.nodos.devolucion.service.strategy.CsvImportStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/importaciones")
public class ImportacionController {

    private final List<CsvImportStrategy> strategies;
    private final SimpMessagingTemplate messagingTemplate;

    public ImportacionController(List<CsvImportStrategy> strategies, SimpMessagingTemplate messagingTemplate) {
        this.strategies = strategies;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/{tipoArchivo}")
    public ResponseEntity<?> importarArchivo(
            @PathVariable String tipoArchivo, 
            @RequestParam("file") MultipartFile file,
            @RequestParam("usuIdRegistro") String usuIdRegistro,
            @RequestParam(value = "novedad", required = false) String novedad,
            @RequestParam(value = "version", required = false) String version) {
        
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío."));
        }

        CsvImportStrategy strategy = strategies.stream()
                .filter(s -> s.getTipoArchivo().equalsIgnoreCase(tipoArchivo))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de archivo no soportado."));
        }

        String jobId = UUID.randomUUID().toString();

        // 1. CREAR ARCHIVO TEMPORAL PROPIO
        File tempFile;
        try {
            Path tempPath = Files.createTempFile("import_" + jobId, ".csv");
            tempFile = tempPath.toFile();
            file.transferTo(tempFile); 
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al procesar el archivo temporal: " + e.getMessage()));
        }

        // 2. PROCESAMIENTO ASÍNCRONO
        CompletableFuture.runAsync(() -> {
            try {
                // 🔥 SOLUCIÓN A LA CONDICIÓN DE CARRERA:
                // Le damos 1.5 segundos al Frontend para que establezca la conexión WebSocket
                // antes de empezar a procesar y emitir mensajes.
                Thread.sleep(1500);
                
                strategy.procesarArchivo(tempFile, jobId, usuIdRegistro, novedad, version); 
            } catch (Exception e) {
                messagingTemplate.convertAndSend(
                    "/topic/import-progress/" + jobId, 
                    Map.of("porcentaje", 0, "mensaje", "ERROR CRÍTICO: " + e.getMessage())
                );
                e.printStackTrace();
            } finally {
                // 3. LIMPIEZA OBLIGATORIA
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });

        return ResponseEntity.ok(Map.of(
            "mensaje", "Archivo encolado para procesamiento.",
            "jobId", jobId
        ));
    }
}