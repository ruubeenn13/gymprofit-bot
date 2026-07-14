package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.MercadoService;
import com.gymprofit.bot.services.MercadoService.RetirarEstado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /retirar <anuncio>}: retira un anuncio propio del mercado y recupera los ítems. */
public final class RetirarComando implements Comando {

    private static final String NOMBRE = "retirar";

    private final MercadoService mercado;

    public RetirarComando(MercadoService mercado) {
        this.mercado = mercado;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData id = new OptionData(OptionType.INTEGER, "anuncio",
                Messages.get(Messages.ES, "comando.retirar.opcion.anuncio"), true)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.retirar.opcion.anuncio"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.retirar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.retirar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.retirar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(id);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long anuncio = evento.getOption("anuncio").getAsLong();

        evento.deferReply(true).queue();
        RetirarEstado r = mercado.retirar(evento.getUser().getIdLong(), anuncio);
        String mensaje = switch (r) {
            case OK -> Messages.get(locale, "retirar.ok");
            case NO_EXISTE -> Messages.get(locale, "comprarmercado.noexiste");
            case NO_TUYO -> Messages.get(locale, "retirar.notuyo");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
