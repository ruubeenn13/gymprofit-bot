package com.gymprofit.bot.commands.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * Colector de los cambios que aplica una ejecución de {@code /setup}: qué se creó, actualizó o
 * eliminó, por categoría y con nombre. Puro (sin JDA ni i18n): la instrumentación de setup lo
 * rellena y {@link InformeSetup} lo renderiza. El orden de registro se conserva para que el
 * informe salga en el mismo orden en que setup montó las cosas.
 */
public final class RegistroCambios {

    /** Familia del elemento tocado (para agrupar el informe). */
    public enum Categoria { ROL, CATEGORIA, CANAL, INTRO, DESCRIPCION_SERVIDOR,
                            WELCOME, AFK, AUTOMOD, ANCLA, PANEL }

    /** Naturaleza del cambio. */
    public enum Tipo { CREADO, ACTUALIZADO, ELIMINADO }

    /** Un cambio concreto. */
    public record Entrada(Tipo tipo, Categoria categoria, String nombre) {}

    private final List<Entrada> entradas = new ArrayList<>();

    public void creado(Categoria categoria, String nombre) {
        entradas.add(new Entrada(Tipo.CREADO, categoria, nombre));
    }

    public void actualizado(Categoria categoria, String nombre) {
        entradas.add(new Entrada(Tipo.ACTUALIZADO, categoria, nombre));
    }

    public void eliminado(Categoria categoria, String nombre) {
        entradas.add(new Entrada(Tipo.ELIMINADO, categoria, nombre));
    }

    public boolean huboCambios() {
        return !entradas.isEmpty();
    }

    public int cuenta(Tipo tipo) {
        return (int) entradas.stream().filter(e -> e.tipo() == tipo).count();
    }

    /** Copia inmutable de los cambios en orden de registro. */
    public List<Entrada> entradas() {
        return List.copyOf(entradas);
    }
}
