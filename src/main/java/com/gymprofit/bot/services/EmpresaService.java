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

    /**
     * Nivel máximo de una empresa. Cada nivel da +2 % de ingresos a todos los miembros
     * ({@link TrabajoService#BONUS_POR_NIVEL}), así que el tope acota el bonus a +20 %.
     */
    public static final int NIVEL_MAX = 10;

    /**
     * Coste (en coins del <b>bote</b>, quemados) de subir del nivel {@code n} al {@code n+1}: es
     * lineal ({@code 50 000 · n}), de modo que cada nivel cuesta más que el anterior y la mejora es una
     * decisión de tesorería de la empresa, no un trámite.
     *
     * @param nivelActual nivel del que se parte (1..{@value #NIVEL_MAX}-1)
     */
    public static long costeNivel(int nivelActual) {
        return 50_000L * nivelActual;
    }

    /** Resultado de fundar una empresa. */
    public enum ResultadoFundar { OK, SIN_TRABAJO, NO_ES_TIER4, YA_EN_EMPRESA, NOMBRE_EN_USO, SIN_SALDO }

    /** Resultado de mejorar (subir un nivel) una empresa. */
    public enum ResultadoMejora { OK, NO_ERES_DUENO, TOPE, SIN_FONDOS }

    /** Resultado de crear una pertenencia (invitar o solicitar). */
    public enum ResultadoIngreso { OK, SIN_TRABAJO, OTRA_RAMA, YA_EN_EMPRESA, EMPRESA_NO_EXISTE, YA_PENDIENTE, ES_MISMO }

    /** Resultado de resolver una pendiente (aceptar/rechazar una invitación o solicitud). */
    public enum ResultadoResolver { ACEPTADO, RECHAZADO, NO_ERES_PARTE, PENDIENTE_NO_EXISTE, YA_EN_EMPRESA }

    /** Resultado de disolver una empresa. */
    public enum ResultadoDisolver { OK, SIN_EMPRESA, NO_ERES_DUENO }

    /** Resultado de alternar el flag de contratando de una empresa (bolsa de empleo F5c). */
    public enum ResultadoContratar { ABIERTA, CERRADA, SIN_EMPRESA, NO_AUTORIZADO }

    /**
     * Salida de {@code fundar}: el estado y, cuando es {@link ResultadoFundar#OK}, el id de la empresa
     * creada y los coins quemados (para pintarlos en el embed de celebración).
     */
    public record SalidaFundar(ResultadoFundar estado, long empresaId, long costeQuemado) {
    }

    /**
     * Salida de {@code mejorar}: el estado y, para el embed, el nivel resultante y el coste pagado del
     * bote. En los fallos {@code nivelNuevo}/{@code coste} valen lo que ayude al mensaje (nivel actual y
     * el coste que habría tocado) o 0 cuando no aplica.
     */
    public record SalidaMejora(ResultadoMejora estado, int nivelNuevo, long coste) {
    }

    /** Foto de una empresa para {@code info}: sus datos y la lista de miembros con rango. */
    public record InfoEmpresa(Empresa empresa, List<MiembroEmpresa> miembros) {
    }

    /** Fila del ranking ya puntuada y ordenada para la vista (F4). */
    public record FilaRanking(String nombre, String rama, int nivel, int miembros, long bote, long prestigio) {
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
     * Variante por id de {@link #solicitar}: el jugador solicita entrar en la empresa cuyo id sale del
     * tablón {@code /empleo} (bolsa de empleo F5c), con un motivo. Aplica exactamente las mismas reglas
     * que {@code solicitar} (tener trabajo, no estar ya en una empresa, y que la empresa sea de la propia
     * rama), pero identifica la empresa por id en vez de por nombre porque el jugador la elige de la lista
     * de contratación, no la escribe.
     *
     * @param discordId jugador que solicita
     * @param empresaId id de la empresa objetivo (elegida en el tablón de empleo)
     * @param motivo    carta del jugador (se recorta a {@value #MOTIVO_MAX} chars), puede ser {@code null}
     */
    public ResultadoIngreso solicitarPorId(long discordId, long empresaId, String motivo) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return ResultadoIngreso.SIN_TRABAJO;
        }
        if (repo.deMiembro(discordId).isPresent()) {
            return ResultadoIngreso.YA_EN_EMPRESA;
        }
        Optional<Empresa> empresa = repo.porId(empresaId);
        if (empresa.isEmpty()) {
            return ResultadoIngreso.EMPRESA_NO_EXISTE;
        }
        Ascensos.Rama rama = ramaDe(p.trabajo());
        // Solo se puede solicitar a empresas de la propia rama (aquí se valida a mano, ya que la búsqueda
        // por id no filtra por rama como sí lo hace porNombreYRama en solicitar).
        if (!empresa.get().rama().equals(rama.name())) {
            return ResultadoIngreso.OTRA_RAMA;
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

    /**
     * Alterna si la empresa del actor aparece en la bolsa de empleo (F5c). Solo un alto cargo
     * (DUENO/DIRECTIVO) puede: es un opt-in de gestión, no una acción de cualquier miembro. Reusa
     * {@code altosCargos} (mismo criterio que {@link EmpresaVentaService}) para la autorización.
     *
     * @param actorId jugador que ejecuta el toggle (debe ser dueño o directivo de su empresa)
     * @return ABIERTA/CERRADA según el nuevo estado, o SIN_EMPRESA/NO_AUTORIZADO si no procede
     */
    public ResultadoContratar alternarContratando(long actorId) {
        Optional<Empresa> emp = repo.deMiembro(actorId);
        if (emp.isEmpty()) {
            return ResultadoContratar.SIN_EMPRESA;
        }
        boolean altoCargo = repo.altosCargos(emp.get().id()).stream()
                .anyMatch(m -> m.discordId() == actorId);
        if (!altoCargo) {
            return ResultadoContratar.NO_AUTORIZADO;
        }
        boolean nuevo = !emp.get().contratando();
        repo.fijarContratando(emp.get().id(), nuevo);
        return nuevo ? ResultadoContratar.ABIERTA : ResultadoContratar.CERRADA;
    }

    /**
     * Rama de carrera del jugador según su trabajo actual (F5c, para el tablón {@code /empleo}).
     * Vacío si aún no tiene trabajo (sin rama no hay empresa a la que optar). Reusa la regla única
     * {@link #ramaDe(String)}, la misma que decide en qué rama funda o ingresa.
     */
    public Optional<Ascensos.Rama> ramaDeJugador(long discordId) {
        Personaje p = personajes.obtenerOCrear(discordId);
        if (p.trabajo() == null) {
            return Optional.empty();
        }
        return Optional.of(ramaDe(p.trabajo()));
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

    /**
     * Sube un nivel a la empresa del jugador pagándolo del bote. Solo el <b>dueño</b> puede; el coste
     * es {@link #costeNivel(int)} sobre el nivel actual y el tope es {@link #NIVEL_MAX}.
     *
     * <p>El cobro es el <b>gate atómico final</b> (igual que {@code fundar}/{@code ascender}):
     * {@link EmpresaRepositorio#gastarDelBote} valida el saldo del bote y descuenta en una sola
     * sentencia, y solo si devolvió {@code true} se sube el nivel. Así nunca se sube gratis ni el bote
     * queda negativo aunque dos mejoras concurran.
     *
     * @param discordId jugador que mejora (debe ser el dueño)
     * @return la salida con estado, nivel resultante y coste pagado
     */
    public SalidaMejora mejorar(long discordId) {
        Optional<Empresa> empresaOpt = repo.deMiembro(discordId);
        // Sin empresa propia (o no siendo su dueño) no hay nada que mejorar.
        if (empresaOpt.isEmpty() || empresaOpt.get().duenoId() != discordId) {
            return new SalidaMejora(ResultadoMejora.NO_ERES_DUENO, 0, 0);
        }
        Empresa e = empresaOpt.get();
        int nivelActual = e.nivel();
        if (nivelActual >= NIVEL_MAX) {
            return new SalidaMejora(ResultadoMejora.TOPE, nivelActual, 0);
        }
        long coste = costeNivel(nivelActual);
        // Cobro atómico del bote ANTES de subir nivel: si no hay saldo, gastarDelBote no toca nada y no
        // se sube el nivel (nunca se paga una mejora que no ocurre ni se sube sin pagar).
        if (!repo.gastarDelBote(e.id(), coste)) {
            return new SalidaMejora(ResultadoMejora.SIN_FONDOS, nivelActual, coste);
        }
        repo.subirNivel(e.id());
        return new SalidaMejora(ResultadoMejora.OK, nivelActual + 1, coste);
    }

    /**
     * Top de empresas por prestigio (F4). Trae todas (son pocas), puntua cada una con
     * {@link Prestigio#calcular} y devuelve las {@code limite} de mayor prestigio, de mayor a menor.
     *
     * @param limite número máximo de filas a devolver (el top que se pinta)
     * @return las empresas de mayor prestigio, ordenadas descendentemente
     */
    public List<FilaRanking> ranking(int limite) {
        return repo.ranking().stream()
                .map(e -> new FilaRanking(e.nombre(), e.rama(), e.nivel(), e.miembros(), e.bote(),
                        Prestigio.calcular(e.nivel(), e.miembros(), e.bote())))
                .sorted(java.util.Comparator.comparingLong(FilaRanking::prestigio).reversed())
                .limit(limite)
                .toList();
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
