package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.Items.Efecto;

import java.util.Map;
import java.util.Optional;

/**
 * Lógica de la tienda e inventario: comprar (paga con el monedero, atómico), usar consumibles
 * (aplican energía/salud) y consultar el inventario. Garantiza las filas necesarias por la FK.
 *
 * <p>Los consumibles pasan por la <b>saciedad</b> de {@link DescansoService}: como máximo
 * {@link DescansoService#MAX_CONSUMOS_DIA} al día. Sin ese tope, con coins se compra energía
 * infinita y el ciclo de descanso deja de tener sentido.
 */
public final class ItemService {

    public enum EstadoCompra { OK, NO_EXISTE, SIN_SALDO, CANTIDAD_INVALIDA }

    public enum EstadoUso { OK, NO_EXISTE, NO_CONSUMIBLE, NO_TIENE, LLENO }

    /** Resultado de comprar: estado y coste total. */
    public record ResultadoCompra(EstadoCompra estado, long coste) {
    }

    /** Resultado de usar un consumible: estado, efecto y magnitud aplicada. */
    public record ResultadoUso(EstadoUso estado, Efecto efecto, int valor) {
    }

    private final EconomiaRepositorio economia;
    private final InventarioRepositorio inventario;
    private final PersonajeRepositorio personajes;
    private final UsuarioDiscordRepositorio usuarios;
    private final DescansoService descanso;

    public ItemService(EconomiaRepositorio economia, InventarioRepositorio inventario,
                       PersonajeRepositorio personajes, UsuarioDiscordRepositorio usuarios,
                       DescansoService descanso) {
        this.economia = economia;
        this.inventario = inventario;
        this.personajes = personajes;
        this.usuarios = usuarios;
        this.descanso = descanso;
    }

    /** Compra {@code cantidad} unidades de un ítem, pagando con coins. */
    public ResultadoCompra comprar(long discordId, String itemId, int cantidad) {
        if (cantidad <= 0) {
            return new ResultadoCompra(EstadoCompra.CANTIDAD_INVALIDA, 0);
        }
        Optional<Items> item = Items.porId(itemId);
        if (item.isEmpty()) {
            return new ResultadoCompra(EstadoCompra.NO_EXISTE, 0);
        }
        usuarios.obtenerOCrear(discordId);
        long coste = item.get().precio() * cantidad;
        if (!economia.gastar(discordId, coste, "tienda")) {
            return new ResultadoCompra(EstadoCompra.SIN_SALDO, coste);
        }
        inventario.anadir(discordId, itemId, cantidad);
        return new ResultadoCompra(EstadoCompra.OK, coste);
    }

    /**
     * Usa un consumible: lo descuenta del inventario y aplica su efecto (energía/salud).
     *
     * <p>La saciedad se comprueba <b>antes</b> de tocar el inventario, para que el intento salga
     * gratis: quedarse lleno no puede costarte el ítem.
     */
    public ResultadoUso usar(long discordId, String itemId) {
        Optional<Items> item = Items.porId(itemId);
        if (item.isEmpty()) {
            return new ResultadoUso(EstadoUso.NO_EXISTE, Efecto.NINGUNO, 0);
        }
        Items i = item.get();
        if (i.categoria() != Items.Categoria.CONSUMIBLE) {
            return new ResultadoUso(EstadoUso.NO_CONSUMIBLE, Efecto.NINGUNO, 0);
        }
        if (!descanso.puedeConsumir(discordId)) {
            return new ResultadoUso(EstadoUso.LLENO, Efecto.NINGUNO, 0);
        }
        personajes.obtenerOCrear(discordId);
        if (!inventario.quitar(discordId, itemId, 1)) {
            return new ResultadoUso(EstadoUso.NO_TIENE, Efecto.NINGUNO, 0);
        }
        switch (i.efecto()) {
            case ENERGIA -> personajes.sumarEnergia(discordId, i.valor());
            case SALUD -> personajes.sumarSalud(discordId, i.valor());
            default -> { }
        }
        // Solo cuenta lo que de verdad se ha consumido (aquí ya salió del inventario).
        descanso.registrarConsumo(discordId);
        return new ResultadoUso(EstadoUso.OK, i.efecto(), i.valor());
    }

    /** Inventario del usuario (ítem → cantidad). */
    public Map<String, Integer> inventario(long discordId) {
        return inventario.listar(discordId);
    }
}
