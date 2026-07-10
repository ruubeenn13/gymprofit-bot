package com.gymprofit.bot.services;

import com.gymprofit.bot.services.EstadisticasService.Conteo;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica la lógica de conteo de los contadores en vivo (humanos, en línea y bots) sin abrir una
 * conexión a Discord: {@link EstadisticasService#contar(List)} es pura y se prueba con mocks.
 */
class EstadisticasServiceTest {

    private static Member miembro(boolean bot, OnlineStatus estado) {
        User user = mock(User.class);
        when(user.isBot()).thenReturn(bot);
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(member.getOnlineStatus()).thenReturn(estado);
        return member;
    }

    @Test
    void cuentaHumanosOnlineYBotsPorSeparado() {
        List<Member> miembros = List.of(
                miembro(false, OnlineStatus.ONLINE),
                miembro(false, OnlineStatus.IDLE),
                miembro(false, OnlineStatus.OFFLINE),
                miembro(true, OnlineStatus.ONLINE),
                miembro(true, OnlineStatus.OFFLINE));

        Conteo c = EstadisticasService.contar(miembros);

        assertEquals(3, c.miembros(), "3 humanos (los bots no cuentan como miembros)");
        assertEquals(2, c.online(), "2 humanos en línea (ONLINE e IDLE; OFFLINE no)");
        assertEquals(2, c.bots(), "2 bots");
    }

    @Test
    void losBotsNoCuentanComoOnlineAunqueEstenConectados() {
        List<Member> miembros = List.of(
                miembro(true, OnlineStatus.ONLINE),
                miembro(true, OnlineStatus.DO_NOT_DISTURB));

        Conteo c = EstadisticasService.contar(miembros);

        assertEquals(0, c.miembros());
        assertEquals(0, c.online(), "los bots nunca cuentan en el contador de humanos en línea");
        assertEquals(2, c.bots());
    }

    @Test
    void listaVaciaDaCero() {
        Conteo c = EstadisticasService.contar(List.of());
        assertEquals(0, c.miembros());
        assertEquals(0, c.online());
        assertEquals(0, c.bots());
    }
}
