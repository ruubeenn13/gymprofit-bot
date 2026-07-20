package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests del catálogo de camas: elección de la mejor e integridad contra {@link Items}. */
class CamasTest {

    @Test
    @DisplayName("sin nada en el inventario se duerme en el suelo")
    void sinNadaDuermeEnElSuelo() {
        assertEquals(Camas.SUELO, Camas.mejorDe(Map.of()));
    }

    @Test
    @DisplayName("elige la mejor cama que se posee, no la primera")
    void eligeLaMejor() {
        Map<String, Integer> inv = Map.of("saco_dormir", 1, "casa", 1, "colchon", 1);
        assertEquals("casa", Camas.mejorDe(inv).itemId());
    }

    @Test
    @DisplayName("un ítem con cantidad 0 no cuenta")
    void cantidadCeroNoCuenta() {
        assertEquals(Camas.SUELO, Camas.mejorDe(Map.of("colchon", 0)));
    }

    @Test
    @DisplayName("todas las camas con itemId existen en el catálogo de Items")
    void integridadDelCatalogo() {
        for (Camas c : Camas.CATALOGO) {
            assertTrue(Items.porId(c.itemId()).isPresent(),
                    "la cama " + c.itemId() + " debe existir en Items");
        }
    }

    /**
     * Pinea los números de una entrada del catálogo. La tabla de camas ES el entregable: sin esto,
     * un typo en un tope pasaría en verde y solo se vería jugando.
     */
    private static void assertCama(String itemId, int energiaHora, int tope) {
        Camas cama = Camas.CATALOGO.stream()
                .filter(c -> itemId.equals(c.itemId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta la cama " + itemId + " en el catálogo"));
        assertEquals(energiaHora, cama.energiaHora(), "energía/hora de " + itemId);
        assertEquals(tope, cama.tope(), "tope de " + itemId);
    }

    @Test
    @DisplayName("la tabla de camas tiene los valores de la spec")
    void tablaDeCamas() {
        // Fuera del catálogo: no se poseen (el suelo es el fallback, el hotel se paga por noche).
        assertEquals(10, Camas.SUELO.energiaHora(), "energía/hora del suelo");
        assertEquals(60, Camas.SUELO.tope(), "tope del suelo");
        assertEquals(25, Camas.HOTEL.energiaHora(), "energía/hora del hotel");
        assertEquals(100, Camas.HOTEL.tope(), "tope del hotel");
        assertEquals(200, Camas.PRECIO_HOTEL, "precio del hotel");

        // Camas que se poseen, de peor a mejor.
        assertCama("saco_dormir", 15, 75);
        assertCama("colchon", 20, 85);
        assertCama("piso", 25, 95);
        assertCama("apartamento", 25, 95);
        // De casa en adelante ya se llega a 100: las viviendas caras son estatus, no ventaja.
        assertCama("casa", 30, 100);
        assertCama("chalet", 30, 100);
        assertCama("mansion", 30, 100);
        assertCama("isla", 30, 100);
        assertCama("castillo", 30, 100);
        assertCama("rascacielos", 30, 100);

        // Que no se cuele una cama nueva sin pinear aquí sus números.
        assertEquals(10, Camas.CATALOGO.size(), "camas en el catálogo");
    }
}
