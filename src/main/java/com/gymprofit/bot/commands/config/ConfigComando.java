package com.gymprofit.bot.commands.config;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.db.ConfigServidor;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.ConfigServidorService;
import com.gymprofit.bot.services.ConfigServidorService.Objetivo;
import com.gymprofit.bot.services.ConfigServidorService.TipoCanal;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Locale;

/**
 * {@code /config}: configuración del servidor (solo staff con permiso «Gestionar servidor»).
 * Subcomandos: {@code ver} (muestra la config), {@code canal} (fija un canal), {@code rol} (fija
 * un rol de objetivo) e {@code idioma}. Es la base de módulos como bienvenida/auto-roles.
 */
public final class ConfigComando implements Comando {

    private static final String NOMBRE = "config";

    private final ConfigServidorService servicio;

    public ConfigComando(ConfigServidorService servicio) {
        this.servicio = servicio;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData tipoCanal = new OptionData(OptionType.STRING, "tipo",
                Messages.get(Messages.ES, "comando.config.sub.canal.opcion.tipo"), true)
                .addChoice("Bienvenida", TipoCanal.BIENVENIDA.name())
                .addChoice("Ejercicio del día", TipoCanal.EJERCICIO_DIA.name())
                .addChoice("Logros", TipoCanal.LOGROS.name())
                .addChoice("Sugerencias", TipoCanal.SUGERENCIAS.name())
                .addChoice("Soporte", TipoCanal.SOPORTE.name())
                .addChoice("Bot-logs", TipoCanal.BOT_LOGS.name());
        OptionData canal = new OptionData(OptionType.CHANNEL, "canal",
                Messages.get(Messages.ES, "comando.config.sub.canal.opcion.canal"), true)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS);

        OptionData objetivo = new OptionData(OptionType.STRING, "objetivo",
                Messages.get(Messages.ES, "comando.config.sub.rol.opcion.objetivo"), true)
                .addChoice("Fuerza", Objetivo.FUERZA.name())
                .addChoice("Cardio", Objetivo.CARDIO.name())
                .addChoice("Pérdida de peso", Objetivo.PERDIDA_PESO.name())
                .addChoice("General", Objetivo.GENERAL.name());
        OptionData rol = new OptionData(OptionType.ROLE, "rol",
                Messages.get(Messages.ES, "comando.config.sub.rol.opcion.rol"), true);

        OptionData idioma = new OptionData(OptionType.STRING, "valor",
                Messages.get(Messages.ES, "comando.config.sub.idioma.opcion.valor"), true)
                .addChoice("Español", "es")
                .addChoice("English", "en");

        SubcommandData ver = new SubcommandData("ver",
                Messages.get(Messages.ES, "comando.config.sub.ver.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.config.sub.ver.descripcion"));
        SubcommandData subCanal = new SubcommandData("canal",
                Messages.get(Messages.ES, "comando.config.sub.canal.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.config.sub.canal.descripcion"))
                .addOptions(tipoCanal, canal);
        SubcommandData subRol = new SubcommandData("rol",
                Messages.get(Messages.ES, "comando.config.sub.rol.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.config.sub.rol.descripcion"))
                .addOptions(objetivo, rol);
        SubcommandData subIdioma = new SubcommandData("idioma",
                Messages.get(Messages.ES, "comando.config.sub.idioma.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.config.sub.idioma.descripcion"))
                .addOptions(idioma);

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.config.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.config.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.config.descripcion"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(ver, subCanal, subRol, subIdioma);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        long guildId = evento.getGuild().getIdLong();
        String sub = evento.getSubcommandName();
        if (sub == null) {
            return;
        }

