package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC de la trivia (F4): lee preguntas del banco resolviendo el idioma, y registra las
 * respuestas del jugador de forma one-shot (una por pregunta). Deriva aciertos y ranking de esas respuestas.
 */
public final class TriviaRepositorio {

    /** Fila del ranking: usuario y sus aciertos acumulados. */
    public record FilaTrivia(long discordId, int aciertos) {}

    private final DataSource dataSource;

    public TriviaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Escoge al azar una pregunta que el jugador aún no haya respondido, opcionalmente filtrando
     * por categoría.
     */
    public Optional<TriviaPregunta> aleatoriaNoRespondida(long discordId, Optional<String> categoria, boolean es) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM trivia_preguntas p WHERE NOT EXISTS ("
          + "SELECT 1 FROM trivia_respuestas r WHERE r.pregunta_id = p.id AND r.discord_id = ?)");
        if (categoria.isPresent()) sql.append(" AND p.categoria = ?");
        sql.append(" ORDER BY RAND() LIMIT 1");
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setLong(1, discordId);
            if (categoria.isPresent()) ps.setString(2, categoria.get());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs, es)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo pregunta de trivia", e);
        }
    }

    /** Busca una pregunta por id (para validar la respuesta tras el botón). */
    public Optional<TriviaPregunta> porId(long id, boolean es) {
        String sql = "SELECT * FROM trivia_preguntas WHERE id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs, es)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la pregunta " + id, e);
        }
    }

    /** Registra la respuesta. {@code true} si se registró (primera vez); {@code false} si ya existía (one-shot). */
    public boolean registrarRespuesta(long discordId, long preguntaId, boolean acierto) {
        String sql = "INSERT IGNORE INTO trivia_respuestas (discord_id, pregunta_id, acierto) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setLong(2, preguntaId);
            ps.setBoolean(3, acierto);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Error registrando respuesta de trivia", e);
        }
    }

    /** Número de aciertos acumulados del jugador. */
    public int aciertosDe(long discordId) {
        String sql = "SELECT COUNT(*) FROM trivia_respuestas WHERE discord_id = ? AND acierto = TRUE";
        return contar(sql, ps -> ps.setLong(1, discordId));
    }

    /** Tamaño del banco de preguntas, opcionalmente filtrado por categoría. */
    public int total(Optional<String> categoria) {
        String sql = "SELECT COUNT(*) FROM trivia_preguntas" + (categoria.isPresent() ? " WHERE categoria = ?" : "");
        return contar(sql, ps -> { if (categoria.isPresent()) ps.setString(1, categoria.get()); });
    }

    /** Top de jugadores por aciertos, de mayor a menor. */
    public List<FilaTrivia> ranking(int limite) {
        String sql = "SELECT discord_id, COUNT(*) AS aciertos FROM trivia_respuestas WHERE acierto = TRUE "
                   + "GROUP BY discord_id ORDER BY aciertos DESC LIMIT ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<FilaTrivia> res = new ArrayList<>();
                while (rs.next()) res.add(new FilaTrivia(rs.getLong("discord_id"), rs.getInt("aciertos")));
                return res;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo el ranking de trivia", e);
        }
    }

    /** Mapea la fila actual de {@code trivia_preguntas} resolviendo las columnas del idioma pedido. */
    private TriviaPregunta mapear(ResultSet rs, boolean es) throws SQLException {
        String suf = es ? "_es" : "_en";
        return new TriviaPregunta(
            rs.getLong("id"), rs.getString("categoria"), rs.getString("dificultad"),
            rs.getString("pregunta" + suf),
            List.of(rs.getString("opcion_a" + suf), rs.getString("opcion_b" + suf),
                    rs.getString("opcion_c" + suf), rs.getString("opcion_d" + suf)),
            rs.getString("correcta").charAt(0));
    }

    /** Callback para parametrizar un {@link PreparedStatement} antes de ejecutar un COUNT. */
    @FunctionalInterface
    private interface Bind {
        void apply(PreparedStatement ps) throws SQLException;
    }

    private int contar(String sql, Bind bind) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind.apply(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error contando en trivia", e);
        }
    }
}
