package com.gymprofit.bot.db;

import com.gymprofit.bot.services.RangoEmpresa;
import com.gymprofit.bot.services.TipoPropuesta;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link EmpresaPropuestaRepositorio} contra MySQL real (Testcontainers): las
 * propuestas se crean con y sin {@code rangoNuevo} y vuelven íntegras, {@code activasDe} las lista por
 * empresa, {@code votar} hace UPSERT (un votante mantiene un solo voto vigente), {@code votos} los
 * lista, {@code cerrar} borra la propuesta y sus votos en cascada, y el UNIQUE {@code uq_propuesta_activa}
 * bloquea dos propuestas idénticas activas. Siembra {@code usuarios_discord} antes de insertar filas
 * con FK a ese id (RGPD) y crea una empresa de base. Se salta si el cliente Docker no es alcanzable en
 * local; corre en CI (Linux).
 */
class EmpresaPropuestaRepositorioTest {

    @Test
    void propuestasVotosUpsertYCierreEnCascada() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                var usuarios = new UsuarioDiscordRepositorio(db.dataSource());
                var empresas = new EmpresaRepositorio(db.dataSource());
                var propuestas = new EmpresaPropuestaRepositorio(db.dataSource());

                // FK a usuarios_discord: todo discord_id debe existir antes de insertarlo con FK a él.
                long dueno = 501L;
                long objetivo = 502L;
                long objetivo2 = 503L;
                long votante1 = 504L;
                long votante2 = 505L;
                for (long id : new long[]{dueno, objetivo, objetivo2, votante1, votante2}) {
                    usuarios.obtenerOCrear(id);
                }
                long empresaId = empresas.fundar("SALUD", dueno, "Gimnasio Central");
                Instant expira = Instant.now().plus(1, ChronoUnit.DAYS);

                // crear + porId con rangoNuevo (CAMBIAR_RANGO): el record vuelve completo. dato NULL.
                long idCambio = propuestas.crear(
                        empresaId, TipoPropuesta.CAMBIAR_RANGO, objetivo, RangoEmpresa.ENCARGADO, dueno, expira, null);
                assertTrue(idCambio > 0, "crear devuelve el id generado");
                Propuesta cambio = propuestas.porId(idCambio).orElseThrow();
                assertEquals(empresaId, cambio.empresaId());
                assertEquals(TipoPropuesta.CAMBIAR_RANGO, cambio.tipo());
                assertEquals(objetivo, cambio.objetivoId());
                assertEquals(RangoEmpresa.ENCARGADO, cambio.rangoNuevo());
                assertEquals(dueno, cambio.proponenteId());
                assertEquals(expira.truncatedTo(ChronoUnit.SECONDS),
                        cambio.expira().truncatedTo(ChronoUnit.SECONDS), "expira ida y vuelta");
                assertNull(cambio.dato(), "CAMBIAR_RANGO no lleva dato");

                // crear sin rangoNuevo (SACAR): rangoNuevo NULL vuelve como null en el record.
                long idSacar = propuestas.crear(
                        empresaId, TipoPropuesta.SACAR, objetivo2, null, dueno, expira, null);
                Propuesta sacar = propuestas.porId(idSacar).orElseThrow();
                assertEquals(TipoPropuesta.SACAR, sacar.tipo());
                assertNull(sacar.rangoNuevo(), "SACAR no lleva rango destino");

                // crear con dato (ASCENSO): el puesto destino viaja en dato y vuelve íntegro por porId.
                long idAscenso = propuestas.crear(
                        empresaId, TipoPropuesta.ASCENSO, objetivo, null, dueno, expira, "cocinero");
                Propuesta ascenso = propuestas.porId(idAscenso).orElseThrow();
                assertEquals(TipoPropuesta.ASCENSO, ascenso.tipo());
                assertEquals("cocinero", ascenso.dato(), "ASCENSO conserva el puesto destino en dato");

                // activasDe: lista las tres propuestas de la empresa (CAMBIAR_RANGO, SACAR, ASCENSO).
                List<Propuesta> activas = propuestas.activasDe(empresaId);
                assertEquals(3, activas.size(), "las tres propuestas de la empresa");

                // votar UPSERT: votante1 vota Sí y luego No -> una sola fila con el último valor.
                propuestas.votar(idCambio, votante1, true);
                propuestas.votar(idCambio, votante1, false);
                propuestas.votar(idCambio, votante2, true);
                List<Voto> votos = propuestas.votos(idCambio);
                assertEquals(2, votos.size(), "el UPSERT no duplica el voto de votante1");
                Voto v1 = votos.stream().filter(v -> v.votanteId() == votante1).findFirst().orElseThrow();
                assertFalse(v1.si(), "el segundo voto (No) pisa al primero (Sí)");
                Voto v2 = votos.stream().filter(v -> v.votanteId() == votante2).findFirst().orElseThrow();
                assertTrue(v2.si(), "votante2 mantiene su Sí");

                // UNIQUE uq_propuesta_activa: misma empresa+tipo+objetivo no se puede duplicar activa.
                assertThrows(DatabaseException.class,
                        () -> propuestas.crear(
                                empresaId, TipoPropuesta.CAMBIAR_RANGO, objetivo, RangoEmpresa.DIRECTIVO, dueno, expira, null),
                        "propuesta activa idéntica viola uq_propuesta_activa");

                // cerrar: tras cerrar, porId vacío y votos vacío (CASCADE borra los votos).
                propuestas.cerrar(idCambio);
                assertTrue(propuestas.porId(idCambio).isEmpty(), "la propuesta cerrada desaparece");
                assertTrue(propuestas.votos(idCambio).isEmpty(), "la cascada borra los votos");
                assertEquals(2, propuestas.activasDe(empresaId).size(), "quedan SACAR y ASCENSO");
            }
        }
    }
}
