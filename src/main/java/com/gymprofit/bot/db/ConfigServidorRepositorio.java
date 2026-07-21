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
 * Repositorio JDBC de {@link ConfigServidor} (tabla {@code config_servidor}). Un registro por
 * servidor; los canales y roles se guardan como IDs (o {@code null} si no están configurados).
 */
public final class ConfigServidorRepositorio {

    private final DataSource dataSource;

    public ConfigServidorRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Busca la configuración de un servidor. */
    public Optional<ConfigServidor> buscar(long guildId) {
        String sql = "SELECT guild_id, idioma, canal_bienvenida, canal_ejercicio_dia, "
                + "canal_logros, canal_sugerencias, canal_soporte, canal_bot_logs, "
                + "rol_objetivo_fuerza, rol_objetivo_cardio, rol_objetivo_perdida_peso, "
                + "rol_objetivo_general FROM config_servidor WHERE guild_id = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la config del servidor " + guildId, e);
        }
    }

    /** Servidores con canal de ejercicio del día configurado (los destinos del job diario). */
    public List<ConfigServidor> listarConEjercicioDia() {
        String sql = "SELECT guild_id, idioma, canal_bienvenida, canal_ejercicio_dia, "
                + "canal_logros, canal_sugerencias, canal_soporte, canal_bot_logs, "
                + "rol_objetivo_fuerza, rol_objetivo_cardio, rol_objetivo_perdida_peso, "
                + "rol_objetivo_general FROM config_servidor WHERE canal_ejercicio_dia IS NOT NULL";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ConfigServidor> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new DatabaseException("Error listando servidores con ejercicio del día", e);
        }
    }

    /** Devuelve la configuración del servidor, creándola por defecto si no existía. */
    public ConfigServidor obtenerOCrear(long guildId) {
        String sql = "INSERT INTO config_servidor (guild_id) VALUES (?) "
                + "ON DUPLICATE KEY UPDATE guild_id = guild_id";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error creando la config del servidor " + guildId, e);
        }
        return buscar(guildId).orElseThrow(() ->
                new DatabaseException("La config del servidor " + guildId + " no existe tras crearla", null));
    }

    /** Inserta o actualiza (upsert) toda la configuración del servidor. */
    public void guardar(ConfigServidor c) {
        String sql = "INSERT INTO config_servidor "
                + "(guild_id, idioma, canal_bienvenida, canal_ejercicio_dia, canal_logros, "
                + "canal_sugerencias, canal_soporte, canal_bot_logs, rol_objetivo_fuerza, "
                + "rol_objetivo_cardio, rol_objetivo_perdida_peso, rol_objetivo_general) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE idioma = VALUES(idioma), "
                + "canal_bienvenida = VALUES(canal_bienvenida), "
                + "canal_ejercicio_dia = VALUES(canal_ejercicio_dia), "
                + "canal_logros = VALUES(canal_logros), "
                + "canal_sugerencias = VALUES(canal_sugerencias), "
                + "canal_soporte = VALUES(canal_soporte), "
                + "canal_bot_logs = VALUES(canal_bot_logs), "
                + "rol_objetivo_fuerza = VALUES(rol_objetivo_fuerza), "
                + "rol_objetivo_cardio = VALUES(rol_objetivo_cardio), "
                + "rol_objetivo_perdida_peso = VALUES(rol_objetivo_perdida_peso), "
                + "rol_objetivo_general = VALUES(rol_objetivo_general)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, c.guildId());
            ps.setString(2, c.idioma());
            ps.setObject(3, c.canalBienvenida());
            ps.setObject(4, c.canalEjercicioDia());
            ps.setObject(5, c.canalLogros());
            ps.setObject(6, c.canalSugerencias());
            ps.setObject(7, c.canalSoporte());
            ps.setObject(8, c.canalBotLogs());
            ps.setObject(9, c.rolObjetivoFuerza());
            ps.setObject(10, c.rolObjetivoCardio());
            ps.setObject(11, c.rolObjetivoPerdidaPeso());
            ps.setObject(12, c.rolObjetivoGeneral());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error guardando la config del servidor " + c.guildId(), e);
        }
    }

    private static ConfigServidor mapear(ResultSet rs) throws SQLException {
        return new ConfigServidor(
                rs.getLong("guild_id"),
                rs.getString("idioma"),
                (Long) rs.getObject("canal_bienvenida"),
                (Long) rs.getObject("canal_ejercicio_dia"),
                (Long) rs.getObject("canal_logros"),
                (Long) rs.getObject("canal_sugerencias"),
                (Long) rs.getObject("canal_soporte"),
                (Long) rs.getObject("canal_bot_logs"),
                (Long) rs.getObject("rol_objetivo_fuerza"),
                (Long) rs.getObject("rol_objetivo_cardio"),
                (Long) rs.getObject("rol_objetivo_perdida_peso"),
                (Long) rs.getObject("rol_objetivo_general")
        );
    }
}
