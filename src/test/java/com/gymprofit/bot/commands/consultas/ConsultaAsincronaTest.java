package com.gymprofit.bot.commands.consultas;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifica el punto único de errores de las consultas: pase lo que pase dentro del trabajo
 * asíncrono el usuario recibe respuesta (antes, cualquier excepción que no fuera
 * {@link ApiException} se tragaba en silencio y el «pensando…» quedaba colgado).
 */
class ConsultaAsincronaTest {

    /** Hook mockeado a fondo: las cadenas de RestAction devuelven stubs que no hacen nada. */
    private static InteractionHook hook(IDeferrableCallback evento) {
        InteractionHook hook = mock(InteractionHook.class, RETURNS_DEEP_STUBS);
        when(evento.getHook()).thenReturn(hook);
        return hook;
    }

    /** Ejecuta el trabajo en un pool real y espera a que termine (no hay futuro que esperar). */
    private static void correr(Runnable trabajo, IDeferrableCallback evento,
                               ConsultaAsincrona.Aviso aviso) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ConsultaAsincrona.ejecutar(evento, Messages.ES, executor, EmbedFactory.Tipo.STATS,
                aviso, "test", trabajo);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void unaExcepcionCualquieraTambienResponde() throws InterruptedException {
        IDeferrableCallback evento = mock(IDeferrableCallback.class);
        InteractionHook hook = hook(evento);

        correr(() -> {
            throw new IllegalStateException("carrera perdida en el sorteo del día");
        }, evento, ConsultaAsincrona.Aviso.BORRAR_ORIGINAL);

        verify(hook).deleteOriginal();
        ArgumentCaptor<MessageEmbed> embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).sendMessageEmbeds(embed.capture());
        assertTrue(embed.getValue().getDescription()
                .contains(Messages.get(Messages.ES, "comando.error.generico")));
    }

    @Test
    void laApiCaidaAvisaConElMensajeAmable() throws InterruptedException {
        IDeferrableCallback evento = mock(IDeferrableCallback.class);
        InteractionHook hook = hook(evento);

        correr(() -> {
            throw new ApiException("la API está dormida");
        }, evento, ConsultaAsincrona.Aviso.BORRAR_ORIGINAL);

        ArgumentCaptor<MessageEmbed> embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(hook).sendMessageEmbeds(embed.capture());
        assertTrue(embed.getValue().getDescription()
                .contains(Messages.get(Messages.ES, "ejercicios.error")));
    }

    /** En los componentes el mensaje original (la lista) no se toca: solo llega el followup. */
    @Test
    void enComponentesNoSeBorraElMensajeOriginal() throws InterruptedException {
        IDeferrableCallback evento = mock(IDeferrableCallback.class);
        InteractionHook hook = hook(evento);

        correr(() -> {
            throw new IllegalStateException("fallo");
        }, evento, ConsultaAsincrona.Aviso.MANTENER_ORIGINAL);

        verify(hook, never()).deleteOriginal();
        verify(hook).sendMessageEmbeds(any(MessageEmbed.class));
    }

    /** El camino feliz no manda ningún aviso. */
    @Test
    void siNoFallaNoAvisa() throws InterruptedException {
        IDeferrableCallback evento = mock(IDeferrableCallback.class);
        InteractionHook hook = hook(evento);

        correr(() -> { }, evento, ConsultaAsincrona.Aviso.BORRAR_ORIGINAL);

        verify(hook, never()).deleteOriginal();
        verify(hook, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }
}
