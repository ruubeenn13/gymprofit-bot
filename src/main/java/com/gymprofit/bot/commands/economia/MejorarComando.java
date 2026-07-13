package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.MejoraService;
import com.gymprofit.bot.services.MejoraService.ResultadoMejora;
import com.gymprofit.bot.services.Mejoras;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /mejorar}: compra un nodo del árbol de mejoras (sube un atributo permanentemente). */
public final class MejorarComando implements Comando {

    private static final String NOMBRE = "mejorar";

    private final MejoraService mejoras;

    public MejorarComando(MejoraService mejoras) {
        this.mejoras = mejoras;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData nodo = new OptionData(OptionType.STRING, "nodo",
                Messages.get(Messages.ES, "comando.mejorar.opcion.nodo"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mejorar.opcion.nodo"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.mejorar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.mejorar.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mejorar.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(nodo);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String nodoId = evento.getOption("nodo").getAsString();

        evento.deferReply(true).queue();
        ResultadoMejora r = mejoras.comprar(evento.getUser().getIdLong(), nodoId);
        String nombre = r.nodo().map(m -> MejorasComando.nombreNodo(locale, m)).orElse(nodoId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "mejorar.ok", nombre,
                    Messages.get(locale, "atributo." + r.nodo().get().atributo()),
                    r.nodo().get().valor());
            case NO_EXISTE -> Messages.get(locale, "mejorar.noexiste");
            case YA_TIENES -> Messages.get(locale, "mejorar.yatienes", nombre);
            case BLOQUEADO -> Messages.get(locale, "mejorar.bloqueado", nombre);
            case SIN_SALDO -> Messages.get(locale, "mejorar.sinsaldo",
                    r.nodo().map(Mejoras::precio).orElse(0L));
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