        switch (sub) {
            case "ver" -> responderVer(evento, locale, guildId);
            case "canal" -> {
                TipoCanal tipo = TipoCanal.valueOf(evento.getOption("tipo").getAsString());
                long canalId = evento.getOption("canal").getAsChannel().getIdLong();
                servicio.fijarCanal(guildId, tipo, canalId);
                responderOk(evento, locale, Messages.get(locale, "config.ok.canal",
                        Messages.get(locale, etiquetaCanal(tipo)), "<#" + canalId + ">"));
            }
            case "rol" -> {
                Objetivo objetivo = Objetivo.valueOf(evento.getOption("objetivo").getAsString());
                long rolId = evento.getOption("rol").getAsRole().getIdLong();
                servicio.fijarRol(guildId, objetivo, rolId);
                responderOk(evento, locale, Messages.get(locale, "config.ok.rol",
                        Messages.get(locale, etiquetaObjetivo(objetivo)), "<@&" + rolId + ">"));
            }
            case "idioma" -> {
                String valor = evento.getOption("valor").getAsString();
                servicio.fijarIdioma(guildId, valor);
                responderOk(evento, locale, Messages.get(locale, "config.ok.idioma", valor));
            }
            default -> { /* subcomando desconocido: ignorar */ }
        }
    }

    private void responderOk(SlashCommandInteractionEvent evento, Locale locale, String mensaje) {
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "config.ok.titulo"), mensaje).build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private void responderVer(SlashCommandInteractionEvent evento, Locale locale, long guildId) {
        ConfigServidor c = servicio.obtener(guildId);

        StringBuilder d = new StringBuilder();
        d.append("🌐 **").append(Messages.get(locale, "config.ver.idioma"))
                .append(":** `").append(c.idioma()).append("`\n\n");

        d.append("__**").append(Messages.get(locale, "config.ver.seccion.canales")).append("**__\n");
        d.append(linea(locale, "👋", "config.campo.bienvenida", c.canalBienvenida(), true));
        d.append(linea(locale, "🏋️", "config.campo.ejercicio", c.canalEjercicioDia(), true));
        d.append(linea(locale, "🏆", "config.campo.logros", c.canalLogros(), true));
        d.append(linea(locale, "💡", "config.campo.sugerencias", c.canalSugerencias(), true));
        d.append(linea(locale, "🎫", "config.campo.soporte", c.canalSoporte(), true));
        d.append(linea(locale, "🛡️", "config.campo.botlogs", c.canalBotLogs(), true));

        d.append("\n__**").append(Messages.get(locale, "config.ver.seccion.roles")).append("**__\n");
        d.append(linea(locale, "💪", "config.campo.rol.fuerza", c.rolObjetivoFuerza(), false));
        d.append(linea(locale, "🏃", "config.campo.rol.cardio", c.rolObjetivoCardio(), false));
        d.append(linea(locale, "⚖️", "config.campo.rol.perdidapeso", c.rolObjetivoPerdidaPeso(), false));
        d.append(linea(locale, "🌟", "config.campo.rol.general", c.rolObjetivoGeneral(), false));

        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                        Messages.get(locale, "config.ver.titulo"), d.toString().strip())
                .setThumbnail(EmbedFactory.iconoUrl())
                .build();
        evento.replyEmbeds(embed).setEphemeral(true).queue();
    }

    /** Una línea de la vista de config: {@code emoji **Etiqueta** — ✅ mención} o {@code — ⚪ sin configurar}. */
    private static String linea(Locale locale, String emoji, String etiquetaKey, Long id, boolean esCanal) {
        String estado;
        if (id != null) {
            estado = "✅ " + (esCanal ? "<#" + id + ">" : "<@&" + id + ">");
        } else {
            estado = "⚪ " + Messages.get(locale, "config.sinconfigurar");
        }
        // Doble salto de línea: deja aire entre ítems (si no, salen muy pegados en Discord).
        return emoji + " **" + Messages.get(locale, etiquetaKey) + "** — " + estado + "\n\n";
    }

    private static String etiquetaCanal(TipoCanal tipo) {
        return switch (tipo) {
            case BIENVENIDA -> "config.campo.bienvenida";
            case EJERCICIO_DIA -> "config.campo.ejercicio";
            case LOGROS -> "config.campo.logros";
            case SUGERENCIAS -> "config.campo.sugerencias";
            case SOPORTE -> "config.campo.soporte";
            case BOT_LOGS -> "config.campo.botlogs";
        };
    }

    private static String etiquetaObjetivo(Objetivo objetivo) {
        return switch (objetivo) {
            case FUERZA -> "config.campo.rol.fuerza";
            case CARDIO -> "config.campo.rol.cardio";
            case PERDIDA_PESO -> "config.campo.rol.perdidapeso";
            case GENERAL -> "config.campo.rol.general";
        };
    }
}
