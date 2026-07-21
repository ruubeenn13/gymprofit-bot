package com.gymprofit.bot.services;

import com.gymprofit.bot.services.Pasivos.Bono;
import com.gymprofit.bot.services.Pasivos.Pasivo;
import com.gymprofit.bot.services.Pasivos.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Barridos de integridad del catálogo de pasivos (sin mocks, al estilo de {@code CamasTest} y
 * {@code RarezaTest}). La tabla de bonos ES el entregable de este módulo: sin estos tests, un typo en
 * una magnitud pasaría en verde y solo se vería jugando (o peor: rompiendo la economía).
 */
class PasivosTest {

    /** Los BIEN que no son camas, es decir los 10 vehículos. */
    private static List<String> vehiculos() {
        Set<String> camas = Camas.CATALOGO.stream().map(Camas::itemId).collect(Collectors.toSet());
        return Items.CATALOGO.stream()
                .filter(i -> i.categoria() == Items.Categoria.BIEN)
                .map(Items::id)
                .filter(id -> !camas.contains(id))
                .toList();
    }

    /** Los 20 ítems de categoría EQUIPO. */
    private static List<String> equipo() {
        return Items.CATALOGO.stream()
                .filter(i -> i.categoria() == Items.Categoria.EQUIPO)
                .map(Items::id).toList();
    }

    @Test
    @DisplayName("1 · los 30 ids del catálogo existen en Items")
    void todosLosIdsExistenEnItems() {
        for (Pasivo p : Pasivos.CATALOGO) {
            assertTrue(Items.porId(p.itemId()).isPresent(),
                    "el pasivo " + p.itemId() + " debe existir en Items");
        }
    }

    @Test
    @DisplayName("2 · todos son EQUIPO o vehículo BIEN; ninguna cama tiene pasivo")
    void ningunaCamaTienePasivo() {
        Set<String> camas = Camas.CATALOGO.stream().map(Camas::itemId).collect(Collectors.toSet());
        for (Pasivo p : Pasivos.CATALOGO) {
            Items i = Items.porId(p.itemId()).orElseThrow();
            assertFalse(camas.contains(p.itemId()),
                    p.itemId() + " es una cama: su efecto ya lo da Camas, sería doble recompensa");
            assertTrue(i.categoria() == Items.Categoria.EQUIPO
                            || i.categoria() == Items.Categoria.BIEN,
                    p.itemId() + " debe ser EQUIPO o BIEN, no " + i.categoria());
        }
    }

    @Test
    @DisplayName("3 · cobertura total: los 20 EQUIPO y los 10 vehículos tienen pasivo")
    void coberturaTotal() {
        Set<String> conPasivo = Pasivos.CATALOGO.stream().map(Pasivo::itemId)
                .collect(Collectors.toSet());
        Set<String> esperados = new HashSet<>(equipo());
        esperados.addAll(vehiculos());
        assertEquals(30, esperados.size(), "deben ser 20 EQUIPO + 10 vehículos BIEN");
        assertEquals(esperados, conPasivo,
                "si se añade un EQUIPO o un vehículo a Items sin pasivo, este test debe fallar");
        assertEquals(30, Pasivos.CATALOGO.size(), "el catálogo tiene exactamente 30 entradas");
    }

    @Test
    @DisplayName("4 · sin ids duplicados")
    void sinDuplicados() {
        Set<String> vistos = new HashSet<>();
        for (Pasivo p : Pasivos.CATALOGO) {
            assertTrue(vistos.add(p.itemId()), "id duplicado en el catálogo: " + p.itemId());
        }
    }

    @Test
    @DisplayName("5 · ningún ítem alcanza por sí solo el tope de ningún tipo")
    void ningunItemSaturaSolo() {
        for (Pasivo p : Pasivos.CATALOGO) {
            for (Bono b : p.bonos()) {
                assertTrue(b.magnitud() < Pasivos.TOPES.get(b.tipo()),
                        p.itemId() + " llega solo al tope de " + b.tipo()
                                + ": el sistema debe exigir combinar al menos dos ranuras");
            }
        }
    }

