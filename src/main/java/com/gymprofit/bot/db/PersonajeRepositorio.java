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
        String sql = "SELECT discord_id, fuerza, resistencia, carisma, energia, salud, trabajo, "
                + "ultimo_work, arma, armadura FROM personajes WHERE discord_id = ?";
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

    /** Fija el trabajo actual del personaje ({@code null} = en paro). */
    public void fijarTrabajo(long discordId, String trabajo) {
        ejecutar("UPDATE personajes SET trabajo = ? WHERE discord_id = ?", ps -> {
            ps.setString(1, trabajo);
            ps.setLong(2, discordId);
        });
    }

    /**
     * Aplica un turno de trabajo: descuenta {@code energia} y marca {@code ultimo_work = NOW()}, solo
     * si hay energía suficiente. Devuelve {@code true} si se aplicó.
     */
    public boolean trabajar(long discordId, int energiaCoste) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE personajes SET energia = energia - ?, ultimo_work = NOW() "
                             + "WHERE discord_id = ? AND energia >= ?")) {
            ps.setInt(1, energiaCoste);
            ps.setLong(2, discordId);
            ps.setInt(3, energiaCoste);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error aplicando trabajo a " + discordId, e);
        }
    }

    /**
     * Entrena un atributo: gasta {@code energiaCoste} y sube el atributo en 1, solo si hay energía.
     * {@code atributo} debe ser una columna válida (fuerza/resistencia/carisma). Devuelve si se aplicó.
     */
    public boolean entrenar(long discordId, String atributo, int energiaCoste) {
        if (!atributo.equals("fuerza") && !atributo.equals("resistencia")
                && !atributo.equals("carisma")) {
            throw new IllegalArgumentException("Atributo no válido: " + atributo);
        }
        String sql = "UPDATE personajes SET " + atributo + " = " + atributo + " + 1, "
                + "energia = energia - ? WHERE discord_id = ? AND energia >= ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, energiaCoste);
            ps.setLong(2, discordId);
            ps.setInt(3, energiaCoste);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Error entrenando a " + discordId, e);
        }
    }

    /** Suma {@code cantidad} a un atributo (fuerza/resistencia/carisma). Para el árbol de mejoras. */
    public void sumarAtributo(long discordId, String atributo, int cantidad) {
        if (!atributo.equals("fuerza") && !atributo.equals("resistencia")
                && !atributo.equals("carisma")) {
            throw new IllegalArgumentException("Atributo no válido: " + atributo);
        }
        ejecutar("UPDATE personajes SET " + atributo + " = " + atributo + " + ? WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, cantidad);
                    ps.setLong(2, discordId);
                });
    }

    /** Suma energía a un personaje (tope 100). Para consumibles. */
    public void sumarEnergia(long discordId, int cantidad) {
        ejecutar("UPDATE personajes SET energia = LEAST(100, energia + ?) WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, cantidad);
                    ps.setLong(2, discordId);
                });
    }

    /** Suma salud a un personaje (tope 100). Para consumibles. */
    public void sumarSalud(long discordId, int cantidad) {
        ejecutar("UPDATE personajes SET salud = LEAST(100, salud + ?) WHERE discord_id = ?",
                ps -> {
                    ps.setInt(1, cantidad);
                    ps.setLong(2, discordId);
                });
    }

    /** Regenera energía a todos los personajes (job periódico): {@code +cantidad}, tope 100. */
    public int regenerarEnergia(int cantidad) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE personajes SET energia = LEAST(100, energia + ?) WHERE energia < 100")) {
            ps.setInt(1, cantidad);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error regenerando energía", e);
        }
    }

    private interface Param {
        void set(PreparedStatement ps) throws SQLException;
    }

    private void ejecutar(String sql, Param param) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            param.set(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error en la operación de personaje", e);
        }
    }

    /**
     * Equipa (o desequipa) un ítem en una ranura de combate. {@code ranura} debe ser
     * {@code arma} o {@code armadura}; {@code itemId} = {@code null} desequipa. No valida posesión
     * (eso lo hace el service antes de llamar).
     */
    public void fijarEquipo(long discordId, String ranura, String itemId) {
        if (!ranura.equals("arma") && !ranura.equals("armadura")) {
            throw new IllegalArgumentException("Ranura no válida: " + ranura);
        }
        ejecutar("UPDATE personajes SET " + ranura + " = ? WHERE discord_id = ?", ps -> {
            ps.setString(1, itemId);
            ps.setLong(2, discordId);
        });
    }

    private static Personaje mapear(ResultSet rs) throws SQLException {
        java.sql.Timestamp uw = rs.getTimestamp("ultimo_work");
        return new Personaje(rs.getLong("discord_id"), rs.getInt("fuerza"), rs.getInt("resistencia"),
                rs.getInt("carisma"), rs.getInt("energia"), rs.getInt("salud"),
                rs.getString("trabajo"), uw == null ? null : uw.toInstant(),
                rs.getString("arma"), rs.getString("armadura"));
    }
}
