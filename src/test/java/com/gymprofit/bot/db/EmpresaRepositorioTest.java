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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Métodos de gestión de F2: {@code actualizarRango} cambia el rango de un miembro,
     * {@code quitarMiembro} lo saca de la empresa y {@code altosCargos} devuelve solo DUENO/DIRECTIVO.
     */
    @Test
    void gestionDeMiembrosYAltosCargos() {
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

                // FK a usuarios_discord: sembrar antes de insertar filas con FK a esos ids (RGPD).
                long dueno = 401L;
                long directivo = 402L;
                long becario = 403L;
                for (long id : new long[]{dueno, directivo, becario}) {
                    usuarios.obtenerOCrear(id);
                }

                long empresaId = empresas.fundar("SALUD", dueno, "Clínica del Músculo");
                empresas.anadirMiembro(empresaId, directivo, RangoEmpresa.DIRECTIVO);
                empresas.anadirMiembro(empresaId, becario, RangoEmpresa.BECARIO);

                // actualizarRango: el becario asciende a ENCARGADO.
                empresas.actualizarRango(empresaId, becario, RangoEmpresa.ENCARGADO);
                RangoEmpresa rangoBecario = empresas.miembros(empresaId).stream()
                        .filter(m -> m.discordId() == becario).findFirst().orElseThrow().rango();
                assertEquals(RangoEmpresa.ENCARGADO, rangoBecario,
                        "actualizarRango cambia el rango del miembro");

                // altosCargos: solo DUENO + DIRECTIVO (el ENCARGADO no vota), ordenados por antigüedad.
                List<MiembroEmpresa> altos = empresas.altosCargos(empresaId);
                assertEquals(2, altos.size(), "solo DUENO y DIRECTIVO son altos cargos");
                assertEquals(RangoEmpresa.DUENO, altos.get(0).rango(), "el dueño (más antiguo) primero");
                assertEquals(RangoEmpresa.DIRECTIVO, altos.get(1).rango());

                // quitarMiembro: el ex-becario deja de aparecer en la empresa.
                empresas.quitarMiembro(empresaId, becario);
                assertTrue(empresas.miembros(empresaId).stream().noneMatch(m -> m.discordId() == becario),
                        "quitarMiembro saca al miembro de la empresa");
                assertEquals(2, empresas.miembros(empresaId).size(), "quedan dueño y directivo");
                assertTrue(empresas.deMiembro(becario).isEmpty(),
                        "el miembro quitado queda libre (sin empresa)");
            }
        }
    }

    /**
     * Economía de F3: {@code incrementarBote} suma al bote, {@code gastarDelBote} resta de forma atómica
     * (devuelve false y no deja el bote negativo si falta saldo), {@code subirNivel} incrementa el nivel y
     * {@code conBote} lista solo las empresas con bote positivo.
     */
    @Test
    void boteNivelYListadoConBote() {
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

                // FK a usuarios_discord: sembrar antes de fundar (RGPD).
                long dueno = 601L;
                long dueno2 = 602L;
                for (long id : new long[]{dueno, dueno2}) {
                    usuarios.obtenerOCrear(id);
                }

                long conDinero = empresas.fundar("SALUD", dueno, "Gimnasio del Bote");
                long sinDinero = empresas.fundar("ARTE", dueno2, "Taller Pelado");

                // incrementarBote suma al bote (arranca en 0 por el DEFAULT de V27).
                empresas.incrementarBote(conDinero, 500);
                assertEquals(500, empresas.porId(conDinero).orElseThrow().bote(), "el bote sube 500");

                // gastarDelBote con saldo suficiente: baja y devuelve true.
                assertTrue(empresas.gastarDelBote(conDinero, 200), "gasta con saldo y devuelve true");
                assertEquals(300, empresas.porId(conDinero).orElseThrow().bote(), "el bote queda en 300");

                // gastarDelBote con cantidad > bote: devuelve false y NO deja el bote negativo.
                assertFalse(empresas.gastarDelBote(conDinero, 1000), "sin saldo devuelve false");
                assertEquals(300, empresas.porId(conDinero).orElseThrow().bote(),
                        "el bote no cambia si el gasto no cabe (nunca negativo)");

                // subirNivel incrementa el nivel (arranca en 1 por el DEFAULT de V27).
                int nivelPrevio = empresas.porId(conDinero).orElseThrow().nivel();
                empresas.subirNivel(conDinero);
                assertEquals(nivelPrevio + 1, empresas.porId(conDinero).orElseThrow().nivel(),
                        "subirNivel suma uno al nivel");

                // conBote: solo la que tiene bote > 0.
                List<Empresa> conBote = empresas.conBote();
                assertEquals(1, conBote.size(), "solo una empresa tiene bote positivo");
                assertEquals(conDinero, conBote.get(0).id());
                assertTrue(conBote.stream().noneMatch(e -> e.id() == sinDinero),
                        "la empresa sin bote no aparece");
            }
        }
    }
}
