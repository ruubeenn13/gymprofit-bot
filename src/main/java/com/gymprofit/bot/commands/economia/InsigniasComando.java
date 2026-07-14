package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.InsigniaService;
import com.gymprofit.bot.services.InsigniaService.Vista;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/** {@code /insignias}: muestra tus logros (desbloqueados y por conseguir); otorga los recién cumplidos. */
public final class InsigniasComando implements Comando {

    private static final String NOMBRE = "insignias";

    private final InsigniaService insignias;

    public InsigniasComando(InsigniaService insignias) {
        this.insignias = insignias;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.insignias.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.insignias.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.insignias.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(false).queue();

        List<Vista> vistas = insignias.listar(evento.getUser().getIdLong());
        long conseguidas = vistas.stream().filter(Vista::ganada).count();

        StringBuilder sb = new StringBuilder(
                Messages.get(locale, "insignias.intro", conseguidas, vistas.size())).append("\n\n");
        for (Vista v : vistas) {
            String estado = v.ganada() ? "✅" : "🔒";
            sb.append(estado).append(' ').append(v.insignia().emoji()).append(' ')
                    .append("**").append(Messages.get(locale, "insignia." + v.insignia().id()))
                    .append("** — ")
                    .append(Messages.get(locale, "insignia." + v.insignia().id() + ".desc"))
                    .append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.LOGRO, locale,
                Messages.get(locale, "insignias.titulo"), sb.toString()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
