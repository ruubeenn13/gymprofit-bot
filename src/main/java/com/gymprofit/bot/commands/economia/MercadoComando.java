package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.ListadoMercado;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.MercadoService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/** {@code /mercado}: lista los anuncios activos del mercado entre jugadores. */
public final class MercadoComando implements Comando {

    private static final String NOMBRE = "mercado";

    private final MercadoService mercado;

    public MercadoComando(MercadoService mercado) {
        this.mercado = mercado;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.mercado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.mercado.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mercado.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        List<ListadoMercado> anuncios = mercado.listar();

        String cuerpo;
        if (anuncios.isEmpty()) {
            cuerpo = Messages.get(locale, "mercado.vacio");
        } else {
            StringBuilder sb = new StringBuilder(Messages.get(locale, "mercado.intro")).append("\n\n");
            for (ListadoMercado l : anuncios) {
                String emoji = Items.porId(l.itemId()).map(Items::emoji).orElse("📦");
                sb.append(Messages.get(locale, "mercado.linea", l.id(), emoji,
                        Messages.get(locale, "item." + l.itemId()), l.cantidad(), l.precio(),
                        "<@" + l.vendedor() + ">")).append('\n');
            }
            cuerpo = sb.toString();
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "mercado.titulo"), cuerpo).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }
}
