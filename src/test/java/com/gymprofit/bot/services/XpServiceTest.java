package com.gymprofit.bot.services;

import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link XpService} con el repositorio mockeado: cálculo de XP/nivel al ganar XP,
 * detección de subida de nivel y conservación del resto de campos del usuario.
 */
@ExtendWith(MockitoExtension.class)
class XpServiceTest {

    @Mock
    private UsuarioDiscordRepositorio repositorio;

    @Test
    void ganarXpSubeDeNivelYPersiste() {
        when(repositorio.obtenerOCrear(5L))
                .thenReturn(new UsuarioDiscord(5L, 90, 0, 30, 2, null, "en", true));

        XpResultado resultado = new XpService(repositorio).ganarXp(5L, 20);

        assertTrue(resultado.subioNivel(), "90 + 20 = 110 XP cruza el umbral del nivel 1");
        assertEquals(0, resultado.nivelAnterior());
        assertEquals(1, resultado.nivelNuevo());

        ArgumentCaptor<UsuarioDiscord> guardado = ArgumentCaptor.forClass(UsuarioDiscord.class);
        verify(repositorio).guardar(guardado.capture());
        assertEquals(110, guardado.getValue().xp());
        assertEquals(1, guardado.getValue().nivel());
        // El resto de campos se conservan.
        assertEquals(30, guardado.getValue().coins());
        assertEquals(2, guardado.getValue().racha());
        assertEquals("en", guardado.getValue().idioma());
        assertTrue(guardado.getValue().optOutLogros());
    }

    @Test
    void ganarXpSinSubirDeNivel() {
        when(repositorio.obtenerOCrear(5L))
                .thenReturn(new UsuarioDiscord(5L, 10, 0, 0, 0, null, "es", false));

        XpResultado resultado = new XpService(repositorio).ganarXp(5L, 20);

        assertFalse(resultado.subioNivel(), "30 XP sigue en el nivel 0");
        assertEquals(0, resultado.nivelNuevo());
    }
}
