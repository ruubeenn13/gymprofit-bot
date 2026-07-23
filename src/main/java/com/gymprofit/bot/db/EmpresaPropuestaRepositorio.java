package com.gymprofit.bot.db;

import com.gymprofit.bot.services.RangoEmpresa;
import com.gymprofit.bot.services.TipoPropuesta;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC de la gobernanza de empresas ({@code empresa_propuestas}, {@code empresa_votos}).
 * Sigue el patrón de la capa {@code db/}: recibe el {@link DataSource} del {@link Database} y usa una
 * conexión del pool por operación, con sentencias parametrizadas. {@code tipo} y {@code rango_nuevo}
 * se persisten por {@code .name()} y se releen con {@code valueOf}; el UNIQUE {@code uq_propuesta_activa}
 * (empresa+tipo+objetivo) se propaga como {@link DatabaseException} para que el service lo traduzca a
 * error de usuario. Borrar una propuesta arrastra sus votos por el {@code ON DELETE CASCADE}.
 */
public final class EmpresaPropuestaRepositorio {

    private final DataSource dataSource;

    public EmpresaPropuestaRepositorio(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Abre una propuesta de gestión y devuelve su id generado. {@code rangoNuevo} solo aplica a
     * {@link TipoPropuesta#CAMBIAR_RANGO}; en el resto llega {@code null} y se persiste como NULL.
     * {@code dato} es la carga extra del {@link TipoPropuesta#ASCENSO} (puesto destino); en el resto
     * llega {@code null}. Propaga la violación de {@code uq_propuesta_activa} si ya hay una propuesta
     * activa idéntica (misma empresa, tipo y objetivo).
     */
    public long crear(long empresaId, TipoPropuesta tipo, long objetivoId, RangoEmpresa rangoNuevo,
                      long proponenteId, Instant expira, String dato) {
        String sql = "INSERT INTO empresa_propuestas "
                + "(empresa_id, tipo, objetivo_discord_id, rango_nuevo, proponente_discord_id, expira, dato) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, empresaId);
            ps.setString(2, tipo.name());
            ps.setLong(3, objetivoId);
            // rango_nuevo es NULL en SACAR/DESPEDIR: setNull para no escribir la cadena "null".
            if (rangoNuevo == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);
            } else {
                ps.setString(4, rangoNuevo.name());
            }
            ps.setLong(5, proponenteId);
            ps.setTimestamp(6, Timestamp.from(expira));
            // dato solo lo usa ASCENSO (puesto destino); NULL en el resto de tipos.
            if (dato == null) {
                ps.setNull(7, java.sql.Types.VARCHAR);
            } else {
                ps.setString(7, dato);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Error creando la propuesta " + tipo + " sobre " + objetivoId
                            + " en la empresa " + empresaId, e);
        }
    }

    /** Propuesta por id. */
    public Optional<Propuesta> porId(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_PROPUESTA + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearPropuesta(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error buscando la propuesta " + id, e);
        }
    }

    /** Propuestas activas de una empresa, ordenadas por antigüedad. */
    public List<Propuesta> activasDe(long empresaId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     SELECT_PROPUESTA + " WHERE empresa_id = ? ORDER BY creada ASC, id ASC")) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Propuesta> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearPropuesta(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando propuestas de la empresa " + empresaId, e);
        }
    }

    /**
     * Emite (o cambia) el voto de un alto cargo sobre una propuesta. UPSERT sobre la PK compuesta
     * {@code (propuesta_id, votante_discord_id)}: si el votante ya votó, se sobrescribe el sentido en
     * lugar de fallar por clave duplicada, de modo que cada uno mantiene un único voto vigente.
     */
    public void votar(long propuestaId, long votanteId, boolean si) {
        // ON DUPLICATE KEY UPDATE es el UPSERT uniforme del proyecto (MySQL 8): un segundo voto del
        // mismo alto cargo pisa el anterior en vez de violar la PK.
        String sql = "INSERT INTO empresa_votos (propuesta_id, votante_discord_id, voto) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE voto = VALUES(voto)";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, propuestaId);
            ps.setLong(2, votanteId);
            ps.setBoolean(3, si);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Error votando la propuesta " + propuestaId + " por " + votanteId, e);
        }
    }

    /** Votos emitidos sobre una propuesta, ordenados por votante. */
    public List<Voto> votos(long propuestaId) {
        String sql = "SELECT propuesta_id, votante_discord_id, voto FROM empresa_votos "
                + "WHERE propuesta_id = ? ORDER BY votante_discord_id ASC";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, propuestaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Voto> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapearVoto(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Error listando votos de la propuesta " + propuestaId, e);
        }
    }

    /** Cierra una propuesta (resuelta o caducada). El {@code ON DELETE CASCADE} borra sus votos. */
    public void cerrar(long propuestaId) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM empresa_propuestas WHERE id = ?")) {
            ps.setLong(1, propuestaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException("Error cerrando la propuesta " + propuestaId, e);
        }
    }

    private static final String SELECT_PROPUESTA =
            "SELECT id, empresa_id, tipo, objetivo_discord_id, rango_nuevo, proponente_discord_id, "
                    + "creada, expira, dato FROM empresa_propuestas";

    private static Propuesta mapearPropuesta(ResultSet rs) throws SQLException {
        String rango = rs.getString("rango_nuevo");
        return new Propuesta(
                rs.getLong("id"),
                rs.getLong("empresa_id"),
                TipoPropuesta.valueOf(rs.getString("tipo")),
                rs.getLong("objetivo_discord_id"),
                rango == null ? null : RangoEmpresa.valueOf(rango),
                rs.getLong("proponente_discord_id"),
                rs.getTimestamp("creada").toInstant(),
                rs.getTimestamp("expira").toInstant(),
                rs.getString("dato"));
    }

    private static Voto mapearVoto(ResultSet rs) throws SQLException {
        return new Voto(
                rs.getLong("propuesta_id"),
                rs.getLong("votante_discord_id"),
                rs.getBoolean("voto"));
    }
}
