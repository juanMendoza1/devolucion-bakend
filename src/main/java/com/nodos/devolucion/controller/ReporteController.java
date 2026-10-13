package com.nodos.devolucion.controller;

import com.nodos.devolucion.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    /** Vista previa: cantidad de usuarios y totales que tendrá el reporte. */
    @GetMapping("/facturacion-devolucion/{maestroId}/resumen")
    public ResponseEntity<Map<String, Object>> resumen(@PathVariable Long maestroId) {
        return ResponseEntity.ok(reporteService.resumenFacturacion(maestroId));
    }

    /** Descarga el Excel: codigo_dataease, valor_devolucion, interes (usuarios objeto de la devolución). */
    @GetMapping("/facturacion-devolucion/{maestroId}")
    public ResponseEntity<StreamingResponseBody> descargar(@PathVariable Long maestroId) {
        String archivo = reporteService.nombreArchivo(maestroId);
        return excel(archivo, salida -> reporteService.escribirFacturacion(maestroId, salida));
    }

    /** Vista previa del consolidado mes (mismas tablas que tendrá el Excel). */
    @GetMapping("/consolidado-mes/{maestroId}/resumen")
    public ResponseEntity<List<ReporteService.TablaConsolidado>> consolidadoMesResumen(
            @PathVariable Long maestroId, @RequestParam(defaultValue = "CONSOLIDADO") ReporteService.ModoConsolidado modo) {
        return ResponseEntity.ok(reporteService.consolidadoMes(maestroId, modo));
    }

    /** Excel del consolidado mes: CONSOLIDADO (una hoja) o POR_MES (una hoja por tarifa). */
    @GetMapping("/consolidado-mes/{maestroId}")
    public ResponseEntity<StreamingResponseBody> consolidadoMes(
            @PathVariable Long maestroId, @RequestParam(defaultValue = "CONSOLIDADO") ReporteService.ModoConsolidado modo) {
        String prefijo = modo == ReporteService.ModoConsolidado.POR_MES ? "consolidado_por_mes_" : "consolidado_mes_";
        String archivo = reporteService.nombreArchivo(maestroId, prefijo);
        return excel(archivo, salida -> reporteService.escribirConsolidadoMes(maestroId, modo, salida));
    }

    private static ResponseEntity<StreamingResponseBody> excel(String archivo, StreamingResponseBody cuerpo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(cuerpo);
    }
}
