-- =====================================================================
-- V2: Motor de reliquidación del concepto TA (procedimiento almacenado)
-- Lo invoca ReliquidacionTAStrategy: CALL proc_reliquidar_ta(...)
-- Definición exportada tal cual estaba en devoluciones_db el 2026-10-12.
-- Idempotente (CREATE OR REPLACE). Ejecutar:
--   psql -h localhost -U jlmendoza -d devoluciones_db -f V2__proc_reliquidar_ta.sql
-- =====================================================================

CREATE OR REPLACE PROCEDURE public.proc_reliquidar_ta(IN p_maestro_id bigint, IN p_job_id character varying, IN p_concepto character varying, IN p_tipo_interes character varying, IN p_meses integer[], IN p_anios integer[], IN p_es_demo boolean, IN p_codigos_prueba text[])
 LANGUAGE plpgsql
AS $procedure$
DECLARE
    v_num_periodos INT;
    i INT;
    v_mes_actual INT;
    v_anio_actual INT;
    v_mes_base INT;
    v_anio_base INT;
    v_fecha_calculo TIMESTAMP := NOW();
    v_nombre_devolucion VARCHAR;
    v_tasa_actual NUMERIC;
BEGIN
    v_num_periodos := array_length(p_meses, 1);
    IF v_num_periodos IS NULL OR v_num_periodos = 0 THEN
        RETURN;
    END IF;

    v_mes_base := p_meses[1];
    v_anio_base := p_anios[1];

    SELECT nombre_devolucion INTO v_nombre_devolucion FROM devolucion_maestro WHERE id = p_maestro_id;

    -- 1. Limpiar datos previos
    DELETE FROM reliquidaciones WHERE devolucion_maestro_id = p_maestro_id AND job_id = p_job_id;

    -- 2. Construir la Semilla Inicial
    CREATE TEMP TABLE tmp_kardex ON COMMIT DROP AS
    SELECT DISTINCT ON (f.codigo_dataease)
        f.codigo_dataease,
        f.id AS factura_base_id,
        f.factura AS factura_base_num,
        f.desocupa AS estaba_desocupado,
        t.id AS tarifa_aplicada_id,
        t.devolucion_aplicar AS valor_base_tarifa, 
        
        CASE WHEN f.desocupa THEN 0.0 ELSE t.devolucion_aplicar END AS capital_acumulado, 
        CASE WHEN f.desocupa THEN 0.0 ELSE t.devolucion_aplicar END AS capital_a_reportar, 
        
        FALSE AS empezo_a_pagar,
        t.aplicado, t.ajustado, t.devolucion, t.porsubcon, t.subcon
    FROM factura_original f
    JOIN devol_tarifas t ON t.estrato = f.estrato 
                        AND t.job_id = p_job_id 
                        AND t.concepto = p_concepto
    WHERE f.mes = v_mes_base AND f.ano = v_anio_base
      AND (p_es_demo = FALSE OR f.codigo_dataease = ANY(p_codigos_prueba))
    ORDER BY f.codigo_dataease, f.id DESC;

    CREATE INDEX idx_tmp_kardex_codigo ON tmp_kardex (codigo_dataease);

    -- 3. Bucle mes a mes
    FOR i IN 1..v_num_periodos LOOP
        v_mes_actual := p_meses[i];
        v_anio_actual := p_anios[i];

        -- Buscar la tasa de este mes
        SELECT COALESCE(MAX(tsi), 0.0) INTO v_tasa_actual 
        FROM tasas_interes 
        WHERE mes = v_mes_actual AND ano = v_anio_actual;

        -- Actualizar bandera de pago
        UPDATE tmp_kardex k
        SET empezo_a_pagar = TRUE
        WHERE k.empezo_a_pagar = FALSE 
          AND EXISTS (
              SELECT 1 FROM factura_original f 
              WHERE f.codigo_dataease = k.codigo_dataease 
                AND f.mes = v_mes_actual 
                AND f.ano = v_anio_actual 
                AND f.pagada = TRUE
          );

        -- Insertar el registro del mes
        INSERT INTO reliquidaciones (
            devolucion_maestro_id, job_id, consolidado_tarifas, factura_original_id, factura, 
            codigo_dataease, tarifa_aplicada_id, concepto, mes_base, anio_base, 
            desocupado_mes_base, tipo_interes, valor_base_tarifa, capital_reliquidado, 
            intereses_calculados, total_devolucion, es_demo, fecha_calculo, detalle_tasas,
            aplicado, ajustado, devolucion, porsubcon, subcon
        )
        SELECT 
            p_maestro_id, p_job_id, v_nombre_devolucion,
            COALESCE(f_mes.id, k.factura_base_id),
            COALESCE(f_mes.factura, k.factura_base_num),
            k.codigo_dataease, k.tarifa_aplicada_id,
            p_concepto, v_mes_actual, v_anio_actual, k.estaba_desocupado, p_tipo_interes, k.valor_base_tarifa,
            
            k.capital_a_reportar, 
            
            -- 🔥 CORRECCIÓN: ABS() para leer el capital negativo y UPPER() para blindar el texto
            CASE 
                WHEN ABS(k.capital_acumulado) > 0 AND UPPER(p_tipo_interes) != 'NINGUNO' AND k.empezo_a_pagar THEN
                    v_tasa_actual * CASE WHEN UPPER(p_tipo_interes) = 'SIMPLE' THEN k.valor_base_tarifa ELSE k.capital_acumulado END
                ELSE 0.0
            END,
            
            k.capital_a_reportar + 
            CASE 
                WHEN ABS(k.capital_acumulado) > 0 AND UPPER(p_tipo_interes) != 'NINGUNO' AND k.empezo_a_pagar THEN
                    v_tasa_actual * CASE WHEN UPPER(p_tipo_interes) = 'SIMPLE' THEN k.valor_base_tarifa ELSE k.capital_acumulado END
                ELSE 0.0
            END,
            
            p_es_demo, v_fecha_calculo, 
            v_tasa_actual::TEXT, 
            k.aplicado, k.ajustado, k.devolucion, k.porsubcon, k.subcon
        FROM tmp_kardex k
        LEFT JOIN LATERAL (
            SELECT id, factura 
            FROM factura_original f 
            WHERE f.codigo_dataease = k.codigo_dataease 
              AND f.mes = v_mes_actual 
              AND f.ano = v_anio_actual 
            ORDER BY id DESC LIMIT 1
        ) f_mes ON true;

        -- Preparar variables para el siguiente mes
        IF i < v_num_periodos THEN
            UPDATE tmp_kardex k
            SET 
                capital_acumulado = capital_acumulado + 
                    CASE 
                        WHEN ABS(k.capital_acumulado) > 0 AND UPPER(p_tipo_interes) = 'COMPUESTO' AND k.empezo_a_pagar THEN
                            v_tasa_actual * k.capital_acumulado
                        ELSE 0.0
                    END,
                capital_a_reportar = 0.0;
        END IF;

    END LOOP;
END;
$procedure$

;
