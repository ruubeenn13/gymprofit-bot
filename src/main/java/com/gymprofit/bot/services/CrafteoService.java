package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lógica de {@code /craftear} y {@code /recetas} (COMBAT-6 crafting). Comprueba que el jugador tiene
 * los minerales de una receta, los consume y le entrega el ítem fabricado. Todo pasa por el
 * inventario (sin coins ni schema nuevo).
 */
public final class CrafteoService {

    /** Estado de un intento de crafteo. */
    public enum Estado { OK, NO_EXISTE, FALTAN_INGREDIENTES }

    /**
     * Resultado de craftear.
     *
     * @param estado    resultado
     * @param resultado id del ítem fabricado (o {@code null})
     * @param faltantes ingredientes que faltan (id → unidades que faltan), vacío si {@code OK}
     */
    public record Resultado(Estado estado, String resultado, List<Recetas.Ingrediente> faltantes) {
    }

    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;

    public CrafteoService(InventarioRepositorio inventario, UsuarioDiscordRepositorio usuarios) {
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** ¿Le faltan ingredientes al jugador para una receta? Devuelve la lista de lo que falta. */
    public List<Recetas.Ingrediente> faltantes(long discordId, Recetas receta) {
        List<Recetas.Ingrediente> falta = new ArrayList<>();
        for (Recetas.Ingrediente ing : receta.ingredientes()) {
            int tiene = inventario.cantidad(discordId, ing.itemId());
            if (tiene < ing.cantidad()) {
                falta.add(new Recetas.Ingrediente(ing.itemId(), ing.cantidad() - tiene));
            }
        }
        return falta;
    }

    /** Fabrica el ítem de una receta: consume los minerales y lo añade al inventario. */
    public Resultado craftear(long discordId, String recetaId) {
        Optional<Recetas> receta = Recetas.porResultado(recetaId);
        if (receta.isEmpty()) {
            return new Resultado(Estado.NO_EXISTE, null, List.of());
        }
        usuarios.obtenerOCrear(discordId);
        Recetas r = receta.get();
        List<Recetas.Ingrediente> falta = faltantes(discordId, r);
        if (!falta.isEmpty()) {
            return new Resultado(Estado.FALTAN_INGREDIENTES, recetaId, falta);
        }
        // Consumir ingredientes (atómico por ítem) y entregar el resultado.
        for (Recetas.Ingrediente ing : r.ingredientes()) {
            inventario.quitar(discordId, ing.itemId(), ing.cantidad());
        }
        inventario.anadir(discordId, r.resultado(), 1);
        return new Resultado(Estado.OK, recetaId, List.of());
    }
}
