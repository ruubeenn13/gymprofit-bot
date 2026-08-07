package com.gymprofit.bot.services;

import com.gymprofit.bot.db.*;
import java.util.Optional;

/**
 * Juego de trivia (F4): elige la siguiente pregunta no respondida y resuelve una respuesta. La respuesta
 * es one-shot y atómica: {@link TriviaRepositorio#registrarRespuesta} (INSERT IGNORE) es el gate; solo si
 * registra por primera vez y es acierto se paga el premio ({@link Trivia}) en coins + XP. Un doble clic o
 * una carrera no cobran dos veces.
 *
 * <p>Toda respuesta que pase el gate (acierto o fallo) suma además al marcador acumulado
 * ({@link TriviaScoreRepositorio}), que es la fuente del ranking y de las stats por usuario.
 */
public final class TriviaService {

    public enum Estado { ACIERTO, FALLO, YA_RESPONDIDA, NO_EXISTE }
    /** Resultado de responder: estado + la letra correcta (para revelarla) + el premio si acertó. */
    public record Resultado(Estado estado, char correcta, long coins, int xp) {}

    private final TriviaRepositorio repo;
    private final EconomiaRepositorio economia;
    private final XpService xp;
    private final UsuarioDiscordRepositorio usuarios;
    private final TriviaScoreRepositorio scores;

    public TriviaService(TriviaRepositorio repo, EconomiaRepositorio economia, XpService xp,
                         UsuarioDiscordRepositorio usuarios, TriviaScoreRepositorio scores) {
        this.repo = repo; this.economia = economia; this.xp = xp; this.usuarios = usuarios;
        this.scores = scores;
    }

    /** Siguiente pregunta no respondida (idioma es/en); vacío si no quedan. */
    public Optional<TriviaPregunta> siguiente(long actorId, Optional<String> categoria, boolean es) {
        return repo.aleatoriaNoRespondida(actorId, categoria, es);
    }

    /** Resuelve una respuesta al botón {@code letra} para la pregunta {@code preguntaId}. */
    public Resultado responder(long actorId, long preguntaId, char letra, boolean es) {
        Optional<TriviaPregunta> pOpt = repo.porId(preguntaId, es);
        if (pOpt.isEmpty()) return new Resultado(Estado.NO_EXISTE, ' ', 0, 0);
        TriviaPregunta p = pOpt.get();
        boolean acierto = Character.toUpperCase(letra) == p.correcta();
        usuarios.obtenerOCrear(actorId);
        // Gate one-shot atómico: si ya estaba respondida, no re-registra ni cobra.
        if (!repo.registrarRespuesta(actorId, preguntaId, acierto)) {
            return new Resultado(Estado.YA_RESPONDIDA, p.correcta(), 0, 0);
        }
        // Solo las respuestas que pasan el gate cuentan en el marcador: así la racha y los contadores
        // no se inflan con doble clics, y el fallo también cuenta (rompe la racha viva).
        scores.registrar(actorId, acierto);
        if (!acierto) return new Resultado(Estado.FALLO, p.correcta(), 0, 0);
        Trivia.Recompensa rec = Trivia.recompensa(p.dificultad());
        economia.ingresar(actorId, rec.coins(), "trivia");
        xp.ganarXp(actorId, rec.xp());
        return new Resultado(Estado.ACIERTO, p.correcta(), rec.coins(), rec.xp());
    }

    /** Preguntas distintas acertadas del banco (progreso X/total); vive en {@code trivia_respuestas}. */
    public int aciertosDe(long actorId) { return repo.aciertosDe(actorId); }
    public int total() { return repo.total(Optional.empty()); }

    /** Top por aciertos acumulados, leído del marcador (más barato que recontar las respuestas). */
    public java.util.List<TriviaRepositorio.FilaTrivia> ranking(int limite) { return scores.ranking(limite); }

    /** Marcador acumulado del usuario (aciertos/fallos/partidas/rachas); vacío si no ha jugado. */
    public Optional<TriviaScoreRepositorio.Marcador> marcador(long actorId) { return scores.de(actorId); }
}
