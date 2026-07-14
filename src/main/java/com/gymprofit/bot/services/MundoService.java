package com.gymprofit.bot.services;

import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.List;
import java.util.Set;

/**
 * Lógica de mundos del RPG de combate (COMBAT-2): navegación y desbloqueo. Un mundo se DESBLOQUEA
 * cuando el jugador ha derrotado al jefe del mundo <b>anterior</b> (el primero está siempre abierto);
 * el {@code nivelRequerido} es una recomendación informativa que se hará valer al pelear (COMBAT-3),
 * no bloquea la navegación. Aquí no hay pelea: solo se listan mundos (con su estado) y el bestiario.
 */
public final class MundoService {

    /**
     * Estado de un mundo para un jugador concreto.
     *
     * @param mundo        el mundo del catálogo
     * @param desbloqueado si el jugador puede acceder (jefe anterior derrotado, o es el primero)
     * @param completado   si el jugador ya derrotó a su jefe
     */
    public record MundoVista(Mundos mundo, boolean desbloqueado, boolean completado) {
    }

    /**
     * Progreso de mundos de un jugador.
     *
     * @param nivelJugador nivel actual del jugador (para contrastar con {@code nivelRequerido})
     * @param mundos       lista de mundos con su estado, en orden de ruta
     */
    public record Progreso(int nivelJugador, List<MundoVista> mundos) {
    }

    private final MundoRepositorio mundos;
    private final UsuarioDiscordRepositorio usuarios;

    public MundoService(MundoRepositorio mundos, UsuarioDiscordRepositorio usuarios) {
        this.mundos = mundos;
        this.usuarios = usuarios;
    }

    /**
     * ¿Está desbloqueado este mundo, dado el conjunto de mundos ya completados? Lógica pura
     * (sin BD) para poder testearla: el primer mundo (sin anterior) siempre lo está; el resto,
     * solo si el mundo inmediatamente anterior está completado.
     */
    public static boolean estaDesbloqueado(Mundos mundo, Set<String> completados) {
        return mundo.anterior()
                .map(anterior -> completados.contains(anterior.id()))
                .orElse(true);
    }

    /** Progreso de mundos del jugador: nivel + estado (desbloqueado/completado) de cada mundo. */
    public Progreso progreso(long discordId) {
        usuarios.obtenerOCrear(discordId);
        int nivel = usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0);
        Set<String> completados = mundos.completados(discordId);
        List<MundoVista> vistas = Mundos.CATALOGO.stream()
                .map(m -> new MundoVista(m, estaDesbloqueado(m, completados),
                        completados.contains(m.id())))
                .toList();
        return new Progreso(nivel, vistas);
    }

    /** Monstruos de un mundo (bestiario), o lista vacía si el mundo no existe. */
    public List<Monstruos> bestiario(String mundoId) {
        return Monstruos.deMundo(mundoId);
    }
}
