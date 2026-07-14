package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.PrecioAccion;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Acciones;
import com.gymprofit.bot.services.BolsaService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /bolsa}: muestra las acciones, su precio y la tendencia (↑↓). */
public final class BolsaComando implements Comando {

    private static final String NOMBRE = "bolsa";

    private final BolsaService bolsa;

    public BolsaComando(BolsaService bolsa) {
        this.bolsa = bolsa;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.bolsa.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.bolsa.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.bolsa.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        StringBuilder sb = new StringBuilder(Messages.get(locale, "bolsa.intro")).append("\n\n");
        for (PrecioAccion p : bolsa.precios()) {
            String emoji = Acciones.porId(p.id()).map(Acciones::emoji).orElse("📈");
            String tendencia = p.precio() > p.previo() ? "🟢▲"
                    : (p.precio() < p.previo() ? "🔴▼" : "⚪▬");
            sb.append(Messages.get(locale, "bolsa.linea", emoji,
                    Messages.get(locale, "accion." + p.id()), p.id().toUpperCase(Locale.ROOT),
                    p.precio(), tendencia)).append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "bolsa.titulo"), sb.toString()).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
