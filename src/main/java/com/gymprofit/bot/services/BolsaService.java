package com.gymprofit.bot.services;

import com.gymprofit.bot.db.BolsaRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Posicion;
import com.gymprofit.bot.db.PrecioAccion;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bolsa ficticia (extra): invertir y vender acciones cuyo precio se mueve solo ({@link #tick}, que
 * ejecuta el job de mercado). Comprar/vender lleva una pequeña comisión (sumidero). El P/L de la
 * cartera se calcula con el coste invertido guardado por posición. Todo es ficción. El azar del tick
 * se inyecta para tests.
 */
public final class BolsaService {

    /** Comisión de compra/venta (sumidero). */
    public static final double COMISION = 0.01;
    /** Probabilidad de evento de mercado (crash/boom) por acción y tick. */
    public static final double EVENTO_PROB = 0.06;

    public enum EstadoInvertir { OK, NO_EXISTE, SIN_SALDO, CANTIDAD_INVALIDA }

    public enum EstadoVender { OK, NO_EXISTE, SIN_ACCIONES, CANTIDAD_INVALIDA }

    public record ResultadoInvertir(EstadoInvertir estado, long cantidad, long precioUnit, long coste) {
    }

    public record ResultadoVender(EstadoVender estado, long cantidad, long neto) {
    }

    /** Una posición valorada a precio actual, con su P/L. */
    public record PosicionVista(String accionId, long cantidad, long coste, long valor, long pl) {
    }

    /** Cartera completa: posiciones + valor total y P/L total. */
    public record CarteraVista(List<PosicionVista> posiciones, long valorTotal, long plTotal) {
    }

    private final BolsaRepositorio bolsa;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final BatallaService.Aleatorio azar;

    public BolsaService(BolsaRepositorio bolsa, EconomiaRepositorio economia,
                        UsuarioDiscordRepositorio usuarios, BatallaService.Aleatorio azar) {
        this.bolsa = bolsa;
        this.economia = economia;
        this.usuarios = usuarios;
        this.azar = azar;
    }

    /** Constructor de producción: azar real. */
    public BolsaService(BolsaRepositorio bolsa, EconomiaRepositorio economia,
                        UsuarioDiscordRepositorio usuarios) {
        this(bolsa, economia, usuarios, () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Precios actuales (para {@code /bolsa}). */
    public List<PrecioAccion> precios() {
        return bolsa.precios();
    }

    /** Compra {@code cantidad} acciones al precio actual (cobra precio·cantidad + comisión). */
    public ResultadoInvertir invertir(long discordId, String accionId, long cantidad) {
        if (cantidad <= 0) {
            return new ResultadoInvertir(EstadoInvertir.CANTIDAD_INVALIDA, 0, 0, 0);
        }
        Optional<PrecioAccion> p = bolsa.precio(accionId);
        if (p.isEmpty()) {
            return new ResultadoInvertir(EstadoInvertir.NO_EXISTE, 0, 0, 0);
        }
        long total = p.get().precio() * cantidad;
        long coste = total + Math.round(total * COMISION);
        usuarios.obtenerOCrear(discordId);
        if (!economia.gastar(discordId, coste, "invertir:" + accionId)) {
            return new ResultadoInvertir(EstadoInvertir.SIN_SALDO, 0, p.get().precio(), coste);
        }
        bolsa.comprar(discordId, accionId, cantidad, total);
        return new ResultadoInvertir(EstadoInvertir.OK, cantidad, p.get().precio(), coste);
    }

    /** Vende {@code cantidad} acciones al precio actual (ingresa el neto tras comisión). */
    public ResultadoVender vender(long discordId, String accionId, long cantidad) {
        if (cantidad <= 0) {
            return new ResultadoVender(EstadoVender.CANTIDAD_INVALIDA, 0, 0);
        }
        Optional<PrecioAccion> p = bolsa.precio(accionId);
        if (p.isEmpty()) {
            return new ResultadoVender(EstadoVender.NO_EXISTE, 0, 0);
        }
        Optional<Posicion> pos = bolsa.posicion(discordId, accionId);
        if (pos.isEmpty() || pos.get().cantidad() < cantidad) {
            return new ResultadoVender(EstadoVender.SIN_ACCIONES, 0, 0);
        }
        long bruto = p.get().precio() * cantidad;
        long neto = bruto - Math.round(bruto * COMISION);
        usuarios.obtenerOCrear(discordId);
        economia.ingresar(discordId, neto, "vender_acciones:" + accionId);
        long nuevaCant = pos.get().cantidad() - cantidad;
        long nuevoCoste = nuevaCant == 0 ? 0
                : Math.round(pos.get().coste() * (double) nuevaCant / pos.get().cantidad());
        bolsa.fijarPosicion(discordId, accionId, nuevaCant, nuevoCoste);
        return new ResultadoVender(EstadoVender.OK, cantidad, neto);
    }

    /** Cartera del jugador valorada a precio actual. */
    public CarteraVista cartera(long discordId) {
        List<PosicionVista> vistas = new ArrayList<>();
        long valorTotal = 0;
        long plTotal = 0;
        for (Posicion pos : bolsa.cartera(discordId)) {
            long precio = bolsa.precio(pos.accionId()).map(PrecioAccion::precio).orElse(0L);
            long valor = precio * pos.cantidad();
            long pl = valor - pos.coste();
            vistas.add(new PosicionVista(pos.accionId(), pos.cantidad(), pos.coste(), valor, pl));
            valorTotal += valor;
            plTotal += pl;
        }
        return new CarteraVista(vistas, valorTotal, plTotal);
    }

    /** Mueve todos los precios un tick (random walk por volatilidad + eventos). Lo llama el job. */
    public void tick() {
        for (PrecioAccion p : bolsa.precios()) {
            Acciones a = Acciones.porId(p.id()).orElse(null);
            if (a == null) {
                continue;
            }
            long nuevo;
            if (azar.next() < EVENTO_PROB) {
                boolean boom = azar.next() < 0.5;
                nuevo = Math.max(1, Math.round(p.precio() * (boom ? 1.30 : 0.70)));
            } else {
                nuevo = Acciones.mover(p.precio(), a.volatilidad(), azar.next());
            }
            bolsa.actualizarPrecio(a.id(), nuevo);
        }
    }
}
