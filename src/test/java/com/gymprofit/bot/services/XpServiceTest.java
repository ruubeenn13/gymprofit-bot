package com.gymprofit.bot.services;

import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica {@link XpService} con el repositorio mockeado: cálculo de XP/nivel al ganar XP,
 * detección de subida de nivel, conservación del resto de campos del usuario y aplicación del bono
 * de XP de los efectos pasivos ({@link Pasivos.Tipo#XP}).
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

    @Test
    @DisplayName("conBonoPasivos: +20 % sobre 100 son 120, y satura en el tope")
    void bonoDeXpPuro() {
        assertEquals(100, XpService.conBonoPasivos(100, 0.0), "sin pasivos no cambia nada");
        assertEquals(120, XpService.conBonoPasivos(100, 0.20));
        assertEquals(120, XpService.conBonoPasivos(100, 0.35), "el tope del +20 % satura");
        assertEquals(0, XpService.conBonoPasivos(0, 0.20), "sobre 0 no se inventa XP");
        assertEquals(100, XpService.conBonoPasivos(100, -0.50), "un bono negativo nunca resta");
    }

    @Test
    @DisplayName("suelo de +1: un bono positivo sobre una cantidad pequeña siempre se nota")
    void sueloDeUnPunto() {
        // round(3 × 1,20) = 4, así que aquí el suelo no hace falta…
        assertEquals(4, XpService.conBonoPasivos(3, 0.20));
        // …pero con 2 XP y +20 % el redondeo daría 2 (sin ganancia) y se leería como un bug.
        assertEquals(3, XpService.conBonoPasivos(2, 0.20));
        assertEquals(2, XpService.conBonoPasivos(1, 0.20));
    }

    @Test
    @DisplayName("ganarXp aplica el bono de pasivos antes de persistir")
    void ganarXpConPasivos() {
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonoDe(5L, Pasivos.Tipo.XP)).thenReturn(0.20);
        when(repositorio.obtenerOCrear(5L))
                .thenReturn(new UsuarioDiscord(5L, 0, 0, 0, 0, null, "es", false));

        new XpService(repositorio, pasivos).ganarXp(5L, 100);

        ArgumentCaptor<UsuarioDiscord> guardado = ArgumentCaptor.forClass(UsuarioDiscord.class);
        verify(repositorio).guardar(guardado.capture());
        assertEquals(120, guardado.getValue().xp(), "100 XP + 20 % de pasivos");
    }

    @Test
    @DisplayName("la subida de nivel se detecta también cuando la cruza el bono de pasivos")
    void bonoQueCruzaElUmbralSubeDeNivel() {
        // 90 + 8 = 98 se quedaría en nivel 0; con el +20 % son 10 XP y llega justo a los 100 del
        // nivel 1. El aviso de subida (y la sincronización del rol de rango que cuelga de él) tiene
        // que dispararse igual, así que el bono se suma ANTES de calcular el nivel nuevo.
        PasivoService pasivos = mock(PasivoService.class);
        when(pasivos.bonoDe(7L, Pasivos.Tipo.XP)).thenReturn(0.20);
        when(repositorio.obtenerOCrear(7L))
                .thenReturn(new UsuarioDiscord(7L, 90, 0, 0, 0, null, "es", false));

        XpResultado resultado = new XpService(repositorio, pasivos).ganarXp(7L, 8);

        assertEquals(100, resultado.usuario().xp());
        assertTrue(resultado.subioNivel(), "el bono es lo que cruza el umbral, pero cuenta igual");
        assertEquals(1, resultado.nivelNuevo());
    }
}
