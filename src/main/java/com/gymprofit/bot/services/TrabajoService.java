package com.gymprofit.bot.services;

import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/**
 * Lógica de trabajo y energía: elegir trabajo (con requisito de nivel), trabajar un turno (gana
 * coins, gasta energía, con cooldown) y entrenar atributos (gasta energía). La regeneración de
 * energía la hace {@code EnergiaJob}. Progresión lenta: cooldowns amplios y ganancias contenidas.
 */
public final class TrabajoService {

    /** Cooldown entre turnos de trabajo. */
    public static final Duration COOLDOWN_WORK = Duration.ofMinutes(60);
    /** Energía que cuesta entrenar un atributo (+1). */
    public static final int ENERGIA_ENTRENAR = 25;
    /** Bono al sueldo por cada punto de estudios. */
    public static final double BONO_ESTUDIOS = 0.01;
    /** Tope del bono de estudios al sueldo. */
    public static final double BONO_ESTUDIOS_MAX = 0.25;

    public enum EstadoWork { OK, SIN_TRABAJO, EN_COOLDOWN, SIN_ENERGIA }

    public enum ResultadoElegir { OK, NO_EXISTE, REQUISITO }

    public enum ResultadoEntrenar { OK, SIN_ENERGIA }

    /** Resultado de {@code /work}. */
    public record ResultadoWork(EstadoWork estado, int pago, int energiaRestante,
                                long segundosRestantes) {
    }

    private final PersonajeRepositorio personajes;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final Random aleatorio = new Random();

    public TrabajoService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios) {
        this.personajes = personajes;
        this.economia = economia;
        this.usuarios = usuarios;
    }

    /** Asigna un trabajo si existe y el usuario cumple el requisito de nivel. */
    public ResultadoElegir elegir(long discordId, String trabajoId) {
        var trabajo = Trabajos.porId(trabajoId);
        if (trabajo.isEmpty()) {
            return ResultadoElegir.NO_EXISTE;
        }
        UsuarioDiscord u = usuarios.obtenerOCrear(discordId);
        personajes.obtenerOCrear(discordId);
        if (u.nivel() < trabajo.get().requisitoNivel()) {
            return ResultadoElegir.REQUISITO;
        }
        personajes.fijarTrabajo(discordId, trabajoId);
        return ResultadoElegir.OK;
    }

    /** Trabaja un turno: valida trabajo, cooldown y energía; paga y descuenta energía. */
    public ResultadoWork trabajar(long discordId, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return new ResultadoWork(EstadoWork.SIN_TRABAJO, 0, p.energia(), 0);
        }
        if (p.ultimoWork() != null) {
            long restante = COOLDOWN_WORK.getSeconds() - Duration.between(p.ultimoWork(), ahora).getSeconds();
            if (restante > 0) {
                return new ResultadoWork(EstadoWork.EN_COOLDOWN, 0, p.energia(), restante);
            }
        }
        Trabajos t = Trabajos.porId(p.trabajo()).orElseThrow();
        if (p.energia() < t.energiaCoste() || !personajes.trabajar(discordId, t.energiaCoste())) {
            return new ResultadoWork(EstadoWork.SIN_ENERGIA, 0, p.energia(), 0);
        }
        int base = calcularPago(t.salarioMin(), t.salarioMax(), aleatorio);
        int pago = conBonoEstudios(base, p.estudios());
        economia.ingresar(discordId, pago, "work");
        return new ResultadoWork(EstadoWork.OK, pago, p.energia() - t.energiaCoste(), 0);
    }

    /** Entrena un atributo (+1) gastando energía. */
    public ResultadoEntrenar entrenar(long discordId, String atributo) {
        usuarios.obtenerOCrear(discordId);
        personajes.obtenerOCrear(discordId);
        return personajes.entrenar(discordId, atributo, ENERGIA_ENTRENAR)
                ? ResultadoEntrenar.OK : ResultadoEntrenar.SIN_ENERGIA;
    }

    /** Pago aleatorio dentro del rango [min, max] del trabajo. */
    public static int calcularPago(int min, int max, Random aleatorio) {
        return min + aleatorio.nextInt(max - min + 1);
    }

    /** Aplica el bono de estudios al pago base (+1% por punto, con tope). */
    public static int conBonoEstudios(int base, int estudios) {
        double bono = Math.min(BONO_ESTUDIOS_MAX, estudios * BONO_ESTUDIOS);
        return (int) Math.round(base * (1 + bono));
    }
}
