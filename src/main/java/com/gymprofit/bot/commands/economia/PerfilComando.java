package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.EconomiaService;
import com.gymprofit.bot.services.EconomiaService.Perfil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /perfil}: muestra el personaje (atributos, energía, salud) y el saldo, tuyo o de otro. */
public final class PerfilComando implements Comando {

    private static final String NOMBRE = "perfil";

    private final EconomiaService economia;

    public PerfilComando(EconomiaService economia) {
        this.economia = economia;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.perfil.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.perfil.opcion.usuario"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.perfil.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.perfil.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.perfil.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption("usuario") != null
                ? evento.getOption("usuario").getAsUser() : evento.getUser();

        evento.deferReply(true).queue();
        Perfil p = economia.perfil(objetivo.getIdLong());
        String desc = Messages.get(locale, "perfil.cuerpo",
                p.coins(), p.personaje().energia(), p.personaje().salud(),
                p.personaje().fuerza(), p.personaje().resistencia(), p.personaje().carisma());
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "perfil.titulo", objetivo.getName()), desc)
                .setThumbnail(objetivo.getEffectiveAvatarUrl()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }
}
