package com.gymprofit.bot.services;

import com.gymprofit.bot.db.DatabaseException;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaPropuestaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.db.PersonajeRepositorio;
import com.gymprofit.bot.db.Propuesta;
import com.gymprofit.bot.db.Voto;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de <b>gobernanza</b> de las empresas (Fase 2): gestionar la plantilla por rango. El
 * <b>Dueño</b> actúa directo (cambiar rango, sacar, despedir) sobre cualquier rango inferior; un
 * <b>Directivo</b> no ejecuta, sino que <b>propone</b> y los altos cargos (Dueño + Directivos) lo
 * votan. La propuesta se aprueba por mayoría estricta de los altos cargos, con el voto del Dueño como
 * desempate, y caduca a las {@value #DURACION_PROPUESTA_HORAS} h. Devuelve enums de resultado como
 * {@link EmpresaService}; los embeds y la i18n viven en la capa de comandos.
 *
 * <p>La <b>regla de rango</b> (nunca tocar a un igual o superior; y al cambiar rango, el nuevo debe ser
 * inferior al del actor y nunca {@link RangoEmpresa#DUENO}) acota el abuso y se <b>revalida al
 * ejecutar</b> una propuesta aprobada, porque el objetivo pudo cambiar de rango o salir de la empresa
 * entre que se propuso y se aprobó.
 *
 * <p>El reloj es inyectable ({@link Clock}) para que el cálculo de {@code expira} y la caducidad sean
 * deterministas en test, igual que en {@link EjercicioDiaService}.
 */
public final class EmpresaGestionService {

    private static final long DURACION_PROPUESTA_HORAS = 48;

    /** Vida de una propuesta antes de caducar sin resolverse. */
    private static final Duration DURACION_PROPUESTA = Duration.ofHours(DURACION_PROPUESTA_HORAS);

    /** Resultado de {@link #gestionar}. */
    public enum ResultadoGestion {
        EJECUTADA, PROPUESTA_CREADA, NO_AUTORIZADO, RANGO_INVALIDO, NO_ES_MIEMBRO, YA_HAY_PROPUESTA
    }

    /** Resultado de {@link #votar}. */
    public enum ResultadoVoto {
        REGISTRADO, APROBADA_EJECUTADA, RECHAZADA, CADUCADA, NO_AUTORIZADO, NO_EXISTE
    }

    private final EmpresaRepositorio repo;
    private final EmpresaPropuestaRepositorio propuestas;
    private final PersonajeRepositorio personajes;
    private final Clock reloj;

    /** Reloj inyectable para deterministas la expira/caducidad en test. */
    public EmpresaGestionService(EmpresaRepositorio repo, EmpresaPropuestaRepositorio propuestas,
                                 PersonajeRepositorio personajes, Clock reloj) {
        this.repo = repo;
        this.propuestas = propuestas;
        this.personajes = personajes;
        this.reloj = reloj;
    }

    /**
     * Gestiona a un miembro de la empresa del actor. Valida en orden: el actor pertenece a una empresa,
     * el objetivo es miembro de la MISMA empresa, el actor es alto cargo, y la regla de rango. Luego, si
     * el actor es {@link RangoEmpresa#DUENO} ejecuta directo; si es {@link RangoEmpresa#DIRECTIVO} abre
     * una propuesta y la vota que sí; cualquier otro rango no está autorizado.
     *
     * @param actorId    quien gestiona
     * @param tipo       acción de gestión
     * @param objetivoId miembro sobre el que se actúa
     * @param rangoNuevo rango destino (solo en {@link TipoPropuesta#CAMBIAR_RANGO}, {@code null} en el resto)
     */
    public ResultadoGestion gestionar(long actorId, TipoPropuesta tipo, long objetivoId,
                                      RangoEmpresa rangoNuevo) {
        Optional<Empresa> empresaOpt = repo.deMiembro(actorId);
        if (empresaOpt.isEmpty()) {
            return ResultadoGestion.NO_AUTORIZADO;
        }
        Empresa empresa = empresaOpt.get();
        List<MiembroEmpresa> miembros = repo.miembros(empresa.id());
        RangoEmpresa rangoActor = rangoDe(miembros, actorId);
        RangoEmpresa rangoObjetivo = rangoDe(miembros, objetivoId);
        if (rangoObjetivo == null) {
            return ResultadoGestion.NO_ES_MIEMBRO;
        }
        // Solo los altos cargos gestionan: un empleado/encargado no autorizado, aunque el objetivo sea
        // inferior a él. El resto de la regla (no tocar a un igual/superior) se comprueba después.
        if (rangoActor != RangoEmpresa.DUENO && rangoActor != RangoEmpresa.DIRECTIVO) {
            return ResultadoGestion.NO_AUTORIZADO;
        }
        if (!rangoValido(rangoActor, rangoObjetivo, tipo, rangoNuevo)) {
            return ResultadoGestion.RANGO_INVALIDO;
        }
        if (rangoActor == RangoEmpresa.DUENO) {
            ejecutar(empresa.id(), tipo, objetivoId, rangoNuevo);
            return ResultadoGestion.EJECUTADA;
        }
        // Directivo: no ejecuta, propone y firma su voto a favor. La UNIQUE uq_propuesta_activa
        // (empresa+tipo+objetivo) frena una segunda propuesta idéntica y se traduce a YA_HAY_PROPUESTA.
        try {
            long id = propuestas.crear(empresa.id(), tipo, objetivoId, rangoNuevo, actorId,
                    Instant.now(reloj).plus(DURACION_PROPUESTA), null);
            propuestas.votar(id, actorId, true);
            return ResultadoGestion.PROPUESTA_CREADA;
        } catch (DatabaseException e) {
            return ResultadoGestion.YA_HAY_PROPUESTA;
        }
    }

    /**
     * Registra el voto de un alto cargo sobre una propuesta y resuelve si ya hay veredicto. Si la
     * propuesta caducó, se cierra sin registrar el voto. Tras votar, recuenta y aplica el predicado:
     * aprobada → revalida la regla de rango y ejecuta; rechazada → cierra; en otro caso queda registrada
     * a la espera de más votos.
     *
     * @param propuestaId propuesta a votar
     * @param votanteId   alto cargo que vota
     * @param si          {@code true} a favor, {@code false} en contra
     */
    public ResultadoVoto votar(long propuestaId, long votanteId, boolean si) {
        Optional<Propuesta> propOpt = propuestas.porId(propuestaId);
        if (propOpt.isEmpty()) {
            return ResultadoVoto.NO_EXISTE;
        }
        Propuesta prop = propOpt.get();
        if (Instant.now(reloj).isAfter(prop.expira())) {
            // Caducó sin resolverse: se cierra y el voto no cuenta.
            propuestas.cerrar(prop.id());
            return ResultadoVoto.CADUCADA;
        }
        List<MiembroEmpresa> altos = repo.altosCargos(prop.empresaId());
        if (altos.stream().noneMatch(m -> m.discordId() == votanteId)) {
            return ResultadoVoto.NO_AUTORIZADO;
        }
        propuestas.votar(prop.id(), votanteId, si);

        // Recuento sobre el censo de altos cargos de AHORA (N), no sobre los votos emitidos: los ausentes
        // cuentan para exigir mayoría estricta del total. Además, un voto de quien votó siendo alto cargo
        // pero luego fue degradado o sacado durante las 48 h NO cuenta (los votos no se purgan al cambiar
        // el rango, solo al cerrar la propuesta), así que se intersecta con el censo actual.
        List<Voto> votos = propuestas.votos(prop.id());
        java.util.Set<Long> censo = altos.stream()
                .map(MiembroEmpresa::discordId)
                .collect(java.util.stream.Collectors.toSet());
        int n = altos.size();
        int sies = 0;
        int noes = 0;
        for (Voto v : votos) {
            if (!censo.contains(v.votanteId())) {
                continue; // voto fantasma: ya no es alto cargo
            }
            if (v.si()) {
                sies++;
            } else {
                noes++;
            }
        }
        Long duenoId = altos.stream()
                .filter(m -> m.rango() == RangoEmpresa.DUENO)
                .map(MiembroEmpresa::discordId)
                .findFirst().orElse(null);
        boolean duenoSi = duenoId != null && votos.stream().anyMatch(v -> v.votanteId() == duenoId && v.si());
        boolean duenoNo = duenoId != null && votos.stream().anyMatch(v -> v.votanteId() == duenoId && !v.si());

        if (aprobada(n, sies, duenoSi)) {
            // Revalidación: entre proponer y aprobar el objetivo pudo ascender o salir de la empresa, así
            // que la regla de rango se comprueba de nuevo contra el estado actual antes de tocar nada. Si
            // ya no es ejecutable, se cierra sin efecto (se trata como rechazada: quedó resuelta).
            if (revalidaEjecutable(prop)) {
                ejecutar(prop.empresaId(), prop.tipo(), prop.objetivoId(), prop.rangoNuevo());
                propuestas.cerrar(prop.id());
                return ResultadoVoto.APROBADA_EJECUTADA;
            }
            propuestas.cerrar(prop.id());
            return ResultadoVoto.RECHAZADA;
        }
        if (rechazada(n, noes, duenoNo)) {
            propuestas.cerrar(prop.id());
            return ResultadoVoto.RECHAZADA;
        }
        return ResultadoVoto.REGISTRADO;
    }

    /** Aprobada si los Sí son mayoría estricta de los N altos cargos, o hay empate y el Dueño votó Sí. */
    private boolean aprobada(int n, int si, boolean duenoVotoSi) {
        return si * 2 > n || (si * 2 == n && duenoVotoSi);
    }

    /** Rechazada si los No son mayoría estricta de los N altos cargos, o hay empate y el Dueño votó No. */
    private boolean rechazada(int n, int no, boolean duenoVotoNo) {
        return no * 2 > n || (no * 2 == n && duenoVotoNo);
    }

    /** Regla de rango: el actor manda sobre el objetivo y, al cambiar rango, el destino es inferior y no Dueño. */
    private boolean rangoValido(RangoEmpresa rangoActor, RangoEmpresa rangoObjetivo, TipoPropuesta tipo,
                                RangoEmpresa rangoNuevo) {
        if (rangoActor.ordinal() <= rangoObjetivo.ordinal()) {
            return false;
        }
        if (tipo == TipoPropuesta.CAMBIAR_RANGO) {
            return rangoNuevo != RangoEmpresa.DUENO && rangoNuevo.ordinal() < rangoActor.ordinal();
        }
        return true;
    }

    /** ¿Sigue siendo ejecutable la propuesta contra el estado actual (objetivo miembro e inferior al proponente)? */
    private boolean revalidaEjecutable(Propuesta prop) {
        List<MiembroEmpresa> miembros = repo.miembros(prop.empresaId());
        RangoEmpresa rangoObjetivo = rangoDe(miembros, prop.objetivoId());
        RangoEmpresa rangoProponente = rangoDe(miembros, prop.proponenteId());
        if (rangoObjetivo == null || rangoProponente == null) {
            return false;
        }
        return rangoValido(rangoProponente, rangoObjetivo, prop.tipo(), prop.rangoNuevo());
    }

    /**
     * Ejecuta la acción de gestión sobre el objetivo (compartida entre el camino directo del dueño y la
     * aprobación por voto): cambiar rango actualiza la columna; sacar quita al miembro conservando su
     * trabajo; despedir lo quita y además lo manda al paro ({@code fijarTrabajo(objetivo, null)}).
     */
    private void ejecutar(long empresaId, TipoPropuesta tipo, long objetivoId, RangoEmpresa rangoNuevo) {
        switch (tipo) {
            case CAMBIAR_RANGO -> repo.actualizarRango(empresaId, objetivoId, rangoNuevo);
            case SACAR -> repo.quitarMiembro(empresaId, objetivoId);
            case DESPEDIR -> {
                repo.quitarMiembro(empresaId, objetivoId);
                // Despedir manda al paro (a diferencia de sacar, que conserva el trabajo).
                personajes.fijarTrabajo(objetivoId, null);
            }
        }
    }

    /** Rango de un miembro concreto dentro de la lista de miembros; {@code null} si no está. */
    private static RangoEmpresa rangoDe(List<MiembroEmpresa> miembros, long discordId) {
        return miembros.stream()
                .filter(m -> m.discordId() == discordId)
                .map(MiembroEmpresa::rango)
                .findFirst().orElse(null);
    }
}
