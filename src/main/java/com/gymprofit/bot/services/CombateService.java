package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Optional;

/**
 * Lógica de combate (COMBAT-1): equipar/desequipar arma y armadura y calcular el
 * <b>poder de combate</b>. Equipar solo referencia el ítem (no lo consume del inventario): exige
 * poseerlo y persiste su id en la ranura del personaje. El poder de combate resume la fuerza bruta
 * del personaje para las fases de pelea (COMBAT-2+): fuerza + resistencia + ataque del arma +
 * defensa de la armadura.
 */
public final class CombateService {

    /** Resultado de equipar: por qué falló o, si {@code OK}, ranura y puntos aportados. */
    public enum EstadoEquipar { OK, NO_EXISTE, NO_EQUIPABLE, NO_TIENE }

    /** Ranuras de equipo (coinciden con las columnas de {@code personajes}). */
    public static final String RANURA_ARMA = "arma";
    public static final String RANURA_ARMADURA = "armadura";

    /**
     * @param estado resultado de la operación
     * @param ranura ranura afectada ({@code arma}/{@code armadura}), o {@code null} si falló
     * @param valor  puntos aportados (ataque o defensa) si {@code OK}
     */
    public record ResultadoEquipar(EstadoEquipar estado, String ranura, int valor) {
    }

    private final PersonajeRepositorio personajes;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;

    public CombateService(PersonajeRepositorio personajes, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios) {
        this.personajes = personajes;
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** Equipa un arma o armadura del inventario en su ranura. No consume el ítem. */
    public ResultadoEquipar equipar(long discordId, String itemId) {
        Optional<Items> item = Items.porId(itemId);
        if (item.isEmpty()) {
            return new ResultadoEquipar(EstadoEquipar.NO_EXISTE, null, 0);
        }
        Items i = item.get();
        if (!i.esEquipable()) {
            return new ResultadoEquipar(EstadoEquipar.NO_EQUIPABLE, null, 0);
        }
        usuarios.obtenerOCrear(discordId);
        personajes.obtenerOCrear(discordId);
        if (inventario.cantidad(discordId, itemId) <= 0) {
            return new ResultadoEquipar(EstadoEquipar.NO_TIENE, null, 0);
        }
        boolean esArma = i.categoria() == Items.Categoria.ARMA;
        String ranura = esArma ? RANURA_ARMA : RANURA_ARMADURA;
        personajes.fijarEquipo(discordId, ranura, itemId);
        return new ResultadoEquipar(EstadoEquipar.OK, ranura, esArma ? i.ataque() : i.defensa());
    }

    /** Desequipa una ranura ({@code arma}/{@code armadura}). Devuelve {@code false} si ya estaba vacía. */
    public boolean desequipar(long discordId, String ranura) {
        if (!ranura.equals(RANURA_ARMA) && !ranura.equals(RANURA_ARMADURA)) {
            throw new IllegalArgumentException("Ranura no válida: " + ranura);
        }
        usuarios.obtenerOCrear(discordId);
        Personaje p = personajes.obtenerOCrear(discordId);
        String actual = ranura.equals(RANURA_ARMA) ? p.arma() : p.armadura();
        if (actual == null) {
            return false;
        }
        personajes.fijarEquipo(discordId, ranura, null);
        return true;
    }

    /**
     * Poder de combate = fuerza + resistencia + ataque del arma + defensa de la armadura. Equivale a
     * {@code ataqueDe(p) + defensaDe(p)} (cada uno ya suma su atributo base + su equipo).
     */
    public static int poderCombate(Personaje p) {
        return ataqueDe(p) + defensaDe(p);
    }

    // ---------------------- Matemática de batalla (COMBAT-3) ----------------------

    /** HP base del combate (con 0 de resistencia). */
    public static final int HP_BASE = 80;
    /** HP de combate que aporta cada punto de resistencia. */
    public static final int HP_POR_RESISTENCIA = 10;

    /**
     * HP de combate del jugador = base + resistencia·k. Es independiente de la <i>salud</i> general
     * (bienestar): la salud la mueven los consumibles; el HP es solo de la pelea y se recalcula en
     * cada combate.
     */
    public static int hpCombate(Personaje p) {
        return HP_BASE + p.resistencia() * HP_POR_RESISTENCIA;
    }

    /** Ofensiva del jugador = fuerza + ataque del arma equipada. */
    public static int ataqueDe(Personaje p) {
        int arma = p.arma() == null ? 0 : Items.porId(p.arma()).map(Items::ataque).orElse(0);
        return p.fuerza() + arma;
    }

    /** Defensa del jugador = resistencia + defensa de la armadura equipada. */
    public static int defensaDe(Personaje p) {
        int armadura = p.armadura() == null ? 0
                : Items.porId(p.armadura()).map(Items::defensa).orElse(0);
        return p.resistencia() + armadura;
    }

    /**
     * Daño de un golpe: {@code (ofensiva − defensa)} con suelo en 1, multiplicado por un factor de
     * azar y redondeado (mínimo 1). Función pura para poder testear el balance de forma determinista.
     *
     * @param ofensiva ataque del atacante
     * @param defensa  defensa del defensor
     * @param factor   multiplicador de azar (típicamente ~0.85–1.15)
     */
    public static int dano(int ofensiva, int defensa, double factor) {
        int base = Math.max(1, ofensiva - defensa);
        return Math.max(1, (int) Math.round(base * factor));
    }
}
