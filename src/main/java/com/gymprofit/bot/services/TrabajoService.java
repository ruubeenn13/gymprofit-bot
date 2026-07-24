package com.gymprofit.bot.services;

import com.gymprofit.bot.db.CarreraRepositorio;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Lógica de trabajo y energía: elegir trabajo (con requisito de nivel), trabajar un turno (gana
 * coins, gasta energía, con cooldown) y entrenar atributos (gasta energía). La regeneración de
 * energía la hace {@code EnergiaJob}. Progresión lenta: cooldowns amplios y ganancias contenidas.
 *
 * <p>Los <b>efectos pasivos</b> ({@link PasivoService}) entran en dos sitios: el bono de
 * {@code SUELDO} sube el pago y el de {@code COOLDOWN_WORK} recorta la espera entre turnos. El orden
 * de la tubería del sueldo es estudios → pasivos → fatiga y <b>no se toca</b>: la fatiga recorta el
 * sueldo final, no el base (ver {@link #conPenalizacionFatiga}).
 *
 * <p><b>Ascensos de carrera</b> ({@link Ascensos}): los puestos por encima del tier de entrada de
 * su rama ya no se eligen, se ganan con {@link #ascender}. Cada salto exige cuatro requisitos
 * (antigüedad en el puesto, estudios, la stat dominante de la rama y coins) y los coins se
 * <b>queman</b> — gasto sin contrapartida, sumidero antiinflación. El tier alcanzado se guarda por
 * rama en {@link CarreraRepositorio} y nunca baja.
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
    /** Multiplicador del sueldo con fatiga (−20 %): rendir cansado se nota en la nómina. */
    public static final double PENAL_FATIGA = 0.8;
    /** Corte que se lleva la empresa del curro de cada miembro (10 % del bruto → al bote). */
    public static final double CORTE_EMPRESA = 0.10;
    /** Bonus de ingresos por cada nivel de la empresa (+2 %/nivel sobre el sueldo del miembro). */
    public static final double BONUS_POR_NIVEL = 0.02;

    public enum EstadoWork { OK, SIN_TRABAJO, EN_COOLDOWN, SIN_ENERGIA, DORMIDO }

    public enum ResultadoElegir { OK, NO_EXISTE, REQUISITO, TIER }

    public enum EstadoAscenso { OK, SIN_TRABAJO, NO_EXISTE, DESTINO, TOPE, NIVEL, TURNOS, ESTUDIOS, STAT, COINS }

    public enum ResultadoEntrenar { OK, SIN_ENERGIA }

    public enum ResultadoDimitir { OK, SIN_TRABAJO }

    /** Resultado de {@code /work}. */
    public record ResultadoWork(EstadoWork estado, int pago, int energiaRestante,
                                long segundosRestantes) {
    }

    /**
     * Resultado de un intento de ascenso.
     *
     * @param estado   resultado
     * @param requisito requisitos del salto intentado (para pintar cuánto falta), o {@code null}
     * @param actual   valor actual del requisito incumplido (turnos/estudios/stat), o 0
     */
    public record ResultadoAscenso(EstadoAscenso estado, Ascensos.Requisitos requisito, int actual) {
    }

    /**
     * Foto de la carrera de un jugador para {@code /trabajo carrera}: rama y tier, puesto y
     * antigüedad, y el siguiente salto con sus requisitos. Incluye {@code estudios} y {@code stat}
     * (el valor <b>actual</b> de la stat dominante de la rama) para que el comando pinte la
     * checklist sin tocar repositorios.
     *
     * <p><b>Semántica sin trabajo:</b> {@code rama}, {@code puestoActual} y {@code requisitos} son
     * {@code null}; {@code tierAlcanzado}, {@code turnosPuesto}, {@code estudios} y {@code stat}
     * valen 0; {@code siguiente} está vacío. El comando detecta el paro con
     * {@code puestoActual() == null}.
     */
    public record InfoCarrera(Ascensos.Rama rama, int tierAlcanzado, String puestoActual,
                              int turnosPuesto, int estudios, int stat,
                              Optional<Integer> siguiente, Ascensos.Requisitos requisitos) {
    }

    private final PersonajeRepositorio personajes;
    private final EconomiaRepositorio economia;
    private final UsuarioDiscordRepositorio usuarios;
    private final DescansoService descanso;
    /** Tier alcanzado por rama: la memoria de la carrera de cada jugador. */
    private final CarreraRepositorio carreras;
    /** Efectos pasivos del jugador. {@code null} en los tests y arranques que no los usan. */
    private final PasivoService pasivos;
    /**
     * Empresa del jugador: el curro corta un 10 % al bote y el nivel bonifica el ingreso (F3).
     * {@code null} en los tests y arranques que no usan empresas (el curro paga el base sin corte).
     */
    private final EmpresaRepositorio empresas;
    private final Random aleatorio = new Random();

    private static final Logger log = LoggerFactory.getLogger(TrabajoService.class);

    public TrabajoService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          CarreraRepositorio carreras, PasivoService pasivos,
                          EmpresaRepositorio empresas) {
        this.personajes = personajes;
        this.economia = economia;
        this.usuarios = usuarios;
        this.descanso = descanso;
        this.carreras = carreras;
        this.pasivos = pasivos;
        this.empresas = empresas;
    }

    /**
     * Constructor sin empresas (tests y arranques que no tocan el corte al bote): el curro paga el
     * sueldo sin corte ni bonus de empresa.
     */
    public TrabajoService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          CarreraRepositorio carreras, PasivoService pasivos) {
        this(personajes, economia, usuarios, descanso, carreras, pasivos, null);
    }

    /**
     * Constructor sin pasivos ni empresas (tests y arranques degradados): equivale a no tener nada
     * equipado y a currar fuera de cualquier empresa.
     */
    public TrabajoService(PersonajeRepositorio personajes, EconomiaRepositorio economia,
                          UsuarioDiscordRepositorio usuarios, DescansoService descanso,
                          CarreraRepositorio carreras) {
        this(personajes, economia, usuarios, descanso, carreras, null, null);
    }

    /**
     * Asigna un trabajo si existe, el usuario cumple el requisito de nivel y el puesto es
     * alcanzable: o es del tier de entrada de su rama, o el jugador ya tiene ese tier por carrera.
     */
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
        // El tier ya no es libre: o es la entrada de la rama, o lo tienes alcanzado por carrera.
        Ascensos.Rama rama = Ascensos.ramaDe(trabajo.get().sector());
        if (trabajo.get().tier() > tierAlcanzadoEn(discordId, rama)) {
            return ResultadoElegir.TIER;
        }
        personajes.fijarTrabajo(discordId, trabajoId);
        return ResultadoElegir.OK;
    }

    /**
     * Asciende al puesto indicado del siguiente tier de la rama del trabajo actual. Valida en orden:
     * destino (existe, misma rama, tier correcto), nivel de servidor, antigüedad, estudios, stat y
     * por último el cobro <b>atómico</b> (gastar solo si todo lo demás cumple: nunca se cobra un
     * ascenso fallido). Los coins se queman: gasto sin contrapartida en el ledger.
     *
     * <p>Se compone de dos piezas reutilizables: {@link #validarAscenso} (todos los requisitos NO
     * monetarios) y {@link #aplicarAscenso} (fijar carrera + puesto). El cobro del propio jugador vía
     * {@link EconomiaRepositorio#gastar} queda <b>estrictamente entre ambas</b>, para que el orden y la
     * regla de «nunca cobrar un ascenso fallido» sean idénticos al patrocinio de empresa, que reutiliza
     * las mismas piezas quemando del bote en vez del bolsillo del jugador.
     */
    public ResultadoAscenso ascender(long discordId, String puestoId) {
        ResultadoAscenso validacion = validarAscenso(discordId, puestoId);
        if (validacion.estado() != EstadoAscenso.OK) {
            return validacion;
        }
        // El cobro es lo último antes de aplicar: si no llega el saldo, no se toca ni carrera ni puesto.
        if (!economia.gastar(discordId, validacion.requisito().coins(), "ascenso")) {
            return new ResultadoAscenso(EstadoAscenso.COINS, validacion.requisito(), 0);
        }
        aplicarAscenso(discordId, puestoId);
        return new ResultadoAscenso(EstadoAscenso.OK, validacion.requisito(), 0);
    }

    /**
     * Comprueba todos los requisitos <b>no monetarios</b> de ascender a {@code puestoId} desde el
     * trabajo actual, en el mismo orden que {@link #ascender}: destino (existe, misma rama, es el
     * siguiente tier), nivel de servidor, antigüedad, estudios y stat dominante. Devuelve
     * {@link EstadoAscenso#OK} con el requisito del salto si todo cumple; en otro caso, el primer motivo
     * de fallo. <b>No cobra ni aplica nada</b>: es la parte pura que comparten el ascenso normal y el
     * patrocinado por empresa (que decide aparte de dónde sale el dinero).
     */
    public ResultadoAscenso validarAscenso(long discordId, String puestoId) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return new ResultadoAscenso(EstadoAscenso.SIN_TRABAJO, null, 0);
        }
        var destino = Trabajos.porId(puestoId);
        if (destino.isEmpty()) {
            return new ResultadoAscenso(EstadoAscenso.NO_EXISTE, null, 0);
        }
        Trabajos actual = Trabajos.porId(p.trabajo()).orElseThrow();
        Ascensos.Rama rama = Ascensos.ramaDe(actual.sector());
        var siguiente = Ascensos.siguienteTier(rama, tierAlcanzadoEn(discordId, rama));
        if (siguiente.isEmpty()) {
            return new ResultadoAscenso(EstadoAscenso.TOPE, null, 0);
        }
        if (Ascensos.ramaDe(destino.get().sector()) != rama
                || destino.get().tier() != siguiente.get()) {
            return new ResultadoAscenso(EstadoAscenso.DESTINO, null, 0);
        }
        Ascensos.Requisitos req = Ascensos.requisitosPara(siguiente.get());
        UsuarioDiscord u = usuarios.obtenerOCrear(discordId);
        if (u.nivel() < destino.get().requisitoNivel()) {
            return new ResultadoAscenso(EstadoAscenso.NIVEL, req, u.nivel());
        }
        if (p.turnosPuesto() < req.turnos()) {
            return new ResultadoAscenso(EstadoAscenso.TURNOS, req, p.turnosPuesto());
        }
        if (p.estudios() < req.estudios()) {
            return new ResultadoAscenso(EstadoAscenso.ESTUDIOS, req, p.estudios());
        }
        int stat = statDelPersonaje(p, Ascensos.statDe(rama));
        if (stat < req.stat()) {
            return new ResultadoAscenso(EstadoAscenso.STAT, req, stat);
        }
        return new ResultadoAscenso(EstadoAscenso.OK, req, 0);
    }

    /**
     * Aplica un ascenso ya validado: fija el tier de la rama en la carrera (nunca baja) y cambia al
     * puesto destino (lo que además resetea la antigüedad). <b>No valida ni cobra</b>: presupone que
     * {@link #validarAscenso} devolvió OK y que el pago (del jugador o del bote de su empresa) ya se
     * hizo. El tier destino se deduce del propio puesto, así que la firma no lo arrastra.
     *
     * <p><b>Package-private a propósito:</b> solo lo llaman {@link #ascender} y el patrocinio de empresa
     * ({@code EmpresaGestionService}), ambos en este paquete; no es superficie pública porque aplicar sin
     * validar ni cobrar antes rompería las reglas de dinero.
     */
    void aplicarAscenso(long discordId, String puestoId) {
        Trabajos destino = Trabajos.porId(puestoId).orElseThrow();
        Ascensos.Rama rama = Ascensos.ramaDe(destino.sector());
        carreras.fijarTier(discordId, rama.name(), destino.tier());
        personajes.fijarTrabajo(discordId, puestoId); // resetea también la antigüedad
    }

    /**
     * Coste en coins de ascender a {@code puestoId}: los coins del requisito de su tier destino. Lo usa
     * el patrocinio de empresa para saber cuánto quemar del bote sin recalcular la escala de ascensos.
     * Un puesto inexistente o de tier de entrada (sin salto) cuesta {@code 0}.
     */
    public long costeAscenso(String puestoId) {
        return Trabajos.porId(puestoId)
                .map(t -> Ascensos.requisitosPara(t.tier()))
                .map(Ascensos.Requisitos::coins)
                .orElse(0L);
    }

    /**
     * Tier alcanzado por el jugador en una rama: el máximo entre la <b>entrada</b> de la rama
     * (siempre concedida, aunque no haya carrera guardada) y lo que diga la BD de carrera. Es la
     * regla única de «hasta dónde has llegado», compartida por elegir, ascender y la lista.
     */
    public int tierAlcanzadoEn(long discordId, Ascensos.Rama rama) {
        return Math.max(Ascensos.tierEntrada(rama), carreras.tierAlcanzado(discordId, rama.name()));
    }

    /**
     * Puestos elegibles para ascender desde el trabajo actual (para el autocompletado de
     * {@code /trabajo ascender}): los del siguiente tier existente de tu rama. Vacío si el jugador
     * no tiene trabajo o su rama ya topó.
     */
    public List<Trabajos> opcionesAscenso(long discordId) {
        Optional<Ascensos.Rama> rama = ramaActual(personajes.obtenerOCrear(discordId));
        if (rama.isEmpty()) {
            return List.of();
        }
        return Ascensos.siguienteTier(rama.get(), tierAlcanzadoEn(discordId, rama.get()))
                .map(t -> Ascensos.puestosDe(rama.get(), t))
                .orElse(List.of());
    }

    /** Rama del trabajo actual del personaje, o vacío si está en paro. Sin trabajo no hay carrera. */
    private static Optional<Ascensos.Rama> ramaActual(Personaje p) {
        if (p.trabajo() == null) {
            return Optional.empty();
        }
        return Optional.of(Ascensos.ramaDe(Trabajos.porId(p.trabajo()).orElseThrow().sector()));
    }

    /**
     * Foto de la carrera para {@code /trabajo carrera} (ver semántica sin trabajo en
     * {@link InfoCarrera}). Misma lógica de rama/tier que {@link #opcionesAscenso}, más los valores
     * actuales (antigüedad, estudios, stat dominante) que la checklist compara con los requisitos.
     */
    public InfoCarrera infoCarrera(long discordId) {
        Personaje p = personajes.obtenerOCrear(discordId);
        Optional<Ascensos.Rama> ramaOpt = ramaActual(p);
        if (ramaOpt.isEmpty()) {
            return new InfoCarrera(null, 0, null, 0, 0, 0, Optional.empty(), null);
        }
        Ascensos.Rama rama = ramaOpt.get();
        int alcanzado = tierAlcanzadoEn(discordId, rama);
        Optional<Integer> siguiente = Ascensos.siguienteTier(rama, alcanzado);
        return new InfoCarrera(rama, alcanzado, p.trabajo(), p.turnosPuesto(), p.estudios(),
                statDelPersonaje(p, Ascensos.statDe(rama)), siguiente,
                siguiente.map(Ascensos::requisitosPara).orElse(null));
    }

    /** Saldo del jugador, expuesto para que el comando pinte el requisito de coins sin tocar repos. */
    public long saldo(long discordId) {
        return economia.saldo(discordId);
    }

    /** Valor de la stat dominante de la rama en este personaje. */
    private static int statDelPersonaje(Personaje p, String stat) {
        return switch (stat) {
            case "fuerza" -> p.fuerza();
            case "resistencia" -> p.resistencia();
            case "carisma" -> p.carisma();
            default -> throw new IllegalArgumentException("stat desconocida: " + stat);
        };
    }

    /** Trabaja un turno: valida sueño, trabajo, cooldown y energía; paga y descuenta energía. */
    public ResultadoWork trabajar(long discordId, Instant ahora) {
        usuarios.obtenerOCrear(discordId);
        // Dormido no se cura: es la primera guarda, antes de tocar cooldown o energía, para que
        // intentarlo durmiendo no consuma nada y el comando pueda ofrecer despertar.
        if (descanso.estaDormido(discordId)) {
            return new ResultadoWork(EstadoWork.DORMIDO, 0, 0, 0);
        }
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return new ResultadoWork(EstadoWork.SIN_TRABAJO, 0, p.energia(), 0);
        }
        // Un único viaje a los pasivos por turno: se necesitan dos tipos de bono (cooldown y sueldo)
        // y cada llamada a bonosDe son tres consultas, así que se leen juntos y se reparten.
        Map<Pasivos.Tipo, Double> bonos = bonosDe(discordId);
        if (p.ultimoWork() != null) {
            // El cooldown ya no es una constante: los pasivos de COOLDOWN_WORK lo recortan hasta un
            // suelo de 45 min.
            Duration cooldown = cooldownEfectivo(bono(bonos, Pasivos.Tipo.COOLDOWN_WORK));
            long restante = cooldown.getSeconds() - Duration.between(p.ultimoWork(), ahora).getSeconds();
            if (restante > 0) {
                return new ResultadoWork(EstadoWork.EN_COOLDOWN, 0, p.energia(), restante);
            }
        }
        Trabajos t = Trabajos.porId(p.trabajo()).orElseThrow();
        if (p.energia() < t.energiaCoste() || !personajes.trabajar(discordId, t.energiaCoste())) {
            return new ResultadoWork(EstadoWork.SIN_ENERGIA, 0, p.energia(), 0);
        }
        int base = calcularPago(t.salarioMin(), t.salarioMax(), aleatorio);
        // La fatiga (>24 h sin dormir) se aplica la última, sobre el pago ya bonificado: es un
        // recorte del sueldo final, no del base, para que empuje al ciclo diario de dormir sin
        // anular los bonos (estudios y pasivos) que el jugador se ha ganado.
        boolean fatiga = DescansoService.tieneFatiga(descanso.estadoDe(discordId), ahora);
        int pago = conPenalizacionFatiga(
                conBonoPasivos(conBonoEstudios(base, p.estudios()), bono(bonos, Pasivos.Tipo.SUELDO)),
                fatiga);
        // Corte de empresa (F3): si el jugador está en una empresa, su nivel bonifica el ingreso y un
        // 10 % del bruto va al bote. Se aplica sobre el sueldo FINAL que hoy percibe (ya con estudios,
        // pasivos y fatiga): es el importe natural sobre el que la empresa tributa. Sin empresa el pago
        // no cambia; si algo falla al leer/actualizar la empresa se cobra el sueldo íntegro sin corte.
        long aCobrar = aplicarEmpresa(discordId, pago);
        economia.ingresar(discordId, aCobrar, "work");
        return new ResultadoWork(EstadoWork.OK, (int) aCobrar, p.energia() - t.energiaCoste(), 0);
    }

    /**
     * Aplica el corte/bonus de empresa al sueldo final del turno y devuelve lo que cobra el jugador.
     * Si pertenece a una empresa, {@link #ingresoEmpresa} calcula el bruto bonificado por nivel y el
     * corte al bote, se ingresa el corte con {@link EmpresaRepositorio#incrementarBote} y el jugador
     * cobra el neto. Sin empresa (o sin repo inyectado) devuelve el sueldo intacto.
     *
     * <p>Todo el acceso a la empresa va envuelto en {@code try/catch}: <b>el curro es prioritario</b>,
     * así que un fallo leyendo o actualizando la empresa degrada a «cobrar el base sin corte» en vez de
     * dejar al jugador sin cobrar su turno.
     */
    private long aplicarEmpresa(long discordId, int sueldo) {
        if (empresas == null) {
            return sueldo;
        }
        try {
            Optional<Empresa> emp = empresas.deMiembro(discordId);
            if (emp.isEmpty()) {
                return sueldo; // fuera de toda empresa no hay corte ni bonus: cobra el sueldo íntegro
            }
            IngresoEmpresa ingreso = ingresoEmpresa(sueldo, emp.get().nivel());
            empresas.incrementarBote(emp.get().id(), ingreso.corte());
            return ingreso.neto();
        } catch (RuntimeException e) {
            log.warn("No se pudo aplicar el corte de empresa al curro de {}: {}", discordId, e.toString());
            return sueldo;
        }
    }

    /** Reparto del sueldo de un turno entre el miembro (neto) y el bote de su empresa (corte). */
    public record IngresoEmpresa(long neto, long corte) {
    }

    /**
     * Reparte el sueldo de un turno entre el miembro y el bote de su empresa. El nivel bonifica el
     * bruto ({@code +2 %} por nivel) y el {@code 10 %} de ese bruto se corta al bote; el miembro cobra
     * el resto. Redondeo a la baja en ambos pasos. <b>Pura.</b>
     */
    public static IngresoEmpresa ingresoEmpresa(long sueldo, int nivel) {
        long bruto = (long) Math.floor(sueldo * (1 + BONUS_POR_NIVEL * nivel));
        long corte = (long) Math.floor(bruto * CORTE_EMPRESA);
        return new IngresoEmpresa(bruto - corte, corte);
    }

    /**
     * Renuncia al trabajo actual: vuelta al paro. Resetea la antigüedad del puesto. <b>No</b> toca la
     * pertenencia a empresa (la pertenencia se valida al entrar, no de forma continua — decisión de F1).
     */
    public ResultadoDimitir dimitir(long discordId) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return ResultadoDimitir.SIN_TRABAJO;
        }
        // fijarTrabajo(null) deja trabajo=NULL y turnos_puesto=0 en la misma sentencia: el mismo
        // método (y reseteo de antigüedad) que usan elegir y ascender al cambiar de puesto.
        personajes.fijarTrabajo(discordId, null);
        return ResultadoDimitir.OK;
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

    /**
     * Aplica la penalización por fatiga al sueldo <b>ya calculado</b> (base + bono de estudios).
     * <b>Puro</b>.
     *
     * <p>El suelo de 1 coin evita que un sueldo mínimo se redondee a 0: currar siempre paga algo,
     * porque un turno que sale gratis se leería como un bug, no como un castigo.
     *
     * @param base   pago ya bonificado por estudios
     * @param fatiga si el jugador arrastra fatiga ({@link DescansoService#tieneFatiga})
     * @return el pago final, nunca menor que 1
     */
    public static int conPenalizacionFatiga(int base, boolean fatiga) {
        return fatiga ? Math.max(1, (int) Math.round(base * PENAL_FATIGA)) : base;
    }

    /**
     * Aplica el bono de sueldo de los efectos pasivos al pago <b>ya bonificado por estudios</b>.
     * <b>Puro.</b>
     *
     * <p>Es multiplicativo respecto al bono de estudios ({@code base × 1,25 × 1,30}), no aditivo:
     * mantiene los dos topes independientes y evita que un sistema eclipse al otro. Satura en
     * {@code Pasivos.TOPES.get(SUELDO)} (+30 %), igual que {@link #conBonoEstudios} con el suyo.
     *
     * @param base pago ya bonificado por estudios
     * @param bono bono de sueldo de los pasivos (fracción; ya viene topado del service, pero se
     *             vuelve a topar aquí para que la función sea segura por sí sola)
     * @return el pago con el bono aplicado
     */
    public static int conBonoPasivos(int base, double bono) {
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.SUELDO), Math.max(0, bono));
        return (int) Math.round(base * (1 + aplicado));
    }

    /**
     * Cooldown efectivo de currar con el bono de pasivos aplicado. <b>Puro.</b>
     *
     * <p>{@link #COOLDOWN_WORK} sigue siendo el valor base de 60 min y no se toca ni se renombra;
     * el bono es una <b>reducción</b> topada al 25 %, así que el suelo son <b>45 minutos</b> y nunca
     * puede salir un cooldown negativo por muchos vehículos que se acumulen.
     *
     * @param bono reducción de los pasivos (fracción positiva); valores negativos se ignoran
     * @return el cooldown a respetar entre turnos
     */
    public static Duration cooldownEfectivo(double bono) {
        double aplicado = Math.min(Pasivos.TOPES.get(Pasivos.Tipo.COOLDOWN_WORK), Math.max(0, bono));
        return Duration.ofSeconds(Math.round(COOLDOWN_WORK.getSeconds() * (1 - aplicado)));
    }

    /** Bonos pasivos del jugador, o el mapa vacío si el service no está inyectado. */
    private Map<Pasivos.Tipo, Double> bonosDe(long discordId) {
        return pasivos == null ? Map.of() : pasivos.bonosDe(discordId);
    }

    /** Lectura defensiva de un tipo concreto del mapa de bonos. */
    private static double bono(Map<Pasivos.Tipo, Double> bonos, Pasivos.Tipo tipo) {
        return bonos.getOrDefault(tipo, 0.0);
    }
}
