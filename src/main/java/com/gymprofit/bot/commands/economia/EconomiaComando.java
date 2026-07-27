package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.ComandoAutocompletable;
import com.gymprofit.bot.db.EventoActivo;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.jobs.EventoEconomicoJob;
import com.gymprofit.bot.services.EventoEconomico;
import com.gymprofit.bot.services.EventoEconomicoService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /economia} — cara del clima económico global (F5). Con {@code ver} cualquiera consulta el
 * evento activo (o la ausencia de él); con {@code lanzar} el staff fuerza un evento concreto (o uno
 * aleatorio si no elige ninguno), reutilizando el mismo anuncio de canal que dispara el job. Toda la
 * regla vive en {@link EventoEconomicoService}; aquí solo se traducen sus datos a embeds i18n.
 *
 * <p>Visibilidad: {@code ver} es <b>público</b> (cualquier jugador consulta el clima, respuesta a la
 * vista); {@code lanzar} se restringe a staff con un gate en código ({@link Permission#MANAGE_SERVER}),
 * porque mueve la economía de todo el servidor. El gate va en código (no a nivel de comando) para no
 * ocultar {@code ver} a los jugadores; su error de permisos va <b>efímero</b> (convención del proyecto).
 */
public final class EconomiaComando implements ComandoAutocompletable {

    private static final String NOMBRE = "economia";
    /** Discord admite como mucho 25 sugerencias de autocompletado. */
    private static final int MAX_SUGERENCIAS = 25;

    private final EventoEconomicoService service;

    public EconomiaComando(EventoEconomicoService service) {
        this.service = service;
    }

    @Override
    public SlashCommandData definicion() {
        // La opción del evento a lanzar es opcional (vacío = aleatorio) y autocompletada con el catálogo.
        OptionData evento = new OptionData(OptionType.STRING, "evento",
                Messages.get(Messages.ES, "comando.economia.lanzar.opcion.evento"), false, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.economia.lanzar.opcion.evento"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.economia.desc"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.economia.desc"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.economia.desc"))
                .setContexts(InteractionContextType.GUILD)
                // Comando público: `ver` lo usa cualquiera; `lanzar` se gatea a staff en código (así el
                // comando entero no queda oculto a los jugadores por permisos a nivel de comando).
                .addSubcommands(
                        sub("ver", "comando.economia.ver.desc"),
                        sub("lanzar", "comando.economia.lanzar.desc").addOptions(evento));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName();
        switch (sub) {
            case "ver" -> ver(evento, locale);
            case "lanzar" -> lanzar(evento, locale);
            default -> { /* nada */ }
        }
    }

    /**
     * Muestra el evento activo (nombre, efecto y cuándo termina con timestamp relativo de Discord) o,
     * si no hay ninguno, que la economía va con normalidad. Público (el clima se juega a la vista).
     */
    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        Optional<EventoActivo> activoOpt = service.activo();
        if (activoOpt.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "economia.ver.titulo"),
                    Messages.get(locale, "economia.ver.sin_evento")).build()).queue();
            return;
        }
        EventoActivo activo = activoOpt.get();
        EventoEconomico tipo = activo.tipo();
        String cuerpo = Messages.get(locale, "economia.ver.activo",
                tipo.emoji(),
                Messages.get(locale, tipo.claveI18n() + ".nombre"),
                Messages.get(locale, tipo.claveI18n() + ".efecto"),
                activo.fin().getEpochSecond());
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "economia.ver.titulo"), cuerpo).build()).queue();
    }

    /**
     * Lanza un evento (solo staff): el elegido por la opción o, si viene vacía, uno aleatorio. Además del
     * ack al invocador, publica el anuncio en el canal de economía con el mismo envío que usa el job. El
     * gate de staff va en código ({@code MANAGE_SERVER}) porque el comando es público por su subcomando
     * {@code ver}; el error de permisos va efímero (convención del proyecto).
     */
    private void lanzar(SlashCommandInteractionEvent evento, Locale locale) {
        if (evento.getMember() == null || !evento.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "economia.lanzar.no_autorizado"))).setEphemeral(true).queue();
            return;
        }
        OptionMapping op = evento.getOption("evento");
        EventoEconomico e;
        if (op != null) {
            try {
                // Defensivo: los valores vienen de nuestro propio autocompletado, pero se valida igual.
                e = EventoEconomico.valueOf(op.getAsString());
            } catch (IllegalArgumentException ex) {
                evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
                return;
            }
            service.lanzar(e, Instant.now());
        } else {
            e = service.lanzarAleatorio(Instant.now());
        }
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "economia.ver.titulo"),
                Messages.get(locale, "economia.lanzar.ok",
                        e.emoji(), Messages.get(locale, e.claveI18n() + ".nombre"))).build()).queue();
        // El lanzamiento manual también se anuncia en el canal de economía (mismo envío que el job).
        EventoEconomicoJob.anunciarInicio(evento.getJDA(), e);
    }

    /**
     * Autocompleta el evento a lanzar con el catálogo entero (nombre localizado según el idioma del
     * usuario → valor {@code name()}), filtrado por lo ya tecleado. Responde siempre (aunque vacío) y
     * sin {@code defer}: Discord da 3 s.
     */
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        if (!"evento".equals(evento.getFocusedOption().getName())) {
            evento.replyChoices(List.of()).queue();
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String tecleado = evento.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> opciones = Arrays.stream(EventoEconomico.values())
                .map(t -> new Command.Choice(Messages.get(locale, t.claveI18n() + ".nombre"), t.name()))
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(tecleado))
                .limit(MAX_SUGERENCIAS)
                .toList();
        evento.replyChoices(opciones).queue();
    }
}
