package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.TruequeRegistro;
import com.gymprofit.bot.services.TruequeService.Oferta;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /trueque}: propone un intercambio de ítems/coins a otro jugador (lo confirma con botones). */
public final class TruequeComando implements Comando {

    private static final String NOMBRE = "trueque";

    private final TruequeRegistro registro;

    public TruequeComando(TruequeRegistro registro) {
        this.registro = registro;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.trueque.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trueque.opcion.usuario"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.trueque.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.trueque.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.trueque.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(usuario,
                        opcionItem("doy_item", "comando.trueque.opcion.doyitem"),
                        opcionEntero("doy_cantidad", "comando.trueque.opcion.doycant"),
                        opcionEntero("doy_coins", "comando.trueque.opcion.doycoins"),
                        opcionItem("pido_item", "comando.trueque.opcion.pidoitem"),
                        opcionEntero("pido_cantidad", "comando.trueque.opcion.pidocant"),
                        opcionEntero("pido_coins", "comando.trueque.opcion.pidocoins"));
    }

    private static OptionData opcionItem(String nombre, String claveDesc) {
        return new OptionData(OptionType.STRING, nombre, Messages.get(Messages.ES, claveDesc), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData opcionEntero(String nombre, String claveDesc) {
        return new OptionData(OptionType.INTEGER, nombre, Messages.get(Messages.ES, claveDesc), false)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        User objetivo = evento.getOption("usuario").getAsUser();
        long proponente = evento.getUser().getIdLong();

        if (objetivo.isBot() || objetivo.getIdLong() == proponente) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "trueque.destino"))).setEphemeral(true).queue();
            return;
        }

        Oferta oferta = new Oferta(proponente, objetivo.getIdLong(),
                item("doy_item", evento), cant("doy_cantidad", evento), coins("doy_coins", evento),
                item("pido_item", evento), cant("pido_cantidad", evento), coins("pido_coins", evento));
        if (!oferta.ofreceAlgo() || !oferta.pideAlgo()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "trueque.vacio"))).setEphemeral(true).queue();
            return;
        }
        // Validación de que los ítems existen (la posesión se comprueba al aceptar).
        if ((oferta.doyItem() != null && Items.porId(oferta.doyItem()).isEmpty())
                || (oferta.pidoItem() != null && Items.porId(oferta.pidoItem()).isEmpty())) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comprar.noexiste"))).setEphemeral(true).queue();
            return;
        }

        long id = registro.registrar(oferta);
        String desc = Messages.get(locale, "trueque.oferta",
                "<@" + proponente + ">", "<@" + objetivo.getIdLong() + ">",
                lado(locale, oferta.doyItem(), oferta.doyCant(), oferta.doyCoins()),
                lado(locale, oferta.pidoItem(), oferta.pidoCant(), oferta.pidoCoins()));
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "trueque.titulo"), desc).build();
        evento.replyEmbeds(embed).setComponents(ActionRow.of(
                        Button.success("trueque:acc:" + objetivo.getIdLong() + ":" + id,
                                Messages.get(locale, "trueque.aceptar")),
                        Button.danger("trueque:rej:" + objetivo.getIdLong() + ":" + id,
                                Messages.get(locale, "trueque.rechazar"))))
                .queue();
    }

    private static String item(String opt, SlashCommandInteractionEvent e) {
        OptionMapping o = e.getOption(opt);
        return o != null ? o.getAsString() : null;
    }

    private static int cant(String opt, SlashCommandInteractionEvent e) {
        OptionMapping o = e.getOption(opt);
        return o != null ? o.getAsInt() : 1;
    }

    private static long coins(String opt, SlashCommandInteractionEvent e) {
        OptionMapping o = e.getOption(opt);
        return o != null ? o.getAsLong() : 0;
    }

    /** Describe un lado del trueque (coins + ítem), o «nada». */
    static String lado(Locale locale, String itemId, int cant, long coins) {
        StringBuilder sb = new StringBuilder();
        if (coins > 0) {
            sb.append(coins).append(" 🪙");
        }
        if (itemId != null) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            String emoji = Items.porId(itemId).map(Items::emoji).orElse("📦");
            sb.append(emoji).append(' ').append(Messages.get(locale, "item." + itemId))
                    .append(" ×").append(cant);
        }
        return sb.length() > 0 ? sb.toString() : Messages.get(locale, "trueque.nada");
    }
}
