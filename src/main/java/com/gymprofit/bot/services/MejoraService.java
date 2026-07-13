package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.MejoraRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Optional;
import java.util.Set;

/**
 * Lógica del árbol de mejoras: comprar un nodo (valida prerrequisito y saldo, paga y aplica el bonus
 * de atributo) y consultar los nodos comprados. Progresión lenta: cada nodo es más caro que el
 * anterior.
 */
public final class MejoraService {

    public enum EstadoMejora { OK, NO_EXISTE, YA_TIENES, BLOQUEADO, SIN_SALDO }

    /** Resultado de comprar una mejora: estado y (si OK) el nodo comprado. */
    public record ResultadoMejora(EstadoMejora estado, Optional<Mejoras> nodo) {
    }

    private final MejoraRepositorio mejoras;
    private final EconomiaRepositorio economia;
    private final PersonajeRepositorio personajes;
    private final UsuarioDiscordRepositorio usuarios;

    public MejoraService(MejoraRepositorio mejoras, EconomiaRepositorio economia,
                         PersonajeRepositorio personajes, UsuarioDiscordRepositorio usuarios) {
        this.mejoras = mejoras;
        this.economia = economia;
        this.personajes = personajes;
        this.usuarios = usuarios;
    }

    /** Compra un nodo del árbol si procede; aplica el bonus al atributo. */
    public ResultadoMejora comprar(long discordId, String nodoId) {
        Optional<Mejoras> nodo = Mejoras.porId(nodoId);
        if (nodo.isEmpty()) {
            return new ResultadoMejora(EstadoMejora.NO_EXISTE, Optional.empty());
        }
        Mejoras m = nodo.get();
        usuarios.obtenerOCrear(discordId);
        personajes.obtenerOCrear(discordId);
        if (mejoras.tiene(discordId, nodoId)) {
            return new ResultadoMejora(EstadoMejora.YA_TIENES, nodo);
        }
        if (m.prereq() != null && !mejoras.tiene(discordId, m.prereq())) {
            return new ResultadoMejora(EstadoMejora.BLOQUEADO, nodo);
        }
        if (!economia.gastar(discordId, m.precio(), "mejora")) {
            return new ResultadoMejora(EstadoMejora.SIN_SALDO, nodo);
        }
        mejoras.comprar(discordId, nodoId);
        personajes.sumarAtributo(discordId, m.atributo(), m.valor());
        return new ResultadoMejora(EstadoMejora.OK, nodo);
    }

    /** Nodos que el usuario ya ha comprado (para pintar el árbol). */
    public Set<String> comprados(long discordId) {
        return mejoras.comprados(discordId);
    }
}
