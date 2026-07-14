package com.gymprofit.bot.services;

import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica la lógica de desbloqueo de mundos, el armado del progreso y la integridad de los
 * catálogos amplios ({@link Mundos} y {@link Monstruos}).
 */
class MundoServiceTest {

    private final MundoRepositorio mundos = mock(MundoRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final MundoService servicio = new MundoService(mundos, usuarios);

    private static Mundos mundo(String id) {
        return Mundos.porId(id).orElseThrow();
    }

    // ---------------------- Desbloqueo (lógica pura) ----------------------

    @Test
    void elPrimerMundoSiempreEstaDesbloqueado() {
        assertTrue(MundoService.estaDesbloqueado(mundo("bosque"), Set.of()));
    }

    @Test
    void unMundoSeDesbloqueaAlCompletarElAnterior() {
        assertFalse(MundoService.estaDesbloqueado(mundo("cueva"), Set.of()));
        assertTrue(MundoService.estaDesbloqueado(mundo("cueva"), Set.of("bosque")));
    }

    @Test
    void completarUnMundoNoSaltaLosIntermedios() {
        // Tener el bosque hecho no abre el pantano (falta la cueva en medio).
        assertFalse(MundoService.estaDesbloqueado(mundo("pantano"), Set.of("bosque")));
        assertTrue(MundoService.estaDesbloqueado(mundo("pantano"), Set.of("bosque", "cueva")));
    }

    @Test
    void progresoMarcaDesbloqueadosYCompletados() {
        long id = 7L;
        when(usuarios.buscar(id)).thenReturn(Optional.of(
                new UsuarioDiscord(id, 500, 12, 0, 0, null, null, false)));
        when(mundos.completados(id)).thenReturn(Set.of("bosque"));

        MundoService.Progreso p = servicio.progreso(id);
        assertEquals(12, p.nivelJugador());
        assertEquals(Mundos.CATALOGO.size(), p.mundos().size());

        var bosque = p.mundos().get(0);
        assertTrue(bosque.completado());
        assertTrue(bosque.desbloqueado());
        var cueva = p.mundos().get(1);
        assertFalse(cueva.completado());
        assertTrue(cueva.desbloqueado()); // bosque completado -> cueva abierta
        var pantano = p.mundos().get(2);
        assertFalse(pantano.desbloqueado()); // cueva no completada
    }

    // ---------------------- Integridad de catálogos ----------------------

    @Test
    void losMundosEstanNumeradosDeFormaConsecutiva() {
        for (int i = 0; i < Mundos.CATALOGO.size(); i++) {
            assertEquals(i + 1, Mundos.CATALOGO.get(i).orden(),
                    "orden consecutivo empezando en 1");
        }
        assertTrue(Mundos.CATALOGO.size() >= 8, "catálogo amplio de mundos");
    }

    @Test
    void cadaMundoTieneMonstruosYExactamenteUnJefe() {
        for (Mundos m : Mundos.CATALOGO) {
            var mobs = Monstruos.deMundo(m.id());
            assertFalse(mobs.isEmpty(), "el mundo " + m.id() + " debe tener monstruos");
            long jefes = mobs.stream().filter(Monstruos::esJefe).count();
            assertEquals(1, jefes, "el mundo " + m.id() + " debe tener exactamente un jefe");
        }
    }

    @Test
    void elBestiarioEsAmplio() {
        assertTrue(Monstruos.CATALOGO.size() >= 60,
                "bestiario amplio (60+), tiene " + Monstruos.CATALOGO.size());
    }

    @Test
    void losIdsDeMonstruoSonUnicos() {
        long distintos = Monstruos.CATALOGO.stream().map(Monstruos::id).distinct().count();
        assertEquals(Monstruos.CATALOGO.size(), distintos, "no puede haber ids de monstruo repetidos");
    }

    @Test
    void cadaMonstruoPerteneceAUnMundoDelCatalogo() {
        for (Monstruos mo : Monstruos.CATALOGO) {
            assertTrue(Mundos.porId(mo.mundo()).isPresent(),
                    "el monstruo " + mo.id() + " referencia un mundo inexistente: " + mo.mundo());
        }
    }

    @Test
    void todoElLootReferenciaItemsExistentes() {
        for (Monstruos mo : Monstruos.CATALOGO) {
            for (Monstruos.Drop drop : mo.loot()) {
                assertTrue(Items.porId(drop.itemId()).isPresent(),
                        "loot de " + mo.id() + " apunta a un ítem inexistente: " + drop.itemId());
                assertTrue(drop.prob() > 0 && drop.prob() <= 1,
                        "probabilidad de loot fuera de rango en " + mo.id());
            }
        }
    }
}
