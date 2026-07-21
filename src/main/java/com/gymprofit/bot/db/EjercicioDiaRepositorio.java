package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Repositorio JDBC del histórico del ejercicio del día (tabla {@code ejercicio_dia}, V24).
 * La PK por fecha da la idempotencia del job: {@link #insertar} con {@code INSERT IGNORE}
 * devuelve si esta ejecución ganó la fila o ya existía (reinicio, carrera comando/job).
 */
public final class EjercicioDiaRepositorio {

    private final DataSource dataSource;

    public EjercicioDiaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** La elección de una fecha, si ya se hizo. */
    public Optional<EjercicioDia> buscarPorFecha(LocalDate fecha) {
        String sql = "SELECT fecha, ejercicio_id, ronda FROM ejercicio_dia WHERE fecha = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EjercicioDia(rs.getDate("fecha").toLocalDate(),
                        rs.getInt("ejercicio_id"), rs.getInt("ronda")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando el ejercicio del día " + fecha, e);
        }
    }

    /** Ronda en curso: la mayor registrada, o 1 si aún no hay ninguna fila. */
    public int rondaActual() {
        // COALESCE porque MAX() sobre tabla vacía devuelve NULL, y la primera ronda es la 1.
        String sql = "SELECT COALESCE(MAX(ronda), 1) FROM ejercicio_dia";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo la ronda actual", e);
        }
    }

    /** Ids del catálogo que ya han salido en una ronda (para no repetir hasta agotarla). */
    public Set<Integer> idsDeRonda(int ronda) {
        String sql = "SELECT ejercicio_id FROM ejercicio_dia WHERE ronda = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ronda);
            try (ResultSet rs = ps.executeQuery()) {
                Set<Integer> ids = new HashSet<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
                return ids;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error leyendo los ids de la ronda " + ronda, e);
        }
    }

    /**
     * Inserta la elección del día si nadie la hizo antes ({@code INSERT IGNORE} sobre la PK).
     *
     * @return {@code true} si esta llamada insertó la fila; {@code false} si ya existía
     */
    public boolean insertar(EjercicioDia dia) {
        String sql = "INSERT IGNORE INTO ejercicio_dia (fecha, ejercicio_id, ronda) VALUES (?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(dia.fecha()));
            ps.setInt(2, dia.ejercicioId());
            ps.setInt(3, dia.ronda());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando el ejercicio del día " + dia.fecha(), e);
        }
    }
}
