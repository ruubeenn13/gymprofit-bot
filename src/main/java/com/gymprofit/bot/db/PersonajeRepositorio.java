package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repositorio JDBC de {@link Personaje} (tabla {@code personajes}). {@link #obtenerOCrear} garantiza
 * la fila del personaje (requiere que exista antes la fila de {@code usuarios_discord}, por la FK).
 */
public final class PersonajeRepositorio {

    private final DataSource dataSource;

    public PersonajeRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Devuelve el personaje, creándolo con valores por defecto si no existe. */
    public Personaje obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO personajes (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el personaje " + discordId, e);
        }
        return buscar(discordId).orElseThrow(() ->
                new DatabaseException("El personaje " + discordId + " no existe tras crearlo", null));
    }

    /** Personaje por id, si existe. */
    public Optional<Personaje> buscar(long discordId) {
        String sql = "SELECT discord_id, fuerza, resistencia, carisma, energia, salud "
                + "FROM personajes WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el personaje " + discordId, e);
        }
    }

    private static Personaje mapear(ResultSet rs) throws SQLException {
        return new Personaje(rs.getLong("discord_id"), rs.getInt("fuerza"), rs.getInt("resistencia"),
                rs.getInt("carisma"), rs.getInt("energia"), rs.getInt("salud"));
    }
}
