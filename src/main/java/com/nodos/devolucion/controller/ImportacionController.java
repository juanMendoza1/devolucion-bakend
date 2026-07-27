package com.nodos.devolucion.controller;

import com.nodos.devolucion.service.strategy.CsvImportStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/importaciones")
@CrossOrigin(origins = "*")
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
            @RequestParam("usuIdRegistro") String usuIdRegistro) { // <--- Nuevo parámetro
        
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

        CompletableFuture.runAsync(() -> {
            try {
                // Pasamos el usuario a la estrategia
                strategy.procesarArchivo(file, jobId, usuIdRegistro); 
            } catch (Exception e) {
                messagingTemplate.convertAndSend(
                    "/topic/import-progress/" + jobId, 
                    Map.of("porcentaje", 0, "mensaje", "ERROR CRÍTICO: " + e.getMessage())
                );
                e.printStackTrace();
            }
        });

        return ResponseEntity.ok(Map.of(
            "mensaje", "Archivo encolado para procesamiento.",
            "jobId", jobId
        ));
    }
}