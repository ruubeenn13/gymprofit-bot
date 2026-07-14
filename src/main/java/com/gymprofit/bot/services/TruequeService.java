package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.InventarioRepositorio;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.util.ArrayList;
import java.util.List;

/**
 * Trueque entre jugadores (F-ECO-4d): intercambio de ítems y/o coins en ambos sentidos, con
 * confirmación mutua (el objetivo acepta con un botón). La ejecución <b>reserva primero</b> lo que
 * aporta cada parte (operaciones atómicas del monedero/inventario) y, si algo falla, deshace lo ya
 * reservado; solo cuando ambas partes tienen todo se hace la entrega. Sin comisión (es un intercambio
 * directo entre dos personas que ambas aceptan).
 */
public final class TruequeService {

    /**
     * Una oferta de trueque: el proponente <b>da</b> {@code doy*} y <b>pide</b> {@code pido*} al
     * objetivo. Los ítems son opcionales ({@code null}); los coins, 0 si no aplica.
     */
    public record Oferta(long proponente, long objetivo,
                         String doyItem, int doyCant, long doyCoins,
                         String pidoItem, int pidoCant, long pidoCoins) {

        /** ¿El proponente ofrece algo? */
        public boolean ofreceAlgo() {
            return doyCoins > 0 || (doyItem != null && doyCant > 0);
        }

        /** ¿El proponente pide algo? */
        public boolean pideAlgo() {
            return pidoCoins > 0 || (pidoItem != null && pidoCant > 0);
        }
    }

    /** Resultado de ejecutar un trueque. */
    public enum Estado { OK, PROPONENTE_SIN_SALDO, OBJETIVO_SIN_SALDO, PROPONENTE_SIN_ITEM, OBJETIVO_SIN_ITEM }

    private final EconomiaRepositorio economia;
    private final InventarioRepositorio inventario;
    private final UsuarioDiscordRepositorio usuarios;

    public TruequeService(EconomiaRepositorio economia, InventarioRepositorio inventario,
                          UsuarioDiscordRepositorio usuarios) {
        this.economia = economia;
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** Ejecuta el intercambio (ambas partes ya han aceptado). Reserva con rollback y luego entrega. */
    public Estado ejecutar(Oferta o) {
        usuarios.obtenerOCrear(o.proponente());
        usuarios.obtenerOCrear(o.objetivo());
        List<Runnable> deshacer = new ArrayList<>();

        // Reserva lo que aporta el proponente.
        if (o.doyCoins() > 0) {
            if (!economia.gastar(o.proponente(), o.doyCoins(), "trueque")) {
                return Estado.PROPONENTE_SIN_SALDO;
            }
            deshacer.add(() -> economia.ingresar(o.proponente(), o.doyCoins(), "trueque_undo"));
        }
        if (o.doyItem() != null) {
            if (!inventario.quitar(o.proponente(), o.doyItem(), o.doyCant())) {
                revertir(deshacer);
                return Estado.PROPONENTE_SIN_ITEM;
            }
            deshacer.add(() -> inventario.anadir(o.proponente(), o.doyItem(), o.doyCant()));
        }
        // Reserva lo que aporta el objetivo.
        if (o.pidoCoins() > 0) {
            if (!economia.gastar(o.objetivo(), o.pidoCoins(), "trueque")) {
                revertir(deshacer);
                return Estado.OBJETIVO_SIN_SALDO;
            }
            deshacer.add(() -> economia.ingresar(o.objetivo(), o.pidoCoins(), "trueque_undo"));
        }
        if (o.pidoItem() != null) {
            if (!inventario.quitar(o.objetivo(), o.pidoItem(), o.pidoCant())) {
                revertir(deshacer);
                return Estado.OBJETIVO_SIN_ITEM;
            }
            deshacer.add(() -> inventario.anadir(o.objetivo(), o.pidoItem(), o.pidoCant()));
        }

        // Entrega cruzada (lo del proponente al objetivo y viceversa).
        if (o.doyCoins() > 0) {
            economia.ingresar(o.objetivo(), o.doyCoins(), "trueque");
        }
        if (o.doyItem() != null) {
            inventario.anadir(o.objetivo(), o.doyItem(), o.doyCant());
        }
        if (o.pidoCoins() > 0) {
            economia.ingresar(o.proponente(), o.pidoCoins(), "trueque");
        }
        if (o.pidoItem() != null) {
            inventario.anadir(o.proponente(), o.pidoItem(), o.pidoCant());
        }
        return Estado.OK;
    }

    private static void revertir(List<Runnable> deshacer) {
        for (int i = deshacer.size() - 1; i >= 0; i--) {
            deshacer.get(i).run();
        }
    }
}
