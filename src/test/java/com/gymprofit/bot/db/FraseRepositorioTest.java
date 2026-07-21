package com.gymprofit.bot.db;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba {@link FraseRepositorio} contra MySQL real con los seeds de la V2 (32 frases): la
 * aleatoria siempre devuelve algo y el texto sale en el idioma pedido. Se salta sin Docker
 * (npipe en local); corre en CI.
 */
class FraseRepositorioTest {

    @Test
    void devuelveFraseAleatoriaConAmbosIdiomas() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker no alcanzable por el cliente Java; el test corre en CI (Linux)");

        try (MySQLContainer<?> mysql =
                     new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                             .withDatabaseName("gymprofit_bot")) {
            mysql.start();
            try (Database db = new Database(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                db.migrar();
                FraseRepositorio repo = new FraseRepositorio(db.dataSource());

                Frase frase = repo.aleatoria().orElseThrow();
                assertFalse(frase.textoEs().isBlank());
                assertFalse(frase.textoEn().isBlank());
                assertTrue(frase.texto(Locale.forLanguageTag("en")).equals(frase.textoEn()));
                assertTrue(frase.texto(Locale.forLanguageTag("es")).equals(frase.textoEs()));
            }
        }
    }
}
