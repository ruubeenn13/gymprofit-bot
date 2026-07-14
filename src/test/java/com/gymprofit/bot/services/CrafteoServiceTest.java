package com.gymprofit.bot.services;

import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.services.CrafteoService.Estado;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifica /craftear: fabrica con ingredientes, falla sin ellos; e integridad del catálogo. */
class CrafteoServiceTest {

    private final InventarioRepositorio inventario = mock(InventarioRepositorio.class);
    private final UsuarioDiscordRepositorio usuarios = mock(UsuarioDiscordRepositorio.class);
    private final CrafteoService servicio = new CrafteoService(inventario, usuarios);

    @Test
    void conIngredientesFabricaYConsumeLosMinerales() {
        // espada_corta = hierro 3 + carbon 2
        when(inventario.cantidad(1L, "hierro")).thenReturn(5);
        when(inventario.cantidad(1L, "carbon")).thenReturn(3);
        var r = servicio.craftear(1L, "espada_corta");
        assertEquals(Estado.OK, r.estado());
        verify(inventario).quitar(1L, "hierro", 3);
        verify(inventario).quitar(1L, "carbon", 2);
        verify(inventario).anadir(1L, "espada_corta", 1);
    }

    @Test
    void sinIngredientesNoFabrica() {
        when(inventario.cantidad(1L, "hierro")).thenReturn(1); // faltan 2
        when(inventario.cantidad(1L, "carbon")).thenReturn(2);
        var r = servicio.craftear(1L, "espada_corta");
        assertEquals(Estado.FALTAN_INGREDIENTES, r.estado());
        assertTrue(r.faltantes().stream().anyMatch(i -> i.itemId().equals("hierro") && i.cantidad() == 2));
        verify(inventario, never()).quitar(anyLong(), anyString(), anyInt());
        verify(inventario, never()).anadir(anyLong(), anyString(), anyInt());
    }

    @Test
    void recetaInexistenteFalla() {
        assertEquals(Estado.NO_EXISTE, servicio.craftear(1L, "excalibur").estado());
    }

    @Test
    void todasLasRecetasSonCoherentesConElCatalogo() {
        for (Recetas r : Recetas.CATALOGO) {
            assertTrue(Items.porId(r.resultado()).isPresent(),
                    "resultado inexistente: " + r.resultado());
            assertTrue(r.ingredientes().size() > 0, "receta sin ingredientes: " + r.resultado());
            for (Recetas.Ingrediente ing : r.ingredientes()) {
                assertEquals(Items.Categoria.MINERAL,
                        Items.porId(ing.itemId()).orElseThrow().categoria(),
                        "ingrediente no mineral: " + ing.itemId());
            }
        }
    }
}
