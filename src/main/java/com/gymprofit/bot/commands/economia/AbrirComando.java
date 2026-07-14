package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.CofreService;
import com.gymprofit.bot.services.CofreService.Obtenido;
import com.gymprofit.bot.services.CofreService.Resultado;
import com.gymprofit.bot.services.Cofres;
import com.gymprofit.bot.services.Encantamiento;
import com.gymprofit.bot.services.Items;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /abrir <cofre> [cantidad]}: abre cofres del inventario y reparte su botín al azar. */
public final class AbrirComando implements Comando {

    private static final String NOMBRE = "abrir";

    private final CofreService cofres;

    public AbrirComando(CofreService cofres) {
        this.cofres = cofres;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData cofre = new OptionData(OptionType.STRING, "cofre",
                Messages.get(Messages.ES, "comando.abrir.opcion.cofre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.abrir.opcion.cofre"));
        for (Cofres c : Cofres.CATALOGO) {
            cofre.addChoice(Messages.get(Messages.ES, "item." + c.itemId()), c.itemId());
        }
        OptionData cantidad = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.abrir.opcion.cantidad"), false)
                .setMinValue(1).setMaxValue(10)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.abrir.opcion.cantidad"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.abrir.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.abrir.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.abrir.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(cofre, cantidad);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String cofreId = evento.getOption("cofre").getAsString();
        OptionMapping cant = evento.getOption("cantidad");
        int cantidad = cant != null ? cant.getAsInt() : 1;

        evento.deferReply(false).queue();
        Resultado r = cofres.abrir(evento.getUser().getIdLong(), cofreId, cantidad);
        String nombreCofre = Messages.get(locale, "item." + cofreId);
        String mensaje = switch (r.estado()) {
            case OK -> mensajeExito(locale, nombreCofre, r);
            case NO_ES_COFRE -> Messages.get(locale, "abrir.noescofre");
            case NO_TIENE -> Messages.get(locale, "abrir.notiene", nombreCofre);
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.LOGRO, locale, mensaje)).queue();
    }

    private static String mensajeExito(Locale locale, String nombreCofre, Resultado r) {
        StringBuilder sb = new StringBuilder(
                Messages.get(locale, "abrir.titulo", nombreCofre)).append("\n\n");
        for (Obtenido o : r.premios()) {
            sb.append(describir(locale, o)).append('\n');
        }
        return sb.toString().strip();
    }

    /** Describe un premio ya obtenido, con su rareza. */
    static String describir(Locale locale, Obtenido o) {
        String rareza = o.rareza().emoji() + " ";
        return switch (o.tipo()) {
            case COINS -> rareza + Messages.get(locale,
                    o.fallback() ? "abrir.premio.coins.fallback" : "abrir.premio.coins", o.coins());
            case ITEM -> {
                Items i = Items.porId(o.ref()).orElse(null);
                String emoji = i != null ? i.emoji() : "🎁";
                yield rareza + emoji + " " + Messages.get(locale, "item." + o.ref())
                        + " ×" + o.cantidad();
            }
            case ENCANTO -> rareza + "✨ " + Messages.get(locale, "abrir.premio.encanto",
                    Messages.get(locale, "encanto." + o.ref()));
            case NIVEL -> rareza + Messages.get(locale, "abrir.premio.nivel");
        };
    }
}
