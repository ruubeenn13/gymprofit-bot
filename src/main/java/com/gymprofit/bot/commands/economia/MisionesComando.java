package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.MisionService;
import com.gymprofit.bot.services.MisionService.Vista;
import com.gymprofit.bot.util.Barras;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/**
 * {@code /misiones}: muestra las misiones de caza y tu progreso. Se completan solas al pelear (la
 * recompensa aparece en el embed de victoria); aquí solo se consulta.
 */
public final class MisionesComando implements Comando {

    private static final String NOMBRE = "misiones";

    private final MisionService misiones;

    public MisionesComando(MisionService misiones) {
        this.misiones = misiones;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.misiones.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.misiones.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.misiones.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(false).queue();

        StringBuilder sb = new StringBuilder(Messages.get(locale, "misiones.intro")).append("\n\n");
        for (Vista v : misiones.listar(evento.getUser().getIdLong())) {
            String barra = Barras.progreso(v.progreso(), v.mision().meta(), 8);
            sb.append(Messages.get(locale, "misiones.linea",
                    Messages.get(locale, "mision." + v.mision().id()),
                    v.progreso(), v.mision().meta(), barra,
                    v.mision().coins(), v.mision().xp())).append('\n');
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.RETO, locale,
                Messages.get(locale, "misiones.titulo"), sb.toString()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
