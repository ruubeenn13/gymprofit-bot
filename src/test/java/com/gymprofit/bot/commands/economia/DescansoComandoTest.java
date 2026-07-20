package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Camas;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.DescansoService.Desglose;
import com.gymprofit.bot.services.DescansoService.EstadoDespertar;
import com.gymprofit.bot.services.DescansoService.ResultadoDespertar;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que el embed de despertar <b>explica</b> la cifra: sin estos avisos, un «+47» tras
 * cuatro días dormido parece un fallo del bot (pasó en el servidor de pruebas).
 */
class DescansoComandoTest {

    /** Caso real: 4 días en el suelo con 13 de energía → +47, cortado por horas y por tope. */
    private static ResultadoDespertar cuatroDiasEnElSuelo() {
        Desglose d = DescansoService.desglosar(4 * 24 * 60, Camas.SUELO, 100, 13, 0);
        return new ResultadoDespertar(EstadoDespertar.OK, d.ganada(), 4 * 24 * 60, Camas.SUELO, d);
    }

    @Test
    @DisplayName("el embed de despertar explica el recorte de horas y el tope de la cama")
    void explicaPorQueNoHaGanadoMas() {
        MessageEmbed embed = DescansoComando.embedDespertar(Messages.ES, cuatroDiasEnElSuelo());
        String desc = embed.getDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("13"), desc);   // energía de partida
        assertTrue(desc.contains("60"), desc);   // tope del suelo
        assertTrue(desc.contains("47"), desc);   // ganancia real
        assertTrue(desc.contains("9 h"), desc);  // solo cuentan las primeras 9 h
        // Y le dice qué comprar para que no le vuelva a pasar.
        assertTrue(desc.contains(Messages.get(Messages.ES, "item.saco_dormir")), desc);
    }

    @Test
    @DisplayName("sin recortes no se cuelan avisos que confundan")
    void sinRecortesNoHayAvisos() {
        // 2 h en el suelo desde 0: ni tope ni recorte de horas.
        Desglose d = DescansoService.desglosar(120, Camas.SUELO, 100, 0, 0);
        MessageEmbed embed = DescansoComando.embedDespertar(Messages.ES,
                new ResultadoDespertar(EstadoDespertar.OK, d.ganada(), 120, Camas.SUELO, d));
        String desc = embed.getDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("20"), desc); // 2 h × 10/h
        assertTrue(!desc.contains("9 h"), desc);
    }

    @Test
    @DisplayName("en ingles tambien sale explicado")
    void tambienEnIngles() {
        String desc = DescansoComando.embedDespertar(Messages.EN, cuatroDiasEnElSuelo())
                .getDescription();

        assertNotNull(desc);
        assertTrue(desc.contains("9 h"), desc);
        assertTrue(desc.contains(Messages.get(Messages.EN, "item.saco_dormir")), desc);
    }
}
