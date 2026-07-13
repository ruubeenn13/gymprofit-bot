package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.MejoraService;
import com.gymprofit.bot.services.Mejoras;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /mejoras}: dibuja el <b>árbol de mejoras</b> con arte ASCII/emoji: cada rama (fuerza,
 * resistencia, carisma) con sus nodos marcados como ✅ comprado, 🔓 disponible o 🔒 bloqueado, con su
 * precio. Así se ve de un vistazo por dónde tirar. Se compra con {@code /mejorar <nodo>}.
 */
public final class MejorasComando implements Comando {

    private static final String NOMBRE = "mejoras";
    private static final String[] ROMANOS = {"I", "II", "III", "IV", "V"};
    private static final Map<String, String> EMOJI_RAMA =
            Map.of("fuerza", "💪", "resistencia", "🏃", "carisma", "🗣️");

    private final MejoraService mejoras;

    public MejorasComando(MejoraService mejoras) {
        this.mejoras = mejoras;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.mejoras.descripcion"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.mejoras.descripcion"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.mejoras.descripcion"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        evento.deferReply(true).queue();
        Set<String> comprados = mejoras.comprados(evento.getUser().getIdLong());

        StringBuilder sb = new StringBuilder(Messages.get(locale, "mejoras.leyenda")).append("\n\n");
        for (String rama : Mejoras.RAMAS) {
            sb.append(EMOJI_RAMA.get(rama)).append(" **")
                    .append(Messages.get(locale, "atributo." + rama)).append("**\n```\n");
            List<Mejoras> nodos = Mejoras.deRama(rama);
            for (int i = 0; i < nodos.size(); i++) {
                Mejoras m = nodos.get(i);
                String rama_ = (i == nodos.size() - 1) ? "└─" : "├─";
                String estado = comprados.contains(m.id()) ? "✅"
                        : (m.prereq() == null || comprados.contains(m.prereq())) ? "🔓" : "🔒";
                sb.append(rama_).append(' ').append(estado).append(' ')
                        .append(nombreNodo(locale, m)).append(" (+").append(m.valor()).append(')');
                if (!comprados.contains(m.id())) {
                    sb.append("  ·  ").append(m.precio()).append("🪙  ·  ").append(m.id());
                }
                sb.append('\n');
            }
            sb.append("```\n");
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "mejoras.titulo"), sb.toString()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    /** Nombre legible del nodo: «Fuerza II». */
    static String nombreNodo(Locale locale, Mejoras m) {
        return Messages.get(locale, "atributo." + m.atributo()) + " "
                + ROMANOS[Math.min(m.nivel() - 1, ROMANOS.length - 1)];
    }
}
