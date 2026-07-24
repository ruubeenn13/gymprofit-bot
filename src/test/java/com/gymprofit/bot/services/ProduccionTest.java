package com.gymprofit.bot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Prueba de {@link Produccion}: las funciones puras de la produccion de F5a (unidades por curro,
 * capacidad del almacen por nivel y el impuesto sobre la venta) devuelven los numeros esperados.
 */
class ProduccionTest {

    @Test
    void unidadesPorCurroEsBaseMasNivel() {
        assertEquals(6, Produccion.unidadesPorCurro(1));
        assertEquals(15, Produccion.unidadesPorCurro(10));
    }

    @Test
    void capacidadEsNivelPorFactor() {
        assertEquals(100, Produccion.capacidad(1));
        assertEquals(1_000, Produccion.capacidad(10));
    }

    @Test
    void impuestoYNetoDeUnaVenta() {
        long bruto = 100 * Produccion.PRECIO_UNIDAD; // 5_000
        long impuesto = Produccion.impuesto(bruto);
        assertEquals(750, impuesto);
        assertEquals(4_250, bruto - impuesto);
    }
}
