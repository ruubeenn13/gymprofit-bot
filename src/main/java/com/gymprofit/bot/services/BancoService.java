package com.gymprofit.bot.services;

import com.gymprofit.bot.db.BancoCuenta;
import com.gymprofit.bot.db.BancoRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Banco (F-ECO-4c): ahorro con interés y préstamos, separado del monedero. El ahorro genera un
 * interés diario pequeño con tope (se aplica de forma <b>perezosa</b>: por los días transcurridos
 * desde la última vez, al interactuar). Los préstamos adelantan coins con una comisión que se paga al
 * devolverlos (sumidero). El límite del préstamo crece con el nivel.
 */
public final class BancoService {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    /** Interés diario del ahorro. */
    public static final double INTERES_DIARIO = 0.02;
    /** Tope de interés por día (evita que el ahorro dispare la inflación). */
    public static final long INTERES_MAX_DIA = 500;
    /** Comisión del préstamo (se devuelve de más; sumidero). */
    public static final double FEE_PRESTAMO = 0.10;
    /** Límite de préstamo base y por nivel. */
    public static final long LIMITE_BASE = 1000;
    public static final long LIMITE_POR_NIVEL = 500;

    public enum Estado {
        OK, SIN_SALDO, SIN_FONDOS, CANTIDAD_INVALIDA, YA_DEUDA, SUPERA_LIMITE, SIN_DEUDA
    }

    /** Estado del banco para {@code /banco}. */
    public record Vista(long saldoBanco, long deuda, long monedero) {
    }

    /**
     * Resultado de una operación. {@code valor} lleva el dato relevante: cantidad movida (depósito/
     * retiro), deuda contraída (préstamo OK), deuda restante (pago OK) o el límite (SUPERA_LIMITE).
     */
    public record Resultado(Estado estado, long valor) {
    }

    private final BancoRepositorio banco;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public BancoService(BancoRepositorio banco, EconomiaRepositorio economia,
                        UsuarioDiscordRepositorio usuarios) {
        this.banco = banco;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Interés acumulado de un ahorro entre {@code ultimo} y {@code hoy} (con tope diario). Puro. */
    public static long interesGanado(long saldo, LocalDate ultimo, LocalDate hoy) {
        if (saldo <= 0 || ultimo == null) {
            return 0;
        }
        long dias = ChronoUnit.DAYS.between(ultimo, hoy);
        if (dias <= 0) {
            return 0;
        }
        return Math.min(INTERES_MAX_DIA * dias, Math.round(saldo * INTERES_DIARIO * dias));
    }

    /** Estado del banco (aplica el interés pendiente). */
    public Vista ver(long discordId) {
        usuarios.obtenerOCrear(discordId);
        BancoCuenta c = aplicar(discordId);
        return new Vista(c.saldo(), c.prestamo(), economia.saldo(discordId));
    }

    /** Mueve coins del monedero al ahorro. */
    public Resultado depositar(long discordId, long cantidad) {
        usuarios.obtenerOCrear(discordId);
        BancoCuenta c = aplicar(discordId);
        if (cantidad <= 0) {
            return new Resultado(Estado.CANTIDAD_INVALIDA, 0);
        }
        if (!economia.gastar(discordId, cantidad, "deposito")) {
            return new Resultado(Estado.SIN_SALDO, 0);
        }
        banco.guardar(new BancoCuenta(discordId, c.saldo() + cantidad, c.prestamo(), c.ultimoInteres()));
        return new Resultado(Estado.OK, cantidad);
    }

    /** Mueve coins del ahorro al monedero. */
    public Resultado retirar(long discordId, long cantidad) {
        usuarios.obtenerOCrear(discordId);
        BancoCuenta c = aplicar(discordId);
        if (cantidad <= 0) {
            return new Resultado(Estado.CANTIDAD_INVALIDA, 0);
        }
        if (c.saldo() < cantidad) {
            return new Resultado(Estado.SIN_FONDOS, 0);
        }
        banco.guardar(new BancoCuenta(discordId, c.saldo() - cantidad, c.prestamo(), c.ultimoInteres()));
        economia.ingresar(discordId, cantidad, "retiro_banco");
        return new Resultado(Estado.OK, cantidad);
    }

    /** Pide un préstamo (hasta el límite por nivel); adelanta coins y contrae deuda con comisión. */
    public Resultado prestamo(long discordId, long cantidad) {
        usuarios.obtenerOCrear(discordId);
        BancoCuenta c = aplicar(discordId);
        if (cantidad <= 0) {
            return new Resultado(Estado.CANTIDAD_INVALIDA, 0);
        }
        if (c.prestamo() > 0) {
            return new Resultado(Estado.YA_DEUDA, c.prestamo());
        }
        int nivel = usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0);
        long limite = LIMITE_BASE + (long) nivel * LIMITE_POR_NIVEL;
        if (cantidad > limite) {
            return new Resultado(Estado.SUPERA_LIMITE, limite);
        }
        long deuda = Math.round(cantidad * (1 + FEE_PRESTAMO));
        banco.guardar(new BancoCuenta(discordId, c.saldo(), deuda, c.ultimoInteres()));
        economia.ingresar(discordId, cantidad, "prestamo");
        return new Resultado(Estado.OK, deuda);
    }

    /** Paga (parte de) la deuda del préstamo con coins del monedero. */
    public Resultado pagar(long discordId, long cantidad) {
        usuarios.obtenerOCrear(discordId);
        BancoCuenta c = aplicar(discordId);
        if (cantidad <= 0) {
            return new Resultado(Estado.CANTIDAD_INVALIDA, 0);
        }
        if (c.prestamo() <= 0) {
            return new Resultado(Estado.SIN_DEUDA, 0);
        }
        long pago = Math.min(cantidad, c.prestamo());
        if (!economia.gastar(discordId, pago, "pago_prestamo")) {
            return new Resultado(Estado.SIN_SALDO, 0);
        }
        long restante = c.prestamo() - pago;
        banco.guardar(new BancoCuenta(discordId, c.saldo(), restante, c.ultimoInteres()));
        return new Resultado(Estado.OK, restante);
    }

    /** Aplica el interés pendiente al ahorro y persiste (deja {@code ultimo_interes} en hoy). */
    private BancoCuenta aplicar(long discordId) {
        BancoCuenta c = banco.obtenerOCrear(discordId);
        LocalDate hoy = LocalDate.now(ZONA);
        long interes = interesGanado(c.saldo(), c.ultimoInteres(), hoy);
        BancoCuenta actualizada = new BancoCuenta(discordId, c.saldo() + interes, c.prestamo(), hoy);
        banco.guardar(actualizada);
        return actualizada;
    }
}
