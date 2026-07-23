package com.gymprofit.bot.db;

import com.gymprofit.bot.services.RangoEmpresa;
import com.gymprofit.bot.services.TipoPendiente;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba end-to-end de {@link EmpresaRepositorio} contra MySQL real (Testcontainers): la fundación es
 * transaccional (empresa + dueño DUENO), los UNIQUE de nombre-por-rama y de pertenencia exclusiva
 * bloquean duplicados, los pendientes se crean/listan/filtran/borran y la disolución arrastra en
 * cascada miembros y pendientes. Siembra {@code usuarios_discord} antes de insertar filas con FK a
 * ese id (RGPD). Se salta si el cliente Docker no es alcanzable en local; corre en CI (Linux).
 */
class EmpresaRepositorioTest {

    @Test
    void fundacionPendientesYDisolucionEnCascada() {
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

                // FK a usuarios_discord: todo discord_id debe existir antes de insertarlo en empresas.
                long dueno = 301L;
                long dueno2 = 302L;
                long miembro = 303L;
                long solicitante = 304L;
                long invitado = 305L;
                for (long id : new long[]{dueno, dueno2, miembro, solicitante, invitado}) {
                    usuarios.obtenerOCrear(id);
                }

                // fundar: devuelve id y da de alta al dueño como DUENO.
                long empresaId = empresas.fundar("SALUD", dueno, "Gimnasio Central");
                assertTrue(empresaId > 0, "fundar devuelve el id generado");

                Optional<Empresa> deDueno = empresas.deMiembro(dueno);
                assertTrue(deDueno.isPresent(), "el dueño pertenece a la empresa recién fundada");
                assertEquals(empresaId, deDueno.get().id());

                List<MiembroEmpresa> tras = empresas.miembros(empresaId);
                assertEquals(1, tras.size(), "solo el dueño al fundar");
                assertEquals(RangoEmpresa.DUENO, tras.get(0).rango(), "el fundador es DUENO");
                assertEquals(dueno, tras.get(0).discordId());

                // porNombreYRama / porId / deRama devuelven lo esperado.
                assertEquals(empresaId, empresas.porNombreYRama("Gimnasio Central", "SALUD").orElseThrow().id());
                assertEquals("Gimnasio Central", empresas.porId(empresaId).orElseThrow().nombre());
                assertEquals(1, empresas.deRama("SALUD").size());
                assertTrue(empresas.deRama("ARTE").isEmpty(), "otra rama no tiene empresas");

                // UNIQUE uq_empresa_nombre_rama: mismo nombre+rama no se puede fundar dos veces.
                assertThrows(DatabaseException.class,
                        () -> empresas.fundar("SALUD", dueno2, "Gimnasio Central"),
                        "nombre repetido en la misma rama viola el UNIQUE");
                // La transacción de esa fundación fallida hizo rollback: no dejó al dueño2 como miembro.
                assertTrue(empresas.deMiembro(dueno2).isEmpty(),
                        "el rollback de fundar no deja miembro huérfano");

                // anadirMiembro + miembros listan con el rango correcto.
                empresas.anadirMiembro(empresaId, miembro, RangoEmpresa.BECARIO);
                List<MiembroEmpresa> conBecario = empresas.miembros(empresaId);
                assertEquals(2, conBecario.size());
                assertEquals(RangoEmpresa.BECARIO,
                        conBecario.stream().filter(m -> m.discordId() == miembro).findFirst()
                                .orElseThrow().rango());

                // UNIQUE uq_miembro_unico: un jugador no puede estar en dos empresas a la vez.
                long otra = empresas.fundar("ARTE", dueno2, "Taller de Arte");
                assertThrows(DatabaseException.class,
                        () -> empresas.anadirMiembro(otra, miembro, RangoEmpresa.BECARIO),
                        "el mismo jugador en otra empresa viola uq_miembro_unico");

                // Pendientes: solicitud con motivo.
                long idSol = empresas.crearPendiente(empresaId, solicitante, TipoPendiente.SOLICITUD, "quiero entrar");
                Pendiente sol = empresas.pendiente(idSol).orElseThrow();
                assertEquals(TipoPendiente.SOLICITUD, sol.tipo());
                assertEquals("quiero entrar", sol.motivo());
                assertEquals(solicitante, sol.discordId());

                // Invitación (sin motivo) a otro jugador.
                empresas.crearPendiente(empresaId, invitado, TipoPendiente.INVITACION, null);

                // pendientesDe filtra por tipo: solo la solicitud.
                List<Pendiente> solicitudes = empresas.pendientesDe(empresaId, TipoPendiente.SOLICITUD);
                assertEquals(1, solicitudes.size());
                assertEquals(idSol, solicitudes.get(0).id());

                // invitacionesA filtra INVITACION del jugador destino.
                List<Pendiente> invs = empresas.invitacionesA(invitado);
                assertEquals(1, invs.size());
                assertEquals(TipoPendiente.INVITACION, invs.get(0).tipo());
                assertTrue(empresas.invitacionesA(solicitante).isEmpty(),
                        "el solicitante no tiene invitaciones (tiene una solicitud)");

                // borrarPendiente quita la solicitud.
                empresas.borrarPendiente(idSol);
                assertTrue(empresas.pendiente(idSol).isEmpty(), "el pendiente borrado desaparece");
                assertTrue(empresas.pendientesDe(empresaId, TipoPendiente.SOLICITUD).isEmpty());

                // disolver borra en cascada miembros y pendientes de la empresa.
                empresas.disolver(empresaId);
                assertTrue(empresas.miembros(empresaId).isEmpty(), "sin miembros tras disolver");
                assertTrue(empresas.invitacionesA(invitado).isEmpty(),
                        "los pendientes de la empresa caen por la cascada");
                assertTrue(empresas.porId(empresaId).isEmpty(), "la empresa ya no existe");
            }
        }
    }
}
