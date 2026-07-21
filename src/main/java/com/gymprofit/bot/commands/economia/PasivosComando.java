package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.ComandoAutocompletable;
import com.gymprofit.bot.db.UsuarioDiscord;
import com.gymprofit.bot.db.UsuarioDiscordRepositorio;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.PasivoService;
import com.gymprofit.bot.services.PasivoService.EstadoRanura;
import com.gymprofit.bot.services.PasivoService.ResultadoEquipar;
import com.gymprofit.bot.services.PasivoService.ResultadoQuitar;
import net.dv8tion.jda.api.entities.User;
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

import java.util.List;
import java.util.Locale;

/**
 * {@code /pasivos} con subcomandos (ver, equipar, quitar). Los efectos pasivos del equipo y los
 * vehículos se activan poniéndolos en <b>ranuras</b>: 1 al empezar y la 2.ª, 3.ª y 4.ª a los niveles
 * 10, 25 y 50 (los mismos umbrales que los rangos). El ítem <b>no se consume</b>: se puede quitar y
 * volver a poner sin coste.
 *
 * <p>Las tres respuestas son <b>públicas</b> (las acciones de economía se juegan a la vista de todos,
 * y enseñar el yate es media gracia de comprarlo); solo los <b>errores</b> de validación salen en
 * efímero.
 *
 * <p>El bono se recalcula siempre contra el inventario en {@link PasivoService}: si el jugador vende,
 * regala o publica el ítem, la ranura se queda pero deja de contar, y {@code ver} lo marca con ⚠️.
 */
public final class PasivosComando implements ComandoAutocompletable {

    private static final String NOMBRE = "pasivos";
    /** Discord admite como mucho 25 sugerencias de autocompletado. */
    private static final int MAX_SUGERENCIAS = 25;
    /** Discord corta el nombre de una sugerencia a 100 caracteres. */
    private static final int MAX_LARGO_SUGERENCIA = 100;

    private final PasivoService pasivos;
    private final UsuarioDiscordRepositorio usuarios;

