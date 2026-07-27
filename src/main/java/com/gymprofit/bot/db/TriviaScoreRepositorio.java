package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Marcador acumulado de trivia por usuario (tabla {@code trivia_scores}, F4). Cada respuesta nueva
 * incrementa aciertos/fallos/partidas y mantiene la racha de aciertos seguidos ({@code racha_actual}) y
 * la mejor histórica ({@code mejor_racha}) con un único UPSERT atómico. Es también la fuente del ranking
 * (más barato que recontar {@code trivia_respuestas}).
 */
public final class TriviaScoreRepositorio {

    /** Marcador de un usuario. */
    public record Marcador(int aciertos, int fallos, int partidas, int mejorRacha, int rachaActual) {}

    private final DataSource ds;

    public TriviaScoreRepositorio(DataSource ds) {
        this.ds = ds;
    }

    /**
     * Suma una respuesta al marcador del usuario en una sola sentencia atómica. En el UPDATE,
     * {@code mejor_racha} se evalúa ANTES que {@code racha_actual} para leer la racha vieja: si es
     * acierto, la racha nueva es la vieja+1 y la mejor es el máximo; si es fallo, la racha viva vuelve a 0.
     */
    public void registrar(long discordId, boolean acierto) {
        int ac = acierto ? 1 : 0;
        int fa = acierto ? 0 : 1;
        String sql =
                "INSERT INTO trivia_scores (discord_id, aciertos, fallos, partidas, mejor_racha, racha_actual) "
              + "VALUES (?, ?, ?, 1, ?, ?) "
              + "ON DUPLICATE KEY UPDATE "
              + "  aciertos = aciertos + VALUES(aciertos), "
              + "  fallos   = fallos   + VALUES(fallos), "
              + "  partidas = partidas + 1, "
              + "  mejor_racha = GREATEST(mejor_racha, IF(?, racha_actual + 1, racha_actual)), "
              + "  racha_actual = IF(?, racha_actual + 1, 0)";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            ps.setInt(2, ac);       // aciertos (VALUES)
            ps.setInt(3, fa);       // fallos (VALUES)
            ps.setInt(4, ac);       // mejor_racha inicial (INSERT)
            ps.setInt(5, ac);       // racha_actual inicial (INSERT)
            ps.setBoolean(6, acierto); // IF de mejor_racha (UPDATE)
            ps.setBoolean(7, acierto); // IF de racha_actual (UPDATE)
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DatabaseException("Error registrando el marcador de trivia", e);
        }
    }

    /** Marcador del usuario; vacío si no ha jugado. */
    public Optional<Marcador> de(long discordId) {
        String sql = "SELECT aciertos, fallos, partidas, mejor_racha, racha_actual "
                   + "FROM trivia_scores WHERE discord_id = ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Marcador(
                        rs.getInt("aciertos"), rs.getInt("fallos"), rs.getInt("partidas"),
                        rs.getInt("mejor_racha"), rs.getInt("racha_actual")));
            }
        } catch (Exception e) {
            throw new DatabaseException("Error leyendo el marcador de trivia", e);
        }
    }

    /** Top de jugadores por aciertos (desempate: mejor racha, luego el que llegó antes). */
    public List<TriviaRepositorio.FilaTrivia> ranking(int limite) {
        String sql = "SELECT discord_id, aciertos FROM trivia_scores WHERE aciertos > 0 "
                   + "ORDER BY aciertos DESC, mejor_racha DESC, actualizado_en ASC LIMIT ?";
        try (Connection con = ds.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<TriviaRepositorio.FilaTrivia> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(new TriviaRepositorio.FilaTrivia(rs.getLong("discord_id"), rs.getInt("aciertos")));
                }
                return res;
            }
        } catch (Exception e) {
            throw new DatabaseException("Error leyendo el ranking de trivia", e);
        }
    }
}
