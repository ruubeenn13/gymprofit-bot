package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DatabaseException;
import com.gymprofit.bot.db.EconomiaRepositorio;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.db.Pendiente;
import com.gymprofit.bot.db.Personaje;
import com.gymprofit.bot.db.PersonajeRepositorio;

import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de las <b>empresas</b> (Fase 1): fundar una empresa ligada a la rama del trabajo
 * del jugador, verla, disolverla, e ingresar por las dos vías con consentimiento (la empresa invita o
 * el jugador solicita con motivo). Devuelve enums de resultado como {@link TrabajoService}; los
 * embeds y la i18n viven en la capa de comandos.
 *
 * <p>La rama y el tier salen de la carrera del jugador igual que en los ascensos: la rama es la de su
 * trabajo actual ({@code Trabajos.porId(...).sector()} → {@link Ascensos#ramaDe}) y el tier alcanzado
 * lo resuelve {@link TrabajoService#tierAlcanzadoEn} (regla única, no se duplica aquí).
 *
 * <p>La pertenencia es <b>exclusiva</b> (un jugador, una empresa) y la garantiza en último término la
 * BD (UNIQUE {@code uq_miembro_unico} / {@code uq_empresa_nombre_rama} / {@code uq_pendiente_par}):
 * los chequeos previos evitan el caso normal, pero ante una carrera se confía en las restricciones y
 * su violación (una {@link DatabaseException}) se traduce al enum de error de usuario que toque.
 */
public final class EmpresaService {

    /**
     * Coste de fundar, en coins <b>quemados</b> (sumidero antiinflación, sin contrapartida). Es alto a
     * propósito: el ascenso a t4 ya cuesta 50 000, y fundar es la meta de late-game de una rama, así
     * que se fija al doble (100 000) para que sea una decisión de patrimonio, no un trámite.
     */
    public static final long COSTE_FUNDAR = 100_000L;

    /** Resultado de fundar una empresa. */
    public enum ResultadoFundar { OK, SIN_TRABAJO, NO_ES_TIER4, YA_EN_EMPRESA, NOMBRE_EN_USO, SIN_SALDO }

    /** Resultado de crear una pertenencia (invitar o solicitar). */
    public enum ResultadoIngreso { OK, SIN_TRABAJO, OTRA_RAMA, YA_EN_EMPRESA, EMPRESA_NO_EXISTE, YA_PENDIENTE, ES_MISMO }

    /** Resultado de resolver una pendiente (aceptar/rechazar una invitación o solicitud). */
    public enum ResultadoResolver { ACEPTADO, RECHAZADO, NO_ERES_PARTE, PENDIENTE_NO_EXISTE, YA_EN_EMPRESA }

    /** Resultado de disolver una empresa. */
    public enum ResultadoDisolver { OK, SIN_EMPRESA, NO_ERES_DUENO }

    /**
     * Salida de {@code fundar}: el estado y, cuando es {@link ResultadoFundar#OK}, el id de la empresa
     * creada y los coins quemados (para pintarlos en el embed de celebración).
     */
    public record SalidaFundar(ResultadoFundar estado, long empresaId, long costeQuemado) {
    }

    /** Foto de una empresa para {@code info}: sus datos y la lista de miembros con rango. */
    public record InfoEmpresa(Empresa empresa, List<MiembroEmpresa> miembros) {
    }

    /** Tope de caracteres del motivo de una solicitud (columna {@code motivo VARCHAR(300)}). */
    private static final int MOTIVO_MAX = 300;

    private final EmpresaRepositorio repo;
    private final PersonajeRepositorio personajes;
    private final EconomiaRepositorio economia;
    /** Fuente única del tier alcanzado por rama (no se reimplementa la regla del máximo aquí). */
    private final TrabajoService trabajos;

    public EmpresaService(EmpresaRepositorio repo, PersonajeRepositorio personajes,
                          EconomiaRepositorio economia, TrabajoService trabajos) {
        this.repo = repo;
        this.personajes = personajes;
        this.economia = economia;
        this.trabajos = trabajos;
    }

    /**
     * Funda una empresa en la rama del trabajo actual del jugador. Valida en orden: tener trabajo,
     * ser t4 de esa rama y no estar ya en una empresa; solo entonces intenta el cobro y crea la
     * empresa.
     *
     * <p>El cobro es el <b>gate atómico final</b>, igual que en {@code ascender}: {@code gastar}
     * valida el saldo y descuenta en una sola operación (no hay ventana TOCTOU con un pre-check de
     * saldo). Si tras cobrar el insert falla por el UNIQUE de nombre (se traduce a
     * {@link ResultadoFundar#NOMBRE_EN_USO}), se <b>reembolsa</b> el coste antes de devolver el error,
     * de modo que nunca se cobra una fundación que no ocurrió.
     *
     * @param discordId jugador que funda (será {@link RangoEmpresa#DUENO})
     * @param nombre    nombre de la empresa (único dentro de la rama)
     * @return la salida con estado, id y coste quemado
     */
    public SalidaFundar fundar(long discordId, String nombre) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return new SalidaFundar(ResultadoFundar.SIN_TRABAJO, 0, 0);
        }
        Ascensos.Rama rama = ramaDe(p.trabajo());
        if (trabajos.tierAlcanzadoEn(discordId, rama) != 4) {
            return new SalidaFundar(ResultadoFundar.NO_ES_TIER4, 0, 0);
        }
        if (repo.deMiembro(discordId).isPresent()) {
            return new SalidaFundar(ResultadoFundar.YA_EN_EMPRESA, 0, 0);
        }
        // El cobro es el gate atómico final (como ascender): gastar valida el saldo y descuenta en una
        // sola operación; no hay ventana TOCTOU. Si el insert falla por nombre en uso, se reembolsa.
        if (!economia.gastar(discordId, COSTE_FUNDAR, "fundar_empresa")) {
            return new SalidaFundar(ResultadoFundar.SIN_SALDO, 0, 0);
        }
        long empresaId;
        try {
            empresaId = repo.fundar(rama.name(), discordId, nombre);
        } catch (DatabaseException e) {
            // La red de seguridad de la BD saltó (nombre pillado en la rama, o pertenencia doble por
            // carrera). Ya se ha cobrado, así que se reembolsa antes de devolver el error.
            economia.ingresar(discordId, COSTE_FUNDAR, "reembolso_fundar");
            return new SalidaFundar(ResultadoFundar.NOMBRE_EN_USO, 0, 0);
        }
        return new SalidaFundar(ResultadoFundar.OK, empresaId, COSTE_FUNDAR);
    }

    /**
     * El dueño invita a un jugador a su empresa. El objetivo debe tener trabajo en la rama de la
     * empresa, no estar ya en ninguna y no ser el propio dueño; crea una pendiente {@code INVITACION}
     * que resolverá el invitado.
     *
     * @param duenoId   quien invita (debe ser dueño de una empresa)
     * @param objetivoId jugador invitado
     */
    public ResultadoIngreso invitar(long duenoId, long objetivoId) {
        Optional<Empresa> empresaOpt = repo.deMiembro(duenoId);
        // Sin empresa propia (o no siendo su dueño) no hay a dónde invitar.
        if (empresaOpt.isEmpty() || empresaOpt.get().duenoId() != duenoId) {
            return ResultadoIngreso.EMPRESA_NO_EXISTE;
        }
        if (objetivoId == duenoId) {
            return ResultadoIngreso.ES_MISMO;
        }
        Empresa empresa = empresaOpt.get();
        Personaje objetivo = personajes.obtenerOCrear(objetivoId);
        if (objetivo.trabajo() == null) {
            return ResultadoIngreso.SIN_TRABAJO;
        }
        if (!ramaDe(objetivo.trabajo()).name().equals(empresa.rama())) {
            return ResultadoIngreso.OTRA_RAMA;
        }
        if (repo.deMiembro(objetivoId).isPresent()) {
            return ResultadoIngreso.YA_EN_EMPRESA;
        }
        return crearPendiente(empresa.id(), objetivoId, TipoPendiente.INVITACION, null);
    }

    /**
     * El jugador solicita entrar en una empresa de su rama con un motivo. Debe tener trabajo, no estar
     * en ninguna empresa, y la empresa {@code nombre} debe existir en su rama; crea una pendiente
     * {@code SOLICITUD} que resolverá el dueño.
     *
     * @param discordId jugador que solicita
     * @param nombre    nombre de la empresa objetivo (dentro de su rama)
     * @param motivo    carta del jugador (se recorta a {@value #MOTIVO_MAX} chars), puede ser {@code null}
     */
    public ResultadoIngreso solicitar(long discordId, String nombre, String motivo) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return ResultadoIngreso.SIN_TRABAJO;
        }
        if (repo.deMiembro(discordId).isPresent()) {
            return ResultadoIngreso.YA_EN_EMPRESA;
        }
        Ascensos.Rama rama = ramaDe(p.trabajo());
        // Solo se puede solicitar a empresas de la propia rama: buscarla por nombre+rama lo garantiza.
        Optional<Empresa> empresa = repo.porNombreYRama(nombre, rama.name());
        if (empresa.isEmpty()) {
            return ResultadoIngreso.EMPRESA_NO_EXISTE;
        }
        return crearPendiente(empresa.get().id(), discordId, TipoPendiente.SOLICITUD, recortar(motivo));
    }

    /**
     * Resuelve una pendiente. Valida que quien resuelve es la parte correcta (una {@code INVITACION}
     * la resuelve el jugador invitado; una {@code SOLICITUD}, el dueño de la empresa) y revalida, al
     * aceptar, que el jugador no se haya metido en otra empresa entretanto. Aceptar lo añade como
     * {@link RangoEmpresa#BECARIO}; en ambos casos borra la pendiente.
     *
     * @param pendienteId pendiente a resolver
     * @param aceptar     {@code true} aceptar/aprobar, {@code false} rechazar
     * @param quienId     jugador que resuelve (debe ser la parte con potestad)
     */
    public ResultadoResolver resolver(long pendienteId, boolean aceptar, long quienId) {
        Optional<Pendiente> pendOpt = repo.pendiente(pendienteId);
        if (pendOpt.isEmpty()) {
            return ResultadoResolver.PENDIENTE_NO_EXISTE;
        }
        Pendiente pend = pendOpt.get();
        if (!esLaParte(pend, quienId)) {
            return ResultadoResolver.NO_ERES_PARTE;
        }
        if (!aceptar) {
            repo.borrarPendiente(pendienteId);
            return ResultadoResolver.RECHAZADO;
        }
        // Al aceptar, el jugador no puede haber entrado ya en otra empresa (chequeo + UNIQUE de red).
        if (repo.deMiembro(pend.discordId()).isPresent()) {
            return ResultadoResolver.YA_EN_EMPRESA;
        }
        try {
            repo.anadirMiembro(pend.empresaId(), pend.discordId(), RangoEmpresa.BECARIO);
        } catch (DatabaseException e) {
            // Carrera: se metió en otra empresa entre el chequeo y el insert; el UNIQUE lo frenó.
            return ResultadoResolver.YA_EN_EMPRESA;
        }
        repo.borrarPendiente(pendienteId);
        return ResultadoResolver.ACEPTADO;
    }

    /** Datos y miembros de la empresa del jugador, si pertenece a alguna. */
    public Optional<InfoEmpresa> infoDe(long discordId) {
        return repo.deMiembro(discordId).map(e -> new InfoEmpresa(e, repo.miembros(e.id())));
    }

    /** Datos y miembros de una empresa por nombre dentro de una rama. */
    public Optional<InfoEmpresa> infoPorNombreYRama(String nombre, String rama) {
        return repo.porNombreYRama(nombre, rama).map(e -> new InfoEmpresa(e, repo.miembros(e.id())));
    }

    /**
     * Disuelve la empresa del jugador. Solo el dueño puede: el borrado en cascada limpia miembros y
     * pendientes.
     */
    public ResultadoDisolver disolver(long discordId) {
        Optional<Empresa> empresaOpt = repo.deMiembro(discordId);
        if (empresaOpt.isEmpty()) {
            return ResultadoDisolver.SIN_EMPRESA;
        }
        if (empresaOpt.get().duenoId() != discordId) {
            return ResultadoDisolver.NO_ERES_DUENO;
        }
        repo.disolver(empresaOpt.get().id());
        return ResultadoDisolver.OK;
    }

    /** ¿Es {@code quienId} la parte con potestad para resolver esta pendiente? */
    private boolean esLaParte(Pendiente pend, long quienId) {
        return switch (pend.tipo()) {
            // La invitación la resuelve el jugador invitado.
            case INVITACION -> pend.discordId() == quienId;
            // La solicitud la resuelve el dueño de la empresa destino.
            case SOLICITUD -> repo.porId(pend.empresaId())
                    .map(e -> e.duenoId() == quienId).orElse(false);
        };
    }

    /** Crea la pendiente traduciendo el UNIQUE {@code uq_pendiente_par} a {@code YA_PENDIENTE}. */
    private ResultadoIngreso crearPendiente(long empresaId, long discordId, TipoPendiente tipo,
                                            String motivo) {
        try {
            repo.crearPendiente(empresaId, discordId, tipo, motivo);
            return ResultadoIngreso.OK;
        } catch (DatabaseException e) {
            // Ya había una pendiente para ese par empresa/jugador (el UNIQUE la frenó).
            return ResultadoIngreso.YA_PENDIENTE;
        }
    }

    /** Rama de un trabajo por su id (mismo camino que usa {@link TrabajoService}). */
    private static Ascensos.Rama ramaDe(String trabajoId) {
        return Ascensos.ramaDe(Trabajos.porId(trabajoId).orElseThrow().sector());
    }

    /** Recorta el motivo al tope de la columna; {@code null} pasa tal cual. */
    private static String recortar(String motivo) {
        if (motivo == null) {
            return null;
        }
        return motivo.length() <= MOTIVO_MAX ? motivo : motivo.substring(0, MOTIVO_MAX);
    }
}
