package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.Cofres.Premio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lógica de {@code /abrir} y {@code /cofres} (sistema de cofres). Abrir un cofre lo descuenta del
 * inventario y tira un premio de su tabla por peso (a menos peso, más raro), entregándolo según su
 * tipo (ítem, coins, encantamiento o nivel de arma). Los premios de encanto/nivel sin arma equipada
 * se convierten en coins para no desperdiciarse. El azar se inyecta para tests deterministas. El
 * balance es de <b>sumidero</b>: el valor esperado de cada cofre es menor que su precio.
 */
public final class CofreService {

    /** Estado de una apertura. */
    public enum Estado { OK, NO_ES_COFRE, NO_TIENE, CANTIDAD_INVALIDA }

    /**
     * Un premio ya concedido (para el embed).
     *
     * @param tipo     tipo de premio efectivamente entregado
     * @param ref      id de ítem/encanto si aplica (o {@code null})
     * @param cantidad unidades de ítem (o 0)
     * @param coins    coins entregados (o 0)
     * @param rareza   rareza del premio original
     * @param fallback si un encanto/nivel se convirtió en coins por no tener arma
     */
    public record Obtenido(Cofres.Tipo tipo, String ref, int cantidad, long coins, Rareza rareza,
                           boolean fallback) {
    }

    /** Resultado de abrir. */
    public record Resultado(Estado estado, List<Obtenido> premios) {
    }

    /** Coins de consolación si sale un encanto sin arma equipada (mitad de su precio). */
    private static final double FALLBACK_ENCANTO = 0.5;
    /** Coins de consolación si sale un nivel de arma sin arma equipada. */
    private static final long FALLBACK_NIVEL = 250;

    private final InventarioRepositorio inventario;
    private final EconomiaRepositorio economia;
    private final PersonajeRepositorio personajes;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;

    public CofreService(InventarioRepositorio inventario, EconomiaRepositorio economia,
                        PersonajeRepositorio personajes, UsuarioDiscordRepositorio usuarios,
                        BatallaService.Aleatorio azar) {
        this.inventario = inventario;
        this.economia = economia;
        this.personajes = personajes;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public CofreService(InventarioRepositorio inventario, EconomiaRepositorio economia,
                        PersonajeRepositorio personajes, UsuarioDiscordRepositorio usuarios) {
        this(inventario, economia, personajes, usuarios,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Abre {@code cantidad} cofres del tipo dado, entregando un premio por cada uno. */
    public Resultado abrir(long discordId, String cofreId, int cantidad) {
        if (cantidad <= 0) {
            return new Resultado(Estado.CANTIDAD_INVALIDA, List.of());
        }
        Optional<Cofres> cofre = Cofres.porId(cofreId);
        if (cofre.isEmpty()) {
            return new Resultado(Estado.NO_ES_COFRE, List.of());
        }
        usuarios.obtenerOCrear(discordId);
        if (!inventario.quitar(discordId, cofreId, cantidad)) {
            return new Resultado(Estado.NO_TIENE, List.of());
        }
        List<Obtenido> premios = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            premios.add(conceder(discordId, elegir(cofre.get())));
        }
        return new Resultado(Estado.OK, premios);
    }

    /** Valor esperado del botín de un cofre (para verificar que es un sumidero: {@code < precio}). */
    public static double valorEsperado(Cofres cofre) {
        int total = cofre.pesoTotal();
        double ev = 0;
        for (Premio p : cofre.tabla()) {
            ev += (double) p.peso() / total * valor(p);
        }
        return ev;
    }

    /** Valor aproximado de un premio (en coins), para el cálculo del valor esperado. */
    private static double valor(Premio p) {
        return switch (p.tipo()) {
            case COINS -> media(p.min(), p.max());
            case ITEM -> VentaService.precioVenta(Items.porId(p.ref()).orElseThrow())
                    * media(p.min(), p.max());
            case ENCANTO -> Encantamiento.porId(p.ref()).orElseThrow().precio();
            case NIVEL -> EncantarService.costeNivel(0);
        };
    }

    private static double media(int min, int max) {
        return (min + max) / 2.0;
    }

    // ---------------------- internos ----------------------

    /** Elige un premio de la tabla por peso. */
    private Premio elegir(Cofres cofre) {
        int objetivo = (int) (azar.next() * cofre.pesoTotal());
        int acum = 0;
        for (Premio p : cofre.tabla()) {
            acum += p.peso();
            if (objetivo < acum) {
                return p;
            }
        }
        return cofre.tabla().get(cofre.tabla().size() - 1);
    }

    /** Entrega un premio y devuelve lo concedido (con conversión a coins si no hay arma). */
    private Obtenido conceder(long discordId, Premio p) {
        switch (p.tipo()) {
            case ITEM -> {
                int cant = rango(p.min(), p.max());
                inventario.anadir(discordId, p.ref(), cant);
                return new Obtenido(Cofres.Tipo.ITEM, p.ref(), cant, 0, p.rareza(), false);
            }
            case COINS -> {
                long coins = rango(p.min(), p.max());
                economia.ingresar(discordId, coins, "cofre");
                return new Obtenido(Cofres.Tipo.COINS, null, 0, coins, p.rareza(), false);
            }
            case ENCANTO -> {
                Personaje pj = personajes.obtenerOCrear(discordId);
                if (pj.arma() != null) {
                    personajes.fijarEncanto(discordId, p.ref());
                    return new Obtenido(Cofres.Tipo.ENCANTO, p.ref(), 0, 0, p.rareza(), false);
                }
                long coins = Math.round(Encantamiento.porId(p.ref()).orElseThrow().precio()
                        * FALLBACK_ENCANTO);
                economia.ingresar(discordId, coins, "cofre");
                return new Obtenido(Cofres.Tipo.COINS, null, 0, coins, p.rareza(), true);
            }
            case NIVEL -> {
                Personaje pj = personajes.obtenerOCrear(discordId);
                if (pj.arma() != null && pj.armaNivel() < EncantarService.NIVEL_MAX) {
                    personajes.subirNivelArma(discordId);
                    return new Obtenido(Cofres.Tipo.NIVEL, null, 0, 0, p.rareza(), false);
                }
                economia.ingresar(discordId, FALLBACK_NIVEL, "cofre");
                return new Obtenido(Cofres.Tipo.COINS, null, 0, FALLBACK_NIVEL, p.rareza(), true);
            }
            default -> throw new IllegalStateException("Tipo de premio no soportado: " + p.tipo());
        }
    }

    /** Entero aleatorio en {@code [min, max]}. */
    private int rango(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + (int) (azar.next() * (max - min + 1));
    }
}
