package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.Optional;

/**
 * Lógica de {@code /vender} (COMBAT-5a): convierte ítems del inventario en coins. Los <b>minerales</b>
 * se venden a su valor completo (su {@code precio} en {@link Items}); el resto (equipo, loot,
 * consumibles) a la mitad, para que no haya arbitraje comprando barato y revendiendo. La quita del
 * inventario es atómica; el ingreso pasa por el ledger.
 */
public final class VentaService {

    /** Fracción del precio que se paga por ítems no minerales. */
    public static final double FRACCION_NO_MINERAL = 0.5;

    /** Estado de una venta. */
    public enum Estado { OK, NO_EXISTE, NO_TIENE, CANTIDAD_INVALIDA, NO_VENDIBLE }

    /**
     * @param estado       resultado
     * @param total        coins ingresados
     * @param precioUnidad precio de venta por unidad
     */
    public record Resultado(Estado estado, long total, long precioUnidad) {
    }

    private final InventarioRepositorio inventario;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;

    public VentaService(InventarioRepositorio inventario, EconomiaRepositorio economia,
                        UsuarioDiscordRepositorio usuarios) {
        this.inventario = inventario;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Precio de venta unitario de un ítem (mineral: completo; resto: la mitad, suelo 1). */
    public static long precioVenta(Items item) {
        if (item.categoria() == Items.Categoria.MINERAL) {
            return item.precio();
        }
        return Math.max(1, Math.round(item.precio() * FRACCION_NO_MINERAL));
    }

    /** Vende {@code cantidad} unidades de un ítem del inventario. */
    public Resultado vender(long discordId, String itemId, int cantidad) {
        if (cantidad <= 0) {
            return new Resultado(Estado.CANTIDAD_INVALIDA, 0, 0);
        }
        Optional<Items> item = Items.porId(itemId);
        if (item.isEmpty()) {
            return new Resultado(Estado.NO_EXISTE, 0, 0);
        }
        long precioUnidad = precioVenta(item.get());
        if (precioUnidad <= 0 || item.get().precio() <= 0) {
            return new Resultado(Estado.NO_VENDIBLE, 0, 0);
        }
        usuarios.obtenerOCrear(discordId);
        if (!inventario.quitar(discordId, itemId, cantidad)) {
            return new Resultado(Estado.NO_TIENE, 0, precioUnidad);
        }
        long total = precioUnidad * cantidad;
        economia.ingresar(discordId, total, "vender:" + itemId);
        return new Resultado(Estado.OK, total, precioUnidad);
    }
}
