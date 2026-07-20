package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.Comando;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.DescansoService;
import com.gymprofit.bot.services.ItemService;
import com.gymprofit.bot.services.ItemService.ResultadoUso;
import com.gymprofit.bot.services.Items;
import com.gymprofit.bot.services.VentaService;
import com.gymprofit.bot.services.VentaService.Resultado;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Locale;
import java.util.Map;

/**
 * {@code /inventario} con subcomandos (ver, usar, vender). Consolida la gestión de la mochila en un
 * solo comando para no gastar cupo de slash commands (límite de 100 de Discord).
 *
 * <p>{@code ver} lista lo que posees agrupado por categoría; {@code usar} consume un ítem y aplica su
 * efecto (energía o salud); {@code vender} lo cambia por coins (minerales al 100 %, el resto a mitad
 * de precio). Los tres son <b>públicos</b>: la economía se juega a la vista de todos.
 */
public final class InventarioComando implements Comando {

    private static final String NOMBRE = "inventario";

    private final ItemService items;
    private final VentaService venta;

    public InventarioComando(ItemService items, VentaService venta) {
        this.items = items;
        this.venta = venta;
    }

    @Override
    public SlashCommandData definicion() {
        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.inventario.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.inventario.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.inventario.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("ver", "comando.inventario.descripcion"),
                        sub("usar", "comando.usar.descripcion")
                                .addOptions(item("comando.usar.opcion.item")),
                        sub("vender", "comando.vender.descripcion")
                                .addOptions(item("comando.vender.opcion.item"), cantidad()));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData item(String claveDesc) {
        return new OptionData(OptionType.STRING, "item", Messages.get(Messages.ES, claveDesc), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    private static OptionData cantidad() {
        return new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.vender.opcion.cantidad"), false)
                .setMinValue(1)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.vender.opcion.cantidad"));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "ver" : evento.getSubcommandName();
        switch (sub) {
            case "ver" -> ver(evento, locale);
            case "usar" -> usar(evento, locale);
            case "vender" -> vender(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    private void ver(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        Map<String, Integer> inv = items.inventario(evento.getUser().getIdLong());

        String desc;
        if (inv.isEmpty()) {
            desc = Messages.get(locale, "inventario.vacio");
        } else {
            StringBuilder sb = new StringBuilder();
            Items.Categoria catActual = null;
            for (Items item : Items.CATALOGO) {
                Integer n = inv.get(item.id());
                if (n == null || n <= 0) {
                    continue;
                }
                if (item.categoria() != catActual) {
                    catActual = item.categoria();
                    sb.append("\n**").append(Messages.get(locale,
                            "tienda.cat." + catActual.name().toLowerCase())).append("**\n");
                }
                sb.append(Messages.get(locale, "inventario.linea",
                        item.emoji(), Messages.get(locale, "item." + item.id()), n)).append('\n');
            }
            desc = sb.toString();
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "inventario.titulo", evento.getUser().getName()), desc)
                .setThumbnail(evento.getUser().getEffectiveAvatarUrl()).build();
        evento.getHook().sendMessageEmbeds(embed).queue();
    }

    private void usar(SlashCommandInteractionEvent evento, Locale locale) {
        String itemId = evento.getOption("item").getAsString();

        evento.deferReply(false).queue();
        ResultadoUso r = items.usar(evento.getUser().getIdLong(), itemId);
        String nombre = nombreDe(locale, itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "usar.ok", nombre,
                    Messages.get(locale, "usar.efecto." + r.efecto().name().toLowerCase()), r.valor());
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case NO_CONSUMIBLE -> Messages.get(locale, "usar.noconsumible");
            case NO_TIENE -> Messages.get(locale, "usar.notiene", nombre);
            case LLENO -> Messages.get(locale, "usar.lleno", DescansoService.MAX_CONSUMOS_DIA);
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    private void vender(SlashCommandInteractionEvent evento, Locale locale) {
        String itemId = evento.getOption("item").getAsString();
        OptionMapping cant = evento.getOption("cantidad");
        int cantidad = cant != null ? cant.getAsInt() : 1;

        evento.deferReply(false).queue();
        Resultado r = venta.vender(evento.getUser().getIdLong(), itemId, cantidad);
        String nombre = nombreDe(locale, itemId);
        String mensaje = switch (r.estado()) {
            case OK -> Messages.get(locale, "vender.ok", cantidad, nombre, r.total());
            case NO_EXISTE -> Messages.get(locale, "comprar.noexiste");
            case NO_TIENE -> Messages.get(locale, "vender.notiene", nombre);
            case CANTIDAD_INVALIDA -> Messages.get(locale, "vender.cantidad");
            case NO_VENDIBLE -> Messages.get(locale, "vender.novendible", nombre);
        };
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    /** Nombre localizado del ítem; si el id no existe en el catálogo, el id crudo. */
    private static String nombreDe(Locale locale, String itemId) {
        return Items.porId(itemId).map(i -> Messages.get(locale, "item." + i.id())).orElse(itemId);
    }
}
