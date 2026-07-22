package com.gymprofit.bot.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Barridos de integridad del catálogo de ascensos: si alguien añade un sector nuevo a
 * {@code Trabajos} y olvida mapearlo a una rama, o rompe la monotonía de los requisitos,
 * estos tests lo paran en el build, no en producción.
 */
class AscensosTest {

    @Test
    @DisplayName("todo sector del catálogo de trabajos pertenece a exactamente una rama")
    void todoSectorTieneRama() {
        for (Trabajos t : Trabajos.CATALOGO) {
            assertNotNull(Ascensos.ramaDe(t.sector()),
                    "sector sin rama: " + t.sector() + " (añadirlo a SECTOR_A_RAMA)");
        }
    }

    @Test
    @DisplayName("toda rama tiene al menos un puesto y un tier de entrada")
    void todaRamaTienePuestos() {
        for (Ascensos.Rama rama : Ascensos.Rama.values()) {
            assertTrue(Ascensos.tierEntrada(rama) >= 1,
                    "rama sin puestos en el catálogo: " + rama);
        }
    }

    @Test
    @DisplayName("los requisitos crecen con el tier destino (monotonía)")
    void requisitosMonotonos() {
        Ascensos.Requisitos r2 = Ascensos.requisitosPara(2);
        Ascensos.Requisitos r3 = Ascensos.requisitosPara(3);
        Ascensos.Requisitos r4 = Ascensos.requisitosPara(4);
        assertTrue(r2.turnos() < r3.turnos() && r3.turnos() < r4.turnos());
        assertTrue(r2.estudios() < r3.estudios() && r3.estudios() < r4.estudios());
        assertTrue(r2.stat() < r3.stat() && r3.stat() < r4.stat());
        assertTrue(r2.coins() < r3.coins() && r3.coins() < r4.coins());
    }

    @Test
    @DisplayName("siguienteTier salta los tiers huecos y devuelve vacío en el tope de la rama")
    void siguienteTierSaltaHuecos() {
        // La rama de arte no tiene t1: su entrada es t2 y de t2 se pasa a t3 (actor).
        assertEquals(2, Ascensos.tierEntrada(Ascensos.Rama.ARTE));
        assertEquals(Optional.of(3), Ascensos.siguienteTier(Ascensos.Rama.ARTE, 2));
        assertEquals(Optional.empty(), Ascensos.siguienteTier(Ascensos.Rama.ARTE, 3),
                "t3 es el tope de la rama de arte: no hay más ascensos");
        // Salud llega a t4 (cirujano/astronauta).
        assertEquals(Optional.of(4), Ascensos.siguienteTier(Ascensos.Rama.SALUD, 3));
    }

    @Test
    @DisplayName("puestosDe devuelve los puestos de la rama en ese tier, y solo esos")
    void puestosDeFiltraBien() {
        List<Trabajos> t4Salud = Ascensos.puestosDe(Ascensos.Rama.SALUD, 4);
        assertFalse(t4Salud.isEmpty());
        for (Trabajos t : t4Salud) {
            assertEquals(4, t.tier());
            assertEquals(Ascensos.Rama.SALUD, Ascensos.ramaDe(t.sector()));
        }
    }

    @Test
    @DisplayName("toda rama tiene una stat válida del personaje")
    void statValidaPorRama() {
        for (Ascensos.Rama rama : Ascensos.Rama.values()) {
            String stat = Ascensos.statDe(rama);
            assertTrue(List.of("fuerza", "resistencia", "carisma").contains(stat),
                    "stat desconocida en " + rama + ": " + stat);
        }
    }
}
