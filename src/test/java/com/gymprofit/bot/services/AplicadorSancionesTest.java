package com.gymprofit.bot.services;

import com.gymprofit.bot.commands.moderacion.AplicadorSanciones;
import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.services.ModeracionService.AccionEscalado;
import com.gymprofit.bot.services.ModeracionService.ResultadoAviso;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica que {@link AplicadorSanciones} aplica sobre Discord el escalón que decide
 * {@link ModeracionService} (ninguno / timeout / ban), registra el escalado y siempre intenta el DM
 * al sancionado, sin que un DM cerrado rompa la sanción. Las cadenas de {@code RestAction} de JDA se
 * mockean con {@code RETURNS_DEEP_STUBS} (queue/reason no hacen nada); el canal de DM se mockea
 * explícito para poder capturar el consumidor de error.
 */
class AplicadorSancionesTest {

    private static final long GID = 1L;
    private static final long UID = 100L;
    private static final long MOD = 200L;
    private static final String MOTIVO = "spam de menciones";

    private final ModeracionService moderacion = mock(ModeracionService.class);
    private final ConfigServidorService config = mock(ConfigServidorService.class);
    private final Guild guild = mock(Guild.class, RETURNS_DEEP_STUBS);
    private final Member objetivo = mock(Member.class, RETURNS_DEEP_STUBS);
    private final User user = mock(User.class);
    @SuppressWarnings("unchecked")
    private final CacheRestAction<PrivateChannel> dm = mock(CacheRestAction.class);

    private final AplicadorSanciones aplicador = new AplicadorSanciones(moderacion, config);

    @BeforeEach
    void setUp() {
        when(guild.getIdLong()).thenReturn(GID);
        when(guild.getName()).thenReturn("GymProFit");
        when(objetivo.getIdLong()).thenReturn(UID);
        when(objetivo.getUser()).thenReturn(user);
        when(user.getIdLong()).thenReturn(UID);
        when(user.getAsMention()).thenReturn("<@" + UID + ">");
        when(user.getId()).thenReturn(String.valueOf(UID));
        when(user.openPrivateChannel()).thenReturn(dm);
        // Sin canal de bot-logs configurado: registrarEnLogs vuelve antes de publicar nada.
        when(config.obtener(anyLong())).thenReturn(ConfigServidor.porDefecto(GID));
    }

    private void stubAviso(AccionEscalado accion, int activos) {
        when(moderacion.avisar(GID, UID, MOD, MOTIVO))
                .thenReturn(new ResultadoAviso(1L, activos, accion, true));
    }

    @Test
    void sinEscaladoNoAplicaTimeoutNiBanPeroIntentaDm() {
        stubAviso(AccionEscalado.NINGUNA, 1);

        aplicador.aplicar(guild, user, objetivo, MOD, MOTIVO);

        verify(objetivo, never()).timeoutFor(any(Duration.class));
        verify(guild, never()).ban(any(User.class), anyInt(), any(TimeUnit.class));
        verify(user).openPrivateChannel(); // el DM best-effort siempre se intenta
    }

    @Test
    void timeout1hAplicaTimeoutYRegistra() {
        stubAviso(AccionEscalado.TIMEOUT_1H, 3);

        aplicador.aplicar(guild, user, objetivo, MOD, MOTIVO);

        verify(objetivo).timeoutFor(Duration.ofSeconds(3600L));
        verify(moderacion).registrar(eq(GID), eq(UID), eq(MOD), eq("TIMEOUT"), any(), isNull(), eq(3600L));
        verify(user).openPrivateChannel();
    }

    @Test
    void banAplicaBanYRegistra() {
        stubAviso(AccionEscalado.BAN, 7);

        aplicador.aplicar(guild, user, objetivo, MOD, MOTIVO);

        verify(guild).ban(user, 0, TimeUnit.SECONDS);
        verify(moderacion).registrar(eq(GID), eq(UID), eq(MOD), eq("BAN"), any(), isNull(), isNull());
        verify(objetivo, never()).timeoutFor(any(Duration.class));
    }

    @Test
    void banSeAplicaAunqueElObjetivoHayaSalidoDelServidor() {
        stubAviso(AccionEscalado.BAN, 7);

        // Member null = el usuario ya no está en el servidor: el ban debe aplicarse igual.
        aplicador.aplicar(guild, user, null, MOD, MOTIVO);

        verify(guild).ban(user, 0, TimeUnit.SECONDS);
        verify(moderacion).registrar(eq(GID), eq(UID), eq(MOD), eq("BAN"), any(), isNull(), isNull());
    }

    @Test
    void timeoutNoSeAplicaSinMiembroPeroSeRegistra() {
        stubAviso(AccionEscalado.TIMEOUT_1H, 3);

        // Sin Member no se puede aplicar el timeout de Discord, pero el escalón sí queda registrado.
        aplicador.aplicar(guild, user, null, MOD, MOTIVO);

        verify(objetivo, never()).timeoutFor(any(Duration.class));
        verify(moderacion).registrar(eq(GID), eq(UID), eq(MOD), eq("TIMEOUT"), any(), isNull(), eq(3600L));
    }

    @Test
    void unDmCerradoNoRompeLaSancion() {
        stubAviso(AccionEscalado.NINGUNA, 1);

        aplicador.aplicar(guild, user, objetivo, MOD, MOTIVO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> fallo = ArgumentCaptor.forClass(Consumer.class);
        verify(dm).queue(any(), fallo.capture());
        // Invocar el consumidor de error (DM cerrado) no debe propagar excepción.
        assertDoesNotThrow(() -> fallo.getValue().accept(new RuntimeException("DM cerrado")));
    }
}
