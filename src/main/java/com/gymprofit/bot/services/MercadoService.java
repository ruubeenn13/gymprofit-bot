package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.ListadoMercado;
import com.gymprofit.bot.db.MercadoRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.List;
import java.util.Optional;

/**
 * Mercado entre jugadores (F-ECO-4b). Publicar retira los ítems del inventario (escrow) y crea un
 * anuncio; comprar cobra al comprador, entrega los ítems y paga al vendedor <b>menos una comisión</b>
 * (sumidero anti-inflación); retirar devuelve los ítems al vendedor. La compra reserva stock de forma
 * atómica para no vender de más ante compras simultáneas, y revierte si el cobro falla.
 */
public final class MercadoService {

    /** Comisión sobre la venta (la paga el vendedor; es un sumidero). */
    public static final double COMISION = 0.05;
    /** Máximo de anuncios que muestra {@code /mercado}. */
    public static final int LIMITE_LISTADO = 20;

    public enum PublicarEstado { OK, NO_TIENE, NO_EXISTE, DATOS_INVALIDOS }

    public enum CompraEstado { OK, NO_EXISTE, ES_TUYO, SIN_STOCK, SIN_SALDO, CANTIDAD_INVALIDA }

    public enum RetirarEstado { OK, NO_EXISTE, NO_TUYO }

    /** Resultado de publicar (id del anuncio si {@code OK}). */
    public record PublicarResultado(PublicarEstado estado, long id) {
    }

    /** Resultado de comprar (ítem, cantidad, total pagado y comisión aplicada). */
    public record CompraResultado(CompraEstado estado, String itemId, int cantidad, long total,
                                  long comision) {
    }

    private final MercadoRepositorio mercado;
    private final InventarioRepositorio inventario;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public MercadoService(MercadoRepositorio mercado, InventarioRepositorio inventario,
                          EconomiaRepositorio economia, UsuarioDiscordRepositorio usuarios) {
        this.mercado = mercado;
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Publica un anuncio: retira los ítems del inventario (escrow) y lo crea. */
    public PublicarResultado publicar(long vendedor, String itemId, int cantidad, long precio) {
        if (cantidad <= 0 || precio <= 0) {
            return new PublicarResultado(PublicarEstado.DATOS_INVALIDOS, 0);
        }
        if (Items.porId(itemId).isEmpty()) {
            return new PublicarResultado(PublicarEstado.NO_EXISTE, 0);
        }
        usuarios.obtenerOCrear(vendedor);
        if (!inventario.quitar(vendedor, itemId, cantidad)) {
            return new PublicarResultado(PublicarEstado.NO_TIENE, 0);
        }
        return new PublicarResultado(PublicarEstado.OK, mercado.crear(vendedor, itemId, cantidad, precio));
    }

    /** Compra {@code cantidad} unidades de un anuncio. */
    public CompraResultado comprar(long comprador, long anuncioId, int cantidad) {
        if (cantidad <= 0) {
            return fallo(CompraEstado.CANTIDAD_INVALIDA);
        }
        Optional<ListadoMercado> op = mercado.buscar(anuncioId);
        if (op.isEmpty()) {
            return fallo(CompraEstado.NO_EXISTE);
        }
        ListadoMercado l = op.get();
        if (l.vendedor() == comprador) {
            return fallo(CompraEstado.ES_TUYO);
        }
        if (cantidad > l.cantidad()) {
            return fallo(CompraEstado.SIN_STOCK);
        }
        long total = l.precio() * cantidad;
        usuarios.obtenerOCrear(comprador);
        usuarios.obtenerOCrear(l.vendedor());
        // Reserva atómica de stock; si el cobro falla, se revierte.
        if (!mercado.reservar(anuncioId, cantidad)) {
            return fallo(CompraEstado.SIN_STOCK);
        }
        if (!economia.gastar(comprador, total, "mercado:" + anuncioId)) {
            mercado.devolver(anuncioId, cantidad);
            return fallo(CompraEstado.SIN_SALDO);
        }
        long comision = Math.round(total * COMISION);
        economia.ingresar(l.vendedor(), total - comision, "venta_mercado:" + anuncioId);
        inventario.anadir(comprador, l.itemId(), cantidad);
        if (cantidad == l.cantidad()) {
            mercado.eliminar(anuncioId); // agotado
        }
        return new CompraResultado(CompraEstado.OK, l.itemId(), cantidad, total, comision);
    }

    /** Retira un anuncio propio y devuelve los ítems al vendedor. */
    public RetirarEstado retirar(long vendedor, long anuncioId) {
        Optional<ListadoMercado> op = mercado.buscar(anuncioId);
        if (op.isEmpty()) {
            return RetirarEstado.NO_EXISTE;
        }
        ListadoMercado l = op.get();
        if (l.vendedor() != vendedor) {
            return RetirarEstado.NO_TUYO;
        }
        inventario.anadir(vendedor, l.itemId(), l.cantidad());
        mercado.eliminar(anuncioId);
        return RetirarEstado.OK;
    }

    /** Anuncios activos para mostrar. */
    public List<ListadoMercado> listar() {
        return mercado.listar(LIMITE_LISTADO);
    }

    private static CompraResultado fallo(CompraEstado estado) {
        return new CompraResultado(estado, null, 0, 0, 0);
    }
}
