package com.gymprofit.bot.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Repositorio JDBC del estado de descanso ({@code descanso}). {@link #obtenerOCrear} garantiza la
 * fila (requiere que exista antes {@code usuarios_discord}, por la FK). Sin fila = despierto y sin
 * fatiga.
 *
 * <p>No hay método de borrado a propósito: el olvido RGPD borra {@code usuarios_discord} y la fila
 * se va por el {@code ON DELETE CASCADE} de la FK, igual que el resto de tablas del jugador.
 */
public final class DescansoRepositorio {

    private final DataSource dataSource;

    public DescansoRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Estado de descanso, creándolo con valores por defecto si no existe. */
    public DescansoEstado obtenerOCrear(long discordId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT IGNORE INTO descanso (discord_id) VALUES (?)")) {
            ps.setLong(1, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando el descanso de " + discordId, e);
        }
        String sql = "SELECT discord_id, dormido_desde, ultimo_despertar, consumidos_hoy, "
                + "dia_consumos, cama_pagada FROM descanso WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DatabaseException("El descanso " + discordId
                            + " no existe tras crearlo", null);
                }
                return mapear(rs);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando el descanso de " + discordId, e);
        }
    }

    /**
     * Estado de descanso <b>sin crearlo</b> si no existe. Lo usa el export de privacidad: consultar
     * tus datos no puede generar datos nuevos (RGPD, ADR-009), así que ahí no vale
     * {@link #obtenerOCrear}.
     */
    public Optional<DescansoEstado> buscar(long discordId) {
        String sql = "SELECT discord_id, dormido_desde, ultimo_despertar, consumidos_hoy, "
                + "dia_consumos, cama_pagada FROM descanso WHERE discord_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error consultando el descanso de " + discordId, e);
        }
    }

    private static DescansoEstado mapear(ResultSet rs) throws SQLException {
        Timestamp dd = rs.getTimestamp("dormido_desde");
        Timestamp ud = rs.getTimestamp("ultimo_despertar");
        Date dc = rs.getDate("dia_consumos");
        return new DescansoEstado(rs.getLong("discord_id"),
                dd == null ? null : dd.toInstant(),
                ud == null ? null : ud.toInstant(),
                rs.getInt("consumidos_hoy"),
                dc == null ? null : dc.toLocalDate(),
                rs.getString("cama_pagada"));
    }

    /**
     * Acuesta al jugador: marca {@code dormido_desde} y, si pagó, la cama.
     *
     * @param camaPagada {@code "hotel"} si pagó por dormir, o {@code null} para usar la suya
     */
    public void acostar(long discordId, Instant ahora, String camaPagada) {
        ejecutar("UPDATE descanso SET dormido_desde = ?, cama_pagada = ? WHERE discord_id = ?",
                ps -> {
                    ps.setTimestamp(1, Timestamp.from(ahora));
                    ps.setString(2, camaPagada);
                    ps.setLong(3, discordId);
                }, "acostando a " + discordId);
    }

    /**
     * Levanta al jugador: borra {@code dormido_desde}, sella el despertar (para la fatiga) y limpia
     * la cama pagada, que solo vale para esa noche.
     */
    public void levantar(long discordId, Instant ahora) {
        ejecutar("UPDATE descanso SET dormido_desde = NULL, cama_pagada = NULL, "
                + "ultimo_despertar = ? WHERE discord_id = ?", ps -> {
            ps.setTimestamp(1, Timestamp.from(ahora));
            ps.setLong(2, discordId);
        }, "levantando a " + discordId);
    }

    /**
     * Registra un consumible del día (saciedad). Si {@code dia_consumos} no es hoy, reinicia el
     * contador a 1 y fija el día; si ya es hoy, incrementa. Atómico en una sola sentencia.
     */
    public void registrarConsumo(long discordId, LocalDate hoy) {
        // OJO CON EL ORDEN DE LOS SET: MySQL evalúa las asignaciones de izquierda a derecha, así que
        // el CASE de consumidos_hoy lee el dia_consumos VIEJO (aún no reasignado) y por eso puede
        // detectar el cambio de día. Si se intercambian las dos cláusulas, dia_consumos ya valdría
        // hoy cuando se evalúe el CASE, este daría siempre TRUE y el contador NO se reiniciaría
        // nunca: fallo silencioso que ningún test detecta.
        ejecutar("UPDATE descanso SET "
                + "consumidos_hoy = CASE WHEN dia_consumos = ? THEN consumidos_hoy + 1 ELSE 1 END, "
                + "dia_consumos = ? WHERE discord_id = ?", ps -> {
            ps.setDate(1, Date.valueOf(hoy));
            ps.setDate(2, Date.valueOf(hoy));
            ps.setLong(3, discordId);
        }, "registrando consumo de " + discordId);
    }

    /** Plantilla de UPDATE con manejo uniforme de errores. */
    private void ejecutar(String sql, SqlBinder binder, String contexto) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error " + contexto, e);
        }
    }

    /** Enlaza los parámetros de un PreparedStatement (permite lambdas que lanzan SQLException). */
    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
