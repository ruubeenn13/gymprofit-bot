package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

/**
 * Transferencias entre jugadores (F-ECO-4a): regalar coins o ítems. Cada movimiento se apoya en las
 * operaciones atómicas del monedero/inventario: primero se descuenta al que regala (si no tiene, no
 * se hace nada) y luego se ingresa al que recibe. No hay comisión (los regalos son directos).
 */
public final class RegaloService {

    /** Resultado de un regalo. */
    public enum Estado { OK, SIN_SALDO, NO_TIENE, NO_EXISTE, A_TI_MISMO, CANTIDAD_INVALIDA }

    private final EconomiaRepositorio economia;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;

    public RegaloService(EconomiaRepositorio economia, InventarioRepositorio inventario,
                         UsuarioDiscordRepositorio usuarios) {
        this.economia = economia;
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** Regala {@code cantidad} coins de {@code de} a {@code a}. */
    public Estado regalarCoins(long de, long a, long cantidad) {
        if (cantidad <= 0) {
            return Estado.CANTIDAD_INVALIDA;
        }
        if (de == a) {
            return Estado.A_TI_MISMO;
        }
        usuarios.obtenerOCrear(de);
        usuarios.obtenerOCrear(a);
        if (!economia.gastar(de, cantidad, "regalo:" + a)) {
            return Estado.SIN_SALDO;
        }
        economia.ingresar(a, cantidad, "regalo:" + de);
        return Estado.OK;
    }

    /** Regala {@code cantidad} unidades de un ítem de {@code de} a {@code a}. */
    public Estado regalarItem(long de, long a, String itemId, int cantidad) {
        if (cantidad <= 0) {
            return Estado.CANTIDAD_INVALIDA;
        }
        if (de == a) {
            return Estado.A_TI_MISMO;
        }
        if (Items.porId(itemId).isEmpty()) {
            return Estado.NO_EXISTE;
        }
        usuarios.obtenerOCrear(a);
        if (!inventario.quitar(de, itemId, cantidad)) {
            return Estado.NO_TIENE;
        }
        inventario.anadir(a, itemId, cantidad);
        return Estado.OK;
    }
}
