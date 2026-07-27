package com.gymprofit.bot.db;

import com.gymprofit.bot.services.EventoEconomico;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Repositorio JDBC del clima económico (F5): la tabla {@code evento_economico} guarda como mucho UNA
 * fila (id=1) con el evento activo y su ventana. Tabla vacía = sin evento (estado neutro). {@code fijar}
 * hace upsert de esa fila; {@code limpiar} la borra.
 */
public final class EventoEconomicoRepositorio {

    private final DataSource dataSource;

    public EventoEconomicoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Evento activo, si lo hay. */
    public Optional<EventoActivo> activo() {
        String sql = "SELECT tipo, inicio, fin FROM evento_economico WHERE id = 1";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new EventoActivo(
                    EventoEconomico.valueOf(rs.getString("tipo")),
                    rs.getTimestamp("inicio").toInstant(),
                    rs.getTimestamp("fin").toInstant()));
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo el evento económico activo", e);
        }
    }

    /** Fija (upsert de la fila única) el evento activo y su ventana. */
    public void fijar(EventoEconomico tipo, Instant inicio, Instant fin) {
        String sql = "INSERT INTO evento_economico (id, tipo, inicio, fin) VALUES (1, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE tipo = VALUES(tipo), inicio = VALUES(inicio), fin = VALUES(fin)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ps.setTimestamp(2, Timestamp.from(inicio));
            ps.setTimestamp(3, Timestamp.from(fin));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error fijando el evento económico", e);
        }
    }

    /** Borra el evento activo (vuelve al estado neutro). */
    public void limpiar() {
        String sql = "DELETE FROM evento_economico WHERE id = 1";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error limpiando el evento económico", e);
        }
    }
}
