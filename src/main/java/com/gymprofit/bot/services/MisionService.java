package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.MisionRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lógica de las misiones de caza (COMBAT-6a). Al ganar un combate, incrementa las misiones que casan
 * con el monstruo vencido; cuando una alcanza su meta, se <b>completa sola</b> (paga coins + XP y
 * reinicia su progreso, misiones repetibles). {@code /misiones} solo consulta el progreso.
 */
public final class MisionService {

    /** Vista de una misión para el listado: definición + progreso actual del jugador. */
    public record Vista(Misiones mision, int progreso) {
    }

    private final MisionRepositorio repo;
    private final EconomiaRepositorio economia;
    private final XpService xp;
    private final UsuarioDiscordRepositorio usuarios;

    public MisionService(MisionRepositorio repo, EconomiaRepositorio economia, XpService xp,
                         UsuarioDiscordRepositorio usuarios) {
        this.repo = repo;
        this.economia = economia;
        this.xp = xp;
        this.usuarios = usuarios;
    }

    /**
     * Registra una victoria: avanza las misiones que casan con el monstruo y completa (paga y
     * reinicia) las que llegan a la meta. Devuelve las misiones completadas en esta victoria.
     */
    public List<Misiones> registrarVictoria(long discordId, Monstruos monstruo) {
        usuarios.obtenerOCrear(discordId);
        Map<String, Integer> progreso = repo.progreso(discordId);
        List<Misiones> completadas = new ArrayList<>();
        for (Misiones m : Misiones.CATALOGO) {
            if (!m.casa(monstruo)) {
                continue;
            }
            int nuevo = progreso.getOrDefault(m.id(), 0) + 1;
            if (nuevo >= m.meta()) {
                economia.ingresar(discordId, m.coins(), "mision:" + m.id());
                xp.ganarXp(discordId, m.xp());
                repo.fijarProgreso(discordId, m.id(), 0);
                completadas.add(m);
            } else {
                repo.fijarProgreso(discordId, m.id(), nuevo);
            }
        }
        return completadas;
    }

    /** Progreso de todas las misiones del jugador (para {@code /misiones}). */
    public List<Vista> listar(long discordId) {
        usuarios.obtenerOCrear(discordId);
        Map<String, Integer> progreso = repo.progreso(discordId);
        List<Vista> vistas = new ArrayList<>();
        for (Misiones m : Misiones.CATALOGO) {
            vistas.add(new Vista(m, progreso.getOrDefault(m.id(), 0)));
        }
        return vistas;
    }
}
