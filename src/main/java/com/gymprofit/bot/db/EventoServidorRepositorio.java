package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repositorio JDBC de {@link EventoServidor} (tabla {@code eventos_servidor}). Un registro por
 * servidor con el reto de la semana y el próximo evento. Sigue el patrón del resto de repos:
 * conexión por operación del pool y consultas parametrizadas.
 */
public final class EventoServidorRepositorio {

    private final DataSource dataSource;

    public EventoServidorRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Reto/evento del servidor, o {@link EventoServidor#vacio(long)} si aún no hay registro. */
    public EventoServidor obtener(long guildId) {
        String sql = "SELECT guild_id, reto_texto, evento_nombre, evento_fin "
                + "FROM eventos_servidor WHERE guild_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : EventoServidor.vacio(guildId);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando los eventos del servidor " + guildId, e);
        }
    }

    /** Fija el reto de la semana (conserva el evento). */
    public void guardarReto(long guildId, String retoTexto) {
        String sql = "INSERT INTO eventos_servidor (guild_id, reto_texto) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE reto_texto = VALUES(reto_texto)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, retoTexto);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando el reto del servidor " + guildId, e);
        }
    }

    /** Fija el próximo evento (nombre + fin en epoch; conserva el reto). */
    public void guardarEvento(long guildId, String nombre, long finEpoch) {
        String sql = "INSERT INTO eventos_servidor (guild_id, evento_nombre, evento_fin) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "evento_nombre = VALUES(evento_nombre), evento_fin = VALUES(evento_fin)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.setString(2, nombre);
            ps.setLong(3, finEpoch);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando el evento del servidor " + guildId, e);
        }
    }

    private static EventoServidor mapear(ResultSet rs) throws SQLException {
        return new EventoServidor(
                rs.getLong("guild_id"),
                rs.getString("reto_texto"),
                rs.getString("evento_nombre"),
                (Long) rs.getObject("evento_fin"));
    }
}