    @Test
    @DisplayName("6 · magnitudes > 0 y todos los tipos con fuente y tope")
    void magnitudesPositivasYTiposCubiertos() {
        for (Pasivo p : Pasivos.CATALOGO) {
            assertFalse(p.bonos().isEmpty(), p.itemId() + " no puede tener cero bonos");
            for (Bono b : p.bonos()) {
                assertTrue(b.magnitud() > 0, p.itemId() + " tiene un bono no positivo de " + b.tipo());
            }
        }
        for (Tipo t : Tipo.values()) {
            assertTrue(Pasivos.TOPES.containsKey(t), "falta el tope de " + t);
            assertFalse(Pasivos.fuentesDe(t).isEmpty(), "ningún ítem da " + t);
        }
    }

    @Test
    @DisplayName("7a · las magnitudes caen en el rango plausible de su tipo (caza el 0.7 por 0.07)")
    void magnitudesEnRango() {
        for (Pasivo p : Pasivos.CATALOGO) {
            for (Bono b : p.bonos()) {
                if (Pasivos.esPorcentual(b.tipo())) {
                    assertTrue(b.magnitud() <= 0.15,
                            p.itemId() + ": " + b.tipo() + " porcentual fuera de rango ("
                                    + b.magnitud() + "); ¿un 0.7 donde iba 0.07?");
                    assertEquals(Math.rint(b.magnitud() * 100), b.magnitud() * 100, 1e-9,
                            p.itemId() + ": " + b.tipo() + " no es un % entero");
                } else {
                    assertEquals(Math.rint(b.magnitud()), b.magnitud(), 1e-9,
                            p.itemId() + ": " + b.tipo() + " plano debe ser entero");
                    assertTrue(b.magnitud() >= 1 && b.magnitud() <= 5,
                            p.itemId() + ": " + b.tipo() + " plano fuera de [1,5]");
                }
            }
        }
    }

    /** Suma de las 4 magnitudes más altas de un tipo: el máximo teórico con las 4 ranuras. */
    private static double mejores4(Tipo tipo) {
        return Pasivos.fuentesDe(tipo).values().stream()
                .sorted(Comparator.reverseOrder()).limit(4)
                .mapToDouble(Double::doubleValue).sum();
    }

    @Test
    @DisplayName("7b · el mejor build de 4 ranuras coincide con la tabla de balance de la spec")
    void tablaDeBalanceDeLaSpec() {
        assertEquals(0.35, mejores4(Tipo.SUELDO), 1e-9, "jet 11 + avioneta 9 + traje 8 + coche_lujo 7");
        assertEquals(0.38, mejores4(Tipo.COOLDOWN_WORK), 1e-9, "jet 11 + helicoptero 10 + coche 9 + moto 8");
        assertEquals(0.24, mejores4(Tipo.XP), 1e-9, "yate 7 + jet 6 + avioneta 6 + reloj 5");
        assertEquals(7.0, mejores4(Tipo.ENERGIA_REGEN), 1e-9, "yate 3 + moto_agua 2 + 1 + 1");
        assertEquals(6.0, mejores4(Tipo.MINERIA_CANTIDAD), 1e-9, "dron 2 + camion 2 + 1 + 1");
        assertEquals(0.38, mejores4(Tipo.MINERIA_DURABILIDAD), 1e-9, "helicoptero 12 + dron 10 + 8 + 8");
        assertEquals(11.0, mejores4(Tipo.COMBATE_ATAQUE), 1e-9, "solo hay 3 fuentes: 5 + 3 + 3");
        assertEquals(9.0, mejores4(Tipo.COMBATE_DEFENSA), 1e-9, "cohete 4 + zapatillas 2 + coche_lujo 2 + uniforme 1");
        assertEquals(0.07, mejores4(Tipo.CRITICO), 1e-9, "cohete 3 + coche_lujo 2 + gafas 1 + traje 1");
    }

