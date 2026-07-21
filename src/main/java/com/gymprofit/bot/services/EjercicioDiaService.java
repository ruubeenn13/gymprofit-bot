package com.gymprofit.bot.services;

import com.gymprofit.bot.api.ApiException;
import com.gymprofit.bot.api.dtos.EjercicioDTO;
import com.gymprofit.bot.db.EjercicioDia;
import com.gymprofit.bot.db.EjercicioDiaRepositorio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Elección del ejercicio del día (spec §5.1): cada día natural de Europe/Madrid se sortea un
 * ejercicio entre los que <b>no</b> han salido en la ronda actual; al agotar el catálogo
 * empieza la siguiente. La fila del día la crea quien llegue primero (job de las 8:00 o
 * {@code /ejercicio-dia}): el {@code INSERT IGNORE} sobre la PK por fecha resuelve la carrera
 * y ambos ven siempre el mismo ejercicio.
 */
public final class EjercicioDiaService {

    /** El «día» del bot es el de la comunidad, no UTC. */
    public static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final EjercicioDiaRepositorio repo;
    private final EjercicioService ejercicios;
    private final Random azar;
    private final Clock reloj;

    public EjercicioDiaService(EjercicioDiaRepositorio repo, EjercicioService ejercicios) {
        this(repo, ejercicios, new Random(), Clock.system(ZONA));
    }

    /** Azar y reloj inyectables para testear el sorteo sin aleatoriedad ni reloj real. */
    public EjercicioDiaService(EjercicioDiaRepositorio repo, EjercicioService ejercicios,
                               Random azar, Clock reloj) {
        this.repo = repo;
        this.ejercicios = ejercicios;
        this.azar = azar;
        this.reloj = reloj;
    }

    /** La elección de hoy, creándola si nadie la hizo aún (idéntica para job y comando). */
    public EjercicioDia deHoy() {
        LocalDate hoy = LocalDate.now(reloj);
        return repo.buscarPorFecha(hoy).orElseGet(() -> elegir(hoy));
    }

    private EjercicioDia elegir(LocalDate hoy) {
        // El catálogo se pide en ES: aquí solo importan los ids (la ficha localizada la pide
        // luego quien pinta, con el idioma que toque).
        List<Integer> catalogo = ejercicios.listarTodos("es").stream()
                .map(EjercicioDTO::id)
                .toList();
        if (catalogo.isEmpty()) {
            // Sin catálogo no hay sorteo posible. Se sale por la misma excepción que el resto de
            // fallos de API para que el comando/job respondan con el aviso amable de siempre,
            // en vez de reventar con el IllegalArgumentException de nextInt(0).
            throw new ApiException("La API devolvió un catálogo de ejercicios vacío");
        }
        int ronda = repo.rondaActual();
        Set<Integer> usados = repo.idsDeRonda(ronda);
        List<Integer> candidatos = catalogo.stream()
                .filter(id -> !usados.contains(id))
                .toList();
        if (candidatos.isEmpty()) {
            // Ronda completada: vuelve a valer el catálogo entero en la ronda siguiente.
            ronda++;
            candidatos = catalogo;
        }
        int elegido = candidatos.get(azar.nextInt(candidatos.size()));
        EjercicioDia dia = new EjercicioDia(hoy, elegido, ronda);
        if (!repo.insertar(dia)) {
            // Otro proceso (job vs comando) ganó la carrera: lo suyo es lo que vale.
            return repo.buscarPorFecha(hoy).orElseThrow(
                    () -> new IllegalStateException("ejercicio_dia sin fila tras perder la carrera"));
        }
        return dia;
    }
}
