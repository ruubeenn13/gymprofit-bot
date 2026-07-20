package com.gymprofit.bot.events;

import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests del registro de acciones pendientes: se relanzan una vez, por jugador y sin caducar. */
class ReintentoRegistroTest {

    private static final long ID = 42L;
    private static final Instant AHORA = Instant.parse("2026-07-20T08:00:00Z");

    private final ReintentoRegistro registro = new ReintentoRegistro();

    private static ReintentoRegistro.Accion accion(String texto) {
        return locale -> MessageCreateData.fromContent(texto);
    }

    @Test
    @DisplayName("la accion guardada se recupera y se consume")
    void seConsumeUnaSolaVez() {
        registro.guardar(ID, AHORA, accion("currar"));

        assertEquals("currar",
                registro.tomar(ID, AHORA).orElseThrow().ejecutar(Locale.forLanguageTag("es")).getContent());
        // Segundo intento: ya no queda nada (el botón podría pulsarse dos veces).
        assertTrue(registro.tomar(ID, AHORA).isEmpty());
    }

    @Test
    @DisplayName("sin nada pendiente no se relanza nada")
    void sinPendienteNoHayNada() {
        assertTrue(registro.tomar(ID, AHORA).isEmpty());
    }

    @Test
    @DisplayName("una accion caducada no se relanza")
    void caduca() {
        registro.guardar(ID, AHORA, accion("minar"));

        assertTrue(registro.tomar(ID, AHORA.plus(ReintentoRegistro.TTL).plusSeconds(1)).isEmpty());
    }

    @Test
    @DisplayName("solo se guarda la ultima accion de cada jugador")
    void laUltimaManda() {
        registro.guardar(ID, AHORA, accion("minar"));
        registro.guardar(ID, AHORA, accion("currar"));

        assertEquals("currar",
                registro.tomar(ID, AHORA).orElseThrow().ejecutar(Locale.forLanguageTag("es")).getContent());
    }

    @Test
    @DisplayName("seguir durmiendo descarta lo pendiente")
    void descartar() {
        registro.guardar(ID, AHORA, accion("currar"));
        registro.descartar(ID);

        assertTrue(registro.tomar(ID, AHORA).isEmpty());
    }

    @Test
    @DisplayName("cada jugador tiene su propio reintento")
    void unoPorJugador() {
        registro.guardar(ID, AHORA, accion("currar"));
        registro.guardar(99L, AHORA, accion("minar"));

        assertEquals("currar",
                registro.tomar(ID, AHORA).orElseThrow().ejecutar(Locale.forLanguageTag("es")).getContent());
        assertEquals("minar",
                registro.tomar(99L, AHORA).orElseThrow().ejecutar(Locale.forLanguageTag("es")).getContent());
    }
}