    @Test
    @DisplayName("8 · los topes son los de la spec y cuatro quedan holgados a propósito")
    void topesDeLaSpec() {
        assertEquals(0.30, Pasivos.TOPES.get(Tipo.SUELDO), 1e-9);
        assertEquals(0.25, Pasivos.TOPES.get(Tipo.COOLDOWN_WORK), 1e-9);
        assertEquals(0.20, Pasivos.TOPES.get(Tipo.XP), 1e-9);
        assertEquals(5.0, Pasivos.TOPES.get(Tipo.ENERGIA_REGEN), 1e-9);
        assertEquals(3.0, Pasivos.TOPES.get(Tipo.MINERIA_CANTIDAD), 1e-9);
        assertEquals(0.40, Pasivos.TOPES.get(Tipo.MINERIA_DURABILIDAD), 1e-9);
        assertEquals(12.0, Pasivos.TOPES.get(Tipo.COMBATE_ATAQUE), 1e-9);
        assertEquals(10.0, Pasivos.TOPES.get(Tipo.COMBATE_DEFENSA), 1e-9);
        assertEquals(0.08, Pasivos.TOPES.get(Tipo.CRITICO), 1e-9);
        // Durabilidad, ataque, defensa y crítico NO se tocan ni con el mejor build: es margen
        // deliberado para añadir ítems en el futuro sin bajarle el bono a nadie.
        assertTrue(mejores4(Tipo.MINERIA_DURABILIDAD) < Pasivos.TOPES.get(Tipo.MINERIA_DURABILIDAD));
        assertTrue(mejores4(Tipo.COMBATE_ATAQUE) < Pasivos.TOPES.get(Tipo.COMBATE_ATAQUE));
        assertTrue(mejores4(Tipo.COMBATE_DEFENSA) < Pasivos.TOPES.get(Tipo.COMBATE_DEFENSA));
        assertTrue(mejores4(Tipo.CRITICO) < Pasivos.TOPES.get(Tipo.CRITICO));
    }

    @Test
    @DisplayName("9 · cuantos más bonos, más caro (regla de diseño 4)")
    void masBonosMasCaro() {
        for (Pasivo p : Pasivos.CATALOGO) {
            long precio = Items.porId(p.itemId()).orElseThrow().precio();
            assertTrue(p.bonos().size() <= 3, p.itemId() + " no puede pasar de 3 bonos");
            if (p.bonos().size() >= 3) {
                assertTrue(precio >= 90_000,
                        p.itemId() + " tiene 3 bonos y cuesta " + precio + ": los de 3 son de lujo");
            }
            if (p.bonos().size() == 1) {
                assertTrue(precio <= 1_500,
                        p.itemId() + " tiene 1 bono y cuesta " + precio + ": los de 1 son baratos");
            }
        }
    }

    @Test
    @DisplayName("10 · porId encuentra lo que hay y no inventa lo que no")
    void porId() {
        assertTrue(Pasivos.porId("jet").isPresent());
        assertEquals(3, Pasivos.porId("jet").orElseThrow().bonos().size());
        assertTrue(Pasivos.porId("fruta").isEmpty(), "un consumible no tiene pasivo");
        assertTrue(Pasivos.porId("casa").isEmpty(), "una cama no tiene pasivo");
        assertTrue(Pasivos.porId("no_existe").isEmpty());
    }

    @Test
    @DisplayName("11 · fuentesDe devuelve ítem → magnitud en el orden del catálogo")
    void fuentesDe() {
        var energia = Pasivos.fuentesDe(Tipo.ENERGIA_REGEN);
        assertEquals(6, energia.size(), "zapatillas, mancuernas, bici, moto_agua, avioneta, yate");
        assertEquals(3.0, energia.get("yate"), 1e-9);
        assertEquals(1.0, energia.get("zapatillas"), 1e-9);
        assertFalse(energia.containsKey("jet"), "el jet no da energía");
    }
}
