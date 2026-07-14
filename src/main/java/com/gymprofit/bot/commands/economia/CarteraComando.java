package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Acciones;
import com.gymprofit.bot.services.BolsaService;
import com.gymprofit.bot.services.BolsaService.CarteraVista;
import com.gymprofit.bot.services.BolsaService.PosicionVista;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /cartera}: tus acciones, su valor actual y las ganancias/pérdidas (P/L). */
public final class CarteraComando implements Comando {

    private static final String NOMBRE = "cartera";

    private final BolsaService bolsa;

    public CarteraComando(BolsaService bolsa) {
        this.bolsa = bolsa;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.cartera.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.cartera.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.cartera.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        CarteraVista c = bolsa.cartera(evento.getUser().getIdLong());
        String cuerpo;
        if (c.posiciones().isEmpty()) {
            cuerpo = Messages.get(locale, "cartera.vacia");
        } else {
            StringBuilder sb = new StringBuilder();
            for (PosicionVista p : c.posiciones()) {
                String emoji = Acciones.porId(p.accionId()).map(Acciones::emoji).orElse("📈");
                String signo = p.pl() >= 0 ? "🟢 +" : "🔴 ";
                sb.append(Messages.get(locale, "cartera.linea", emoji,
                        Messages.get(locale, "accion." + p.accionId()), p.cantidad(), p.valor(),
                        signo + p.pl())).append('\n');
            }
            String signoTotal = c.plTotal() >= 0 ? "🟢 +" : "🔴 ";
            sb.append('\n').append(Messages.get(locale, "cartera.total", c.valorTotal(),
                    signoTotal + c.plTotal()));
            cuerpo = sb.toString();
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "cartera.titulo"), cuerpo).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
