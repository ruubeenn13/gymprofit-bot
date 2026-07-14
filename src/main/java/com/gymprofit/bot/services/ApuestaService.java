package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Juegos de azar de ficción (F-ECO-6): coinflip, dado y ruleta. Todo con <b>moneda ficticia</b>
 * (nunca dinero real). Se cobra la apuesta al jugar y se paga el premio si gana. Las tablas de pago
 * dan una pequeña <b>ventaja a la casa</b> (valor esperado &lt; 1) para que el juego sea un sumidero
 * neto y no infle la economía. Límites de apuesta para acotar el riesgo. El azar se inyecta para
 * tests deterministas.
 */
public final class ApuestaService {

    /** Apuesta mínima y máxima. */
    public static final long APUESTA_MIN = 10;
    public static final long APUESTA_MAX = 10000;

    /** Multiplicadores de pago (lo que devuelve una apuesta ganadora, incluida la propia apuesta). */
    public static final int PAGO_COINFLIP = 2;   // 50 % -> EV 1.0 (sin ventaja; variedad)
    public static final int PAGO_DADO = 5;        // 1/6 -> EV 0.833
    public static final int PAGO_RULETA_COLOR = 2; // 18/37 -> EV 0.973
    public static final int PAGO_RULETA_VERDE = 30; // 1/37 -> EV 0.811

    /** Color de la ruleta (0 = verde, 1-18 rojo, 19-36 negro). */
    public enum Color { ROJO, NEGRO, VERDE }

    public enum Estado { OK, APUESTA_INVALIDA, SIN_SALDO }

    /**
     * Resultado de una apuesta.
     *
     * @param estado   validación
     * @param gano     si el jugador ganó
     * @param ganancia coins netos (positivo si gana, {@code -apuesta} si pierde)
     * @param tirada   resultado sorteado (cara 0/1, dado 1-6, ruleta 0-36)
     */
    public record Resultado(Estado estado, boolean gano, long ganancia, int tirada) {
    }

    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;

    public ApuestaService(EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios,
                          BatallaService.Aleatorio azar) {
        this.economia = economia;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public ApuestaService(EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios) {
        this(economia, usuarios, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Cara o cruz: {@code caraElegida} true = cara. Paga el doble. */
    public Resultado coinflip(long discordId, long apuesta, boolean caraElegida) {
        Estado cobro = cobrar(discordId, apuesta);
        if (cobro != Estado.OK) {
            return new Resultado(cobro, false, 0, 0);
        }
        boolean salioCara = azar.next() < 0.5;
        return resolver(discordId, apuesta, salioCara == caraElegida, PAGO_COINFLIP,
                salioCara ? 1 : 0);
    }

    /** Dado 1-6: aciertas el número y cobras 5×. */
    public Resultado dado(long discordId, long apuesta, int numero) {
        if (numero < 1 || numero > 6) {
            return new Resultado(Estado.APUESTA_INVALIDA, false, 0, 0);
        }
        Estado cobro = cobrar(discordId, apuesta);
        if (cobro != Estado.OK) {
            return new Resultado(cobro, false, 0, 0);
        }
        int tirada = 1 + (int) (azar.next() * 6);
        return resolver(discordId, apuesta, tirada == numero, PAGO_DADO, tirada);
    }

    /** Ruleta: apuestas a un color; rojo/negro pagan 2×, verde 30×. */
    public Resultado ruleta(long discordId, long apuesta, Color eleccion) {
        Estado cobro = cobrar(discordId, apuesta);
        if (cobro != Estado.OK) {
            return new Resultado(cobro, false, 0, 0);
        }
        int slot = (int) (azar.next() * 37); // 0-36
        Color salio = slot == 0 ? Color.VERDE : (slot <= 18 ? Color.ROJO : Color.NEGRO);
        int pago = eleccion == Color.VERDE ? PAGO_RULETA_VERDE : PAGO_RULETA_COLOR;
        return resolver(discordId, apuesta, salio == eleccion, pago, slot);
    }

    /** Cobra la apuesta (valida límites y saldo). */
    private Estado cobrar(long discordId, long apuesta) {
        if (apuesta < APUESTA_MIN || apuesta > APUESTA_MAX) {
            return Estado.APUESTA_INVALIDA;
        }
        usuarios.obtenerOCrear(discordId);
        return economia.gastar(discordId, apuesta, "apuesta") ? Estado.OK : Estado.SIN_SALDO;
    }

    /** Aplica el desenlace: si gana, ingresa el premio; devuelve la ganancia neta. */
    private Resultado resolver(long discordId, long apuesta, boolean gano, int pago, int tirada) {
        if (gano) {
            long premio = apuesta * pago;
            economia.ingresar(discordId, premio, "premio_apuesta");
            return new Resultado(Estado.OK, true, premio - apuesta, tirada);
        }
        return new Resultado(Estado.OK, false, -apuesta, tirada);
    }
}
