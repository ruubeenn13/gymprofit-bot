package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.TriviaPregunta;
import com.gymprofit.bot.db.TriviaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica {@link TriviaService}: el gate one-shot de {@code registrarRespuesta} y el premio por dificultad. */
class TriviaServiceTest {

    private static final long USER = 100L;

    private final TriviaRepositorio repo = mock(TriviaRepositorio.class);
    private final EconomiaRepositorio economia = mock(EconomiaRepositorio.class);
    private final XpService xp = mock(XpService.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);

    private TriviaService svc() {
        return new TriviaService(repo, economia, xp, usuarios);
    }

    @Test
    @DisplayName("acierto: registra, cobra coins y XP de la dificultad")
    void acierto() {
        var p = new TriviaPregunta(7, "FITNESS", "DIFICIL", "q", java.util.List.of("a", "b", "c", "d"), 'C');
        when(repo.porId(7, true)).thenReturn(java.util.Optional.of(p));
        when(repo.registrarRespuesta(USER, 7, true)).thenReturn(true);

        var r = svc().responder(USER, 7, 'C', true);

        assertEquals(TriviaService.Estado.ACIERTO, r.estado());
        verify(repo).registrarRespuesta(USER, 7, true);
        verify(economia).ingresar(USER, 120L, "trivia");
        verify(xp).ganarXp(USER, 50);
    }

    @Test
    @DisplayName("fallo: registra como fallo, no cobra, revela la correcta")
    void fallo() {
        var p = new TriviaPregunta(7, "FITNESS", "FACIL", "q", java.util.List.of("a", "b", "c", "d"), 'C');
        when(repo.porId(7, true)).thenReturn(java.util.Optional.of(p));
        when(repo.registrarRespuesta(USER, 7, false)).thenReturn(true);

        var r = svc().responder(USER, 7, 'A', true);

        assertEquals(TriviaService.Estado.FALLO, r.estado());
        assertEquals('C', r.correcta());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
        verify(xp, never()).ganarXp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("ya respondida (registrarRespuesta=false): YA_RESPONDIDA, no cobra")
    void yaRespondida() {
        var p = new TriviaPregunta(7, "FITNESS", "MEDIA", "q", java.util.List.of("a", "b", "c", "d"), 'C');
        when(repo.porId(7, true)).thenReturn(java.util.Optional.of(p));
        when(repo.registrarRespuesta(eq(USER), eq(7L), anyBoolean())).thenReturn(false);

        var r = svc().responder(USER, 7, 'C', true);

        assertEquals(TriviaService.Estado.YA_RESPONDIDA, r.estado());
        verify(economia, never()).ingresar(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("pregunta inexistente -> NO_EXISTE")
    void noExiste() {
        when(repo.porId(9, true)).thenReturn(java.util.Optional.empty());

        assertEquals(TriviaService.Estado.NO_EXISTE, svc().responder(USER, 9, 'A', true).estado());
    }
}
