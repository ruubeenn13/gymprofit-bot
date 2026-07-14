package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.GremioService;
import com.gymprofit.bot.services.GremioService.ResultadoCrear;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Locale;

/** {@code /crear-gremio <nombre>}: funda un gremio (cobra coste) y le crea un canal privado. */
public final class CrearGremioComando implements Comando {

    private static final String NOMBRE = "crear-gremio";

    private final GremioService gremios;

    public CrearGremioComando(GremioService gremios) {
        this.gremios = gremios;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData nombre = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.creargremio.opcion.nombre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.creargremio.opcion.nombre"));
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.creargremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.creargremio.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.creargremio.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .addOptions(nombre);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String nombre = evento.getOption("nombre").getAsString().trim();
        long dueno = evento.getUser().getIdLong();

        evento.deferReply(false).queue();
        ResultadoCrear r = gremios.crear(dueno, nombre);
        if (r.estado() == GremioService.EstadoCrear.OK && evento.getGuild() != null) {
            GremioCanal.crear(evento.getGuild(), nombre, dueno,
                    canalId -> gremios.fijarCanal(r.gremioId(), canalId));
        }
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "gremio.creado", nombre, GremioService.COSTE_CREAR);
            case YA_EN_GREMIO -> Messages.get(locale, "gremio.yaengremio");
            case NOMBRE_INVALIDO -> Messages.get(locale, "gremio.nombreinvalido",
                    GremioService.NOMBRE_MIN, GremioService.NOMBRE_MAX);
            case NOMBRE_USADO -> Messages.get(locale, "gremio.nombreusado");
            case SIN_SALDO -> Messages.get(locale, "gremio.sinsaldo", GremioService.COSTE_CREAR);
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }
}
