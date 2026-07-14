package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Robo de coins entre jugadores (extra, con riesgo). Con cierta probabilidad el ladrón se lleva un
 * porcentaje (con tope) del monedero de la víctima; si falla, paga una <b>multa</b> a la víctima (así
 * el robo es de suma cero entre ambos, no infla la economía). El cooldown que evita el spam lo pone
 * el comando. El azar se inyecta para tests.
 */
public final class RoboService {

    /** Probabilidad de que el robo salga bien. */
    public static final double PROB_EXITO = 0.5;
    /** Fracción del monedero de la víctima que se roba. */
    public static final double PCT_ROBO = 0.15;
    /** Tope de coins por robo. */
    public static final long TOPE_ROBO = 2000;
    /** Multa que paga el ladrón a la víctima si falla. */
    public static final long MULTA = 500;

    public enum Estado { EXITO, FALLO, VICTIMA_SIN_SALDO }

    /** Resultado del robo. {@code cantidad} = botín (éxito) o multa pagada (fallo). */
    public record Resultado(Estado estado, long cantidad) {
    }

    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;

    public RoboService(EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios,
                       BatallaService.Aleatorio azar) {
        this.economia = economia;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public RoboService(EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios) {
        this(economia, usuarios, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Intenta robar a la víctima. */
    public Resultado robar(long ladron, long victima) {
        usuarios.obtenerOCrear(ladron);
        usuarios.obtenerOCrear(victima);
        long saldoVictima = economia.saldo(victima);
        if (saldoVictima <= 0) {
            return new Resultado(Estado.VICTIMA_SIN_SALDO, 0);
        }
        if (azar.next() < PROB_EXITO) {
            long botin = Math.min(TOPE_ROBO, Math.round(saldoVictima * PCT_ROBO));
            botin = Math.max(1, Math.min(botin, saldoVictima));
            economia.gastar(victima, botin, "robado");
            economia.ingresar(ladron, botin, "robo");
            return new Resultado(Estado.EXITO, botin);
        }
        // Falla: el ladrón paga una multa a la víctima (si puede).
        long multa = economia.gastar(ladron, MULTA, "multa_robo") ? MULTA : 0;
        if (multa > 0) {
            economia.ingresar(victima, multa, "compensacion_robo");
        }
        return new Resultado(Estado.FALLO, multa);
    }
}
