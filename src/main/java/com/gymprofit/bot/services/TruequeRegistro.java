package com.gymprofit.bot.services;

import com.gymprofit.bot.services.TruequeService.Oferta;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registro en memoria de las ofertas de trueque pendientes (F-ECO-4d). El comando {@code /trueque}
 * guarda aquí la oferta y obtiene un id que viaja en el customId de los botones; el listener la
 * recupera y la borra al aceptar/rechazar. No se persiste: una oferta viva solo mientras el bot está
 * arriba (si se reinicia, se pierde y basta con reproponer).
 */
public final class TruequeRegistro {

    private final Map<Long, Oferta> ofertas = new ConcurrentHashMap<>();
    private final AtomicLong siguienteId = new AtomicLong(1);

    /** Registra una oferta y devuelve su id. */
    public long registrar(Oferta oferta) {
        long id = siguienteId.getAndIncrement();
        ofertas.put(id, oferta);
        return id;
    }

    /** Recupera y elimina una oferta (se consume al aceptar/rechazar). */
    public Optional<Oferta> consumir(long id) {
        return Optional.ofNullable(ofertas.remove(id));
    }
}
