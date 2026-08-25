package com.pcmarketbuilder.publication_service.Model;

/**
 * Estado de conservación de la pieza publicada.
 * Concepto cerrado (no cambia), por eso en la BD se mantiene como ENUM nativo
 * de MariaDB. Ver Modelo_relacion.sql.
 */
public enum Grade {
    GRADE_A,
    GRADE_B,
    GRADE_C
}
