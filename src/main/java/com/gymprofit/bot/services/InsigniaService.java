package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InsigniaRepositorio;
import com.gymprofit.bot.db.MineriaRepositorio;
import com.gymprofit.bot.db.MundoRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lógica de insignias (F-ECO-3b). Evalúa las condiciones del catálogo sobre el <b>estado actual</b>
 * del jugador (sin necesidad de contadores ni hooks) y persiste las nuevas. {@code /insignias} llama
 * a {@link #revisar} (otorga las recién cumplidas) y luego lista todas con su estado.
 */
public final class InsigniaService {

    /** Snapshot del estado del jugador para evaluar condiciones (permite tests puros). */
    public record Estado(int nivel, long coins, int poder, int mineria, int mundos, int estudios,
                         boolean tieneTrabajo) {
    }

    /** Vista de una insignia para el listado: definición + si está ganada. */
    public record Vista(Insignias insignia, boolean ganada) {
    }

    private final InsigniaRepositorio insignias;
    private final UsuarioDiscordRepositorio usuarios;
    private final PersonajeRepositorio personajes;
    private final MineriaRepositorio mineria;
    private final MundoRepositorio mundos;

    public InsigniaService(InsigniaRepositorio insignias, UsuarioDiscordRepositorio usuarios,
                           PersonajeRepositorio personajes, MineriaRepositorio mineria,
                           MundoRepositorio mundos) {
        this.insignias = insignias;
        this.usuarios = usuarios;
        this.personajes = personajes;
        this.mineria = mineria;
        this.mundos = mundos;
    }

    /** ¿Cumple el estado la condición de la insignia? Función pura (testeable). */
    public static boolean cumple(Insignias i, Estado e) {
        return switch (i.tipo()) {
            case NIVEL -> e.nivel() >= i.umbral();
            case COINS -> e.coins() >= i.umbral();
            case PODER -> e.poder() >= i.umbral();
            case MINERIA -> e.mineria() >= i.umbral();
            case MUNDOS -> e.mundos() >= i.umbral();
            case ESTUDIOS -> e.estudios() >= i.umbral();
            case TRABAJO -> e.tieneTrabajo();
        };
    }

    /** Otorga las insignias recién cumplidas y devuelve las nuevas de esta revisión. */
    public List<Insignias> revisar(long discordId) {
        Estado estado = estado(discordId);
        Set<String> ganadas = insignias.ganadas(discordId);
        List<Insignias> nuevas = new ArrayList<>();
        for (Insignias i : Insignias.CATALOGO) {
            if (!ganadas.contains(i.id()) && cumple(i, estado)) {
                insignias.otorgar(discordId, i.id());
                nuevas.add(i);
            }
        }
        return nuevas;
    }

    /** Todas las insignias con si el jugador las tiene (tras revisar, para incluir las nuevas). */
    public List<Vista> listar(long discordId) {
        revisar(discordId);
        Set<String> ganadas = insignias.ganadas(discordId);
        List<Vista> vistas = new ArrayList<>();
        for (Insignias i : Insignias.CATALOGO) {
            vistas.add(new Vista(i, ganadas.contains(i.id())));
        }
        return vistas;
    }

    /** Construye el snapshot del estado actual del jugador desde los repositorios. */
    private Estado estado(long discordId) {
        UsuarioDiscord u = usuarios.obtenerOCrear(discordId);
        Personaje p = personajes.obtenerOCrear(discordId);
        int poder = CombateService.poderCombate(p);
        int nivelMineria = mineria.obtenerOCrear(discordId).nivelMineria();
        int mundosHechos = mundos.completados(discordId).size();
        return new Estado(u.nivel(), u.coins(), poder, nivelMineria, mundosHechos,
                p.estudios(), p.trabajo() != null);
    }
}