    public PasivosComando(PasivoService pasivos, UsuarioDiscordRepositorio usuarios) {
        this.pasivos = pasivos;
        this.usuarios = usuarios;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData usuario = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.pasivos.ver.opcion.usuario"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pasivos.ver.opcion.usuario"));

        // Autocompletado en vez de choices: son 30 ítems y Discord solo admite 25 choices; además
        // así se filtra por lo que el jugador tiene de verdad.
        OptionData item = new OptionData(OptionType.STRING, "item",
                Messages.get(Messages.ES, "comando.pasivos.equipar.opcion.item"), true, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pasivos.equipar.opcion.item"));

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.pasivos.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.pasivos.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.pasivos.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.pasivos.ver.descripcion").addOptions(usuario),
                        sub("equipar", "comando.pasivos.equipar.descripcion")
                                .addOptions(item, ranura("comando.pasivos.equipar.opcion.ranura", false)),
                        sub("quitar", "comando.pasivos.quitar.descripcion")
                                .addOptions(ranura("comando.pasivos.quitar.opcion.ranura", true)));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData ranura(String claveDesc, boolean obligatoria) {
        return new OptionData(OptionType.INTEGER, "ranura",
                Messages.get(Messages.ES, claveDesc), obligatoria)
                .setMinValue(1)
                .setMaxValue(PasivoService.RANURAS_MAX)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName();
        switch (sub) {
            case "ver" -> ver(evento, locale);
            case "equipar" -> equipar(evento, locale);
            case "quitar" -> quitar(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /**
     * Sugiere los ítems que el jugador <b>posee</b>, <b>tienen pasivo</b> y <b>no están ya
     * equipados</b>, filtrados por lo que va escribiendo. Cada sugerencia enseña el nombre del ítem
     * para que el sistema se explique solo sin abrir documentación.
     */
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        if (!"item".equals(evento.getFocusedOption().getName())) {
            evento.replyChoices(List.of()).queue();
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String escrito = evento.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> opciones = pasivos.equipablesDe(evento.getUser().getIdLong()).stream()
                .map(id -> new Command.Choice(etiqueta(locale, id), id))
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(escrito))
                .limit(MAX_SUGERENCIAS)
                .toList();
        evento.replyChoices(opciones).queue();
    }

    /** Etiqueta de una sugerencia: «🛩️ Jet privado», recortada al máximo que admite Discord. */
    private static String etiqueta(Locale locale, String itemId) {
        String texto = PasivosTexto.emoji(itemId) + " " + PasivosTexto.nombre(locale, itemId);
        return texto.length() > MAX_LARGO_SUGERENCIA
                ? texto.substring(0, MAX_LARGO_SUGERENCIA) : texto;
    }

    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        OptionMapping opcionUsuario = evento.getOption("usuario");
        User objetivo = opcionUsuario != null ? opcionUsuario.getAsUser() : evento.getUser();

        evento.deferReply(false).queue();
        long id = objetivo.getIdLong();
        List<EstadoRanura> ranuras = pasivos.ranuras(id);
        // Una sola pasada por bonosDe: bonoDe(...) lanzaría tres consultas por cada tipo consultado.
        String bonos = PasivosTexto.bonos(locale, pasivos.bonosDe(id));
        int nivel = usuarios.buscar(id).map(UsuarioDiscord::nivel).orElse(0);

        String desc = Messages.get(locale, "pasivos.ver.ranuras") + "\n"
                + PasivosTexto.ranuras(locale, ranuras) + "\n"
                + Messages.get(locale, "pasivos.ver.bonos") + "\n"
                + (bonos.isEmpty() ? Messages.get(locale, "pasivos.ver.sinbonos") : bonos)
                // El pie del siguiente desbloqueo va en la descripción, no en setFooter: el footer
                // de marca lo pone EmbedFactory y no se pisa (SPEC §7).
                + "\n\n_" + PasivosTexto.pie(locale, nivel) + "_";

        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "pasivos.ver.titulo", objetivo.getName()), desc)
                .setThumbnail(objetivo.getEffectiveAvatarUrl())
                .build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void equipar(SlashCommandInteractionEvent evento, Locale locale) {
        String itemId = evento.getOption("item").getAsString();
        OptionMapping opcionRanura = evento.getOption("ranura");
        Integer ranura = opcionRanura == null ? null : opcionRanura.getAsInt();
        long id = evento.getUser().getIdLong();

        // Se difiere antes de tocar la BD (convención del repo): equipar lee inventario, ranuras y
        // vuelve a sumar los bonos, y los 3 s de Discord no se pueden gastar esperando a MySQL.
        evento.deferReply(false).queue();
        ResultadoEquipar r = pasivos.equipar(id, itemId, ranura);
        if (r.estado() != PasivoService.Estado.OK) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    errorEquipar(locale, r, id))).setEphemeral(true).queue();
            return;
        }

        String cuerpo = Messages.get(locale, "pasivos.equipar.ok",
                PasivosTexto.emoji(itemId), PasivosTexto.nombre(locale, itemId), r.ranura(),
                PasivosTexto.descripcion(locale, itemId),
                PasivosTexto.bonos(locale, r.totales()));
        if (r.reemplazado() != null) {
            cuerpo += Messages.get(locale, "pasivos.equipar.reemplazo",
                    PasivosTexto.emoji(r.reemplazado()),
                    PasivosTexto.nombre(locale, r.reemplazado()));
        }
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "pasivos.equipar.titulo", r.ranura()), cuerpo).build()).queue();
    }

    private void quitar(SlashCommandInteractionEvent evento, Locale locale) {
        int ranura = evento.getOption("ranura").getAsInt();
        evento.deferReply(false).queue();
        ResultadoQuitar r = pasivos.quitar(evento.getUser().getIdLong(), ranura);
        if (r.estado() != PasivoService.Estado.OK) {
            String mensaje = r.estado() == PasivoService.Estado.RANURA_INVALIDA
                    ? Messages.get(locale, "pasivos.error.ranurainvalida")
                    : Messages.get(locale, "pasivos.error.vacia", ranura);
            evento.getHook().sendMessageEmbeds(
                            EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                    .setEphemeral(true).queue();
            return;
        }
        String cuerpo = Messages.get(locale, "pasivos.quitar.ok", ranura,
                PasivosTexto.emoji(r.itemId()), PasivosTexto.nombre(locale, r.itemId()),
                PasivosTexto.bonos(locale, r.totales()));
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "pasivos.quitar.titulo", ranura), cuerpo).build()).queue();
    }

    /** Traduce el estado de error a su mensaje. Uno por fila de la tabla de errores del diseño. */
    private String errorEquipar(Locale locale, ResultadoEquipar r, long discordId) {
        String itemId = r.itemId();
        return switch (r.estado()) {
            case NO_EXISTE -> Messages.get(locale, "pasivos.error.noexiste");
            case SIN_PASIVO -> Messages.get(locale, "pasivos.error.sinpasivo",
                    PasivosTexto.emoji(itemId), PasivosTexto.nombre(locale, itemId));
            case NO_TIENE -> Messages.get(locale, "pasivos.error.notiene",
                    PasivosTexto.emoji(itemId), PasivosTexto.nombre(locale, itemId));
            case RANURA_INVALIDA -> Messages.get(locale, "pasivos.error.ranurainvalida");
            case RANURA_BLOQUEADA -> Messages.get(locale, "pasivos.error.ranurabloqueada",
                    r.ranura(), r.nivelRequerido(),
                    usuarios.buscar(discordId).map(UsuarioDiscord::nivel).orElse(0));
            case YA_EQUIPADO -> Messages.get(locale, "pasivos.error.yaequipado", r.ranura());
            // Sin hueco NO se elige por el jugador: se listan las ranuras y se le pide que decida.
            // Pisar en silencio un cohete de 3 000 000 de coins es inaceptable aunque sea reversible.
            case SIN_HUECO -> Messages.get(locale, "pasivos.error.sinhueco",
                    PasivosTexto.ranuras(locale, pasivos.ranuras(discordId)));
            default -> Messages.get(locale, "comando.error.generico");
        };
    }
}
