package com.gymprofit.bot.commands.economia;

import com.gymprofit.bot.commands.ComandoAutocompletable;
import com.gymprofit.bot.db.Empresa;
import com.gymprofit.bot.db.EmpresaPropuestaRepositorio;
import com.gymprofit.bot.db.EmpresaRepositorio;
import com.gymprofit.bot.db.MiembroEmpresa;
import com.gymprofit.bot.db.Pendiente;
import com.gymprofit.bot.db.Propuesta;
import com.gymprofit.bot.embeds.EmbedFactory;
import com.gymprofit.bot.i18n.Messages;
import com.gymprofit.bot.services.Ascensos;
import com.gymprofit.bot.services.EmpresaGestionService;
import com.gymprofit.bot.services.EmpresaGestionService.ResultadoAscensoPatrocinado;
import com.gymprofit.bot.services.EmpresaGestionService.ResultadoGestion;
import com.gymprofit.bot.services.EmpresaService;
import com.gymprofit.bot.services.EmpresaVentaService;
import com.gymprofit.bot.services.Impuesto;
import com.gymprofit.bot.services.Produccion;
import com.gymprofit.bot.services.EmpresaService.InfoEmpresa;
import com.gymprofit.bot.services.EmpresaService.ResultadoFundar;
import com.gymprofit.bot.services.EmpresaService.ResultadoIngreso;
import com.gymprofit.bot.services.EmpresaService.ResultadoMejora;
import com.gymprofit.bot.services.EmpresaService.SalidaFundar;
import com.gymprofit.bot.services.EmpresaService.SalidaMejora;
import com.gymprofit.bot.services.RangoEmpresa;
import com.gymprofit.bot.services.TipoPendiente;
import com.gymprofit.bot.services.TipoPropuesta;
import com.gymprofit.bot.services.TrabajoService;
import net.dv8tion.jda.api.entities.Guild;
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
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code /empresa} con subcomandos (fundar, info, disolver, invitar, solicitar, pendientes). Es la
 * cara de las empresas de la Fase 1: fundar la entidad ligada a tu rama, verla, disolverla e ingresar
 * por las dos vías con consentimiento (te invitan o solicitas con motivo). Toda la lógica vive en
 * {@link EmpresaService}; aquí solo se traducen los enums de resultado a embeds i18n.
 *
 * <p>Visibilidad (SPEC §6, tono casual de economía): fundar y las aceptaciones son <b>públicas</b>
 * (se celebran a la vista del canal); los errores, confirmaciones y la gestión personal
 * (invitar/solicitar/pendientes/disolver) van <b>efímeros</b>, para no exponer las decisiones de cada
 * uno. La invitación se <b>publica en el canal</b> con mención + botones: así el invitado la ve aunque
 * tenga los MDs cerrados, y el {@link com.gymprofit.bot.events.EmpresaBotonesListener listener} valida
 * quién puede pulsar vía el service.
 *
 * <p>El comando recibe también el {@link EmpresaRepositorio} porque los listados de {@code pendientes}
 * y la resolución del id de una invitación recién creada son consultas puras (no reglas de negocio):
 * el service expone las operaciones, el repo las lecturas de apoyo.
 */
public final class EmpresaComando implements ComandoAutocompletable {

    private static final String NOMBRE = "empresa";
    /** Discord admite como mucho 5 filas de botones por mensaje: tope de pendientes que se pintan. */
    private static final int MAX_PENDIENTES = 5;
    /** Discord admite como mucho 25 sugerencias de autocompletado. */
    private static final int MAX_SUGERENCIAS = 25;
    /** Discord corta el nombre de una sugerencia a 100 caracteres. */
    private static final int MAX_LARGO_SUGERENCIA = 100;
    /** Número de empresas que se listan en el ranking de prestigio (F4). */
    private static final int TOP = 10;
    /** Prefijo del customId de resolver una pendiente: {@code empresa:resolver:<pendienteId>:<1|0>}. */
    public static final String BOTON_RESOLVER = "empresa:resolver";
    /** Prefijos del customId de la confirmación de disolver: {@code empresa:disolver:<accion>:<ownerId>}. */
    public static final String BOTON_DISOLVER_SI = "empresa:disolver:si";
    public static final String BOTON_DISOLVER_NO = "empresa:disolver:no";
    /** Prefijo del customId de votar una propuesta (F2): {@code empresa:voto:<propuestaId>:<1|0>}. */
    public static final String BOTON_VOTO = "empresa:voto";

    private final EmpresaService empresa;
    private final EmpresaRepositorio repo;
    private final EmpresaGestionService gestion;
    private final EmpresaPropuestaRepositorio propuestasRepo;
    /** Piezas de carrera para el ascenso patrocinado: opciones de ascenso (autocompletado) y coste. */
    private final TrabajoService trabajos;
    /** Venta de la mercancia del almacen (F5a): neto al bote, impuesto quemado, gate atomico. */
    private final EmpresaVentaService venta;

    public EmpresaComando(EmpresaService empresa, EmpresaRepositorio repo,
                          EmpresaGestionService gestion, EmpresaPropuestaRepositorio propuestasRepo,
                          TrabajoService trabajos, EmpresaVentaService venta) {
        this.empresa = empresa;
        this.repo = repo;
        this.gestion = gestion;
        this.propuestasRepo = propuestasRepo;
        this.trabajos = trabajos;
        this.venta = venta;
    }

    @Override
    public SlashCommandData definicion() {
        OptionData nombreFundar = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.empresa.fundar.opcion.nombre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.fundar.opcion.nombre"));
        OptionData nombreInfo = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.empresa.info.opcion.nombre"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.info.opcion.nombre"));
        OptionData usuarioInvitar = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.empresa.invitar.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.invitar.opcion.usuario"));
        OptionData nombreSolicitar = new OptionData(OptionType.STRING, "nombre",
                Messages.get(Messages.ES, "comando.empresa.solicitar.opcion.nombre"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.solicitar.opcion.nombre"));
        OptionData motivoSolicitar = new OptionData(OptionType.STRING, "motivo",
                Messages.get(Messages.ES, "comando.empresa.solicitar.opcion.motivo"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.solicitar.opcion.motivo"));
        // F2 — gestión de plantilla. El objetivo se elige por mención; en `rango` el destino es una de
        // las cuatro escalas por debajo de DUENO (nunca se nombra dueño por comando).
        OptionData usuarioRango = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.empresa.rango.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.rango.opcion.usuario"));
        OptionData rangoDestino = new OptionData(OptionType.STRING, "rango",
                Messages.get(Messages.ES, "comando.empresa.rango.opcion.rango"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.rango.opcion.rango"))
                .addChoice(Messages.get(Messages.ES, "rango.becario"), RangoEmpresa.BECARIO.name())
                .addChoice(Messages.get(Messages.ES, "rango.empleado"), RangoEmpresa.EMPLEADO.name())
                .addChoice(Messages.get(Messages.ES, "rango.encargado"), RangoEmpresa.ENCARGADO.name())
                .addChoice(Messages.get(Messages.ES, "rango.directivo"), RangoEmpresa.DIRECTIVO.name());
        OptionData usuarioSacar = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.empresa.sacar.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.sacar.opcion.usuario"));
        OptionData usuarioDespedir = new OptionData(OptionType.USER, "usuario",
                Messages.get(Messages.ES, "comando.empresa.despedir.opcion.usuario"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.despedir.opcion.usuario"));
        // F3 — ascenso patrocinado. El objetivo se elige por mención; `puesto` se autocompleta con los
        // puestos a los que ESE miembro puede ascender (el service revalida por si acaso).
        OptionData miembroAscender = new OptionData(OptionType.USER, "miembro",
                Messages.get(Messages.ES, "comando.empresa.ascender.opcion.miembro"), true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.ascender.opcion.miembro"));
        OptionData puestoAscender = new OptionData(OptionType.STRING, "puesto",
                Messages.get(Messages.ES, "comando.empresa.ascender.opcion.puesto"), true, true)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.ascender.opcion.puesto"));
        // F5a — venta de mercancia. Cantidad opcional (>= 1): vacio = vender todo el almacen.
        OptionData cantidadVender = new OptionData(OptionType.INTEGER, "cantidad",
                Messages.get(Messages.ES, "comando.empresa.vender.cantidad"), false)
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.vender.cantidad"))
                .setMinValue(1);

        return Commands.slash(NOMBRE, Messages.get(Messages.ES, "comando.empresa.familia"))
                .setDescriptionLocalization(DiscordLocale.SPANISH,
                        Messages.get(Messages.ES, "comando.empresa.familia"))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US,
                        Messages.get(Messages.EN, "comando.empresa.familia"))
                .setContexts(InteractionContextType.GUILD)
                .addSubcommands(
                        sub("fundar", "comando.empresa.fundar.descripcion").addOptions(nombreFundar),
                        sub("info", "comando.empresa.info.descripcion").addOptions(nombreInfo),
                        sub("mejorar", "comando.empresa.mejorar.descripcion"),
                        sub("disolver", "comando.empresa.disolver.descripcion"),
                        sub("invitar", "comando.empresa.invitar.descripcion").addOptions(usuarioInvitar),
                        sub("solicitar", "comando.empresa.solicitar.descripcion")
                                .addOptions(nombreSolicitar, motivoSolicitar),
                        sub("pendientes", "comando.empresa.pendientes.descripcion"),
                        sub("rango", "comando.empresa.rango.descripcion")
                                .addOptions(usuarioRango, rangoDestino),
                        sub("sacar", "comando.empresa.sacar.descripcion").addOptions(usuarioSacar),
                        sub("despedir", "comando.empresa.despedir.descripcion").addOptions(usuarioDespedir),
                        sub("ascender", "comando.empresa.ascender.descripcion")
                                .addOptions(miembroAscender, puestoAscender),
                        sub("propuestas", "comando.empresa.propuestas.descripcion"),
                        sub("vender", "comando.empresa.vender.desc").addOptions(cantidadVender),
                        sub("ranking", "comando.empresa.ranking.desc"));
    }

    private static SubcommandData sub(String nombre, String claveDesc) {
        return new SubcommandData(nombre, Messages.get(Messages.ES, claveDesc))
                .setDescriptionLocalization(DiscordLocale.ENGLISH_US, Messages.get(Messages.EN, claveDesc));
    }

    @Override
    public void ejecutar(SlashCommandInteractionEvent evento) {
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        String sub = evento.getSubcommandName() == null ? "info" : evento.getSubcommandName();
        switch (sub) {
            case "fundar" -> fundar(evento, locale);
            case "info" -> info(evento, locale);
            case "mejorar" -> mejorar(evento, locale);
            case "disolver" -> disolver(evento, locale);
            case "invitar" -> invitar(evento, locale);
            case "solicitar" -> solicitar(evento, locale);
            case "pendientes" -> pendientes(evento, locale);
            case "rango" -> gestionar(evento, locale, TipoPropuesta.CAMBIAR_RANGO);
            case "sacar" -> gestionar(evento, locale, TipoPropuesta.SACAR);
            case "despedir" -> gestionar(evento, locale, TipoPropuesta.DESPEDIR);
            case "ascender" -> ascender(evento, locale);
            case "propuestas" -> propuestas(evento, locale);
            case "vender" -> vender(evento, locale);
            case "ranking" -> ranking(evento, locale);
            default -> evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "comando.error.generico"))).setEphemeral(true).queue();
        }
    }

    /**
     * Funda una empresa en la rama de tu trabajo. El éxito es un embed <b>público</b> de celebración
     * con mención, nombre y coins quemados; cualquier fallo va <b>efímero</b> con el dato exacto.
     */
    private void fundar(SlashCommandInteractionEvent evento, Locale locale) {
        String nombre = evento.getOption("nombre").getAsString();
        evento.deferReply(false).queue();
        SalidaFundar r = empresa.fundar(evento.getUser().getIdLong(), nombre);
        if (r.estado() == ResultadoFundar.OK) {
            // F4: se crea el canal privado inmediatamente (ya hay Guild y el dueño es quien funda) y se
            // persiste su id con fijarCanal CONDICIONAL. Si otro disparo (p.ej. un /empresa info inmediato)
            // ya materializó el canal, fijarCanal devuelve false y se borra este para no dejar huérfanos.
            // Best-effort: si el canal no se crea, se materializará luego de forma perezosa (info/ingreso).
            Guild guild = evento.getGuild();
            if (guild != null) {
                EmpresaCanal.crear(guild, nombre, evento.getUser().getIdLong(), canalId -> {
                    if (!repo.fijarCanal(r.empresaId(), canalId)) {
                        EmpresaCanal.eliminar(guild, canalId); // perdimos la carrera: canal huérfano
                    }
                });
            }
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.fundada.titulo"),
                    Messages.get(locale, "empresa.fundada.desc",
                            evento.getUser().getAsMention(), nombre, r.costeQuemado())).build()).queue();
            return;
        }
        String mensaje = switch (r.estado()) {
            case SIN_TRABAJO -> Messages.get(locale, "empresa.fundar.sin_trabajo");
            case NO_ES_TIER4 -> Messages.get(locale, "empresa.fundar.no_es_tier4");
            case YA_EN_EMPRESA -> Messages.get(locale, "empresa.fundar.ya_en_empresa");
            case NOMBRE_EN_USO -> Messages.get(locale, "empresa.fundar.nombre_en_uso");
            case SIN_SALDO -> Messages.get(locale, "empresa.fundar.sin_saldo", EmpresaService.COSTE_FUNDAR);
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setEphemeral(true).queue();
    }

    /**
     * Pinta una empresa: nombre, rama, dueño, nivel (con el bonus de ingresos actual), bote y la lista
     * de miembros con su rango. Sin nombre,
     * la tuya; con nombre, la busca por su nombre en cualquier rama (los nombres son únicos por rama,
     * así que la primera coincidencia basta en F1). Es pública (la economía se juega a la vista).
     */
    private void info(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        OptionMapping nombreOpt = evento.getOption("nombre");
        Optional<InfoEmpresa> infoOpt;
        if (nombreOpt == null) {
            infoOpt = empresa.infoDe(evento.getUser().getIdLong());
        } else {
            infoOpt = buscarPorNombre(nombreOpt.getAsString());
        }
        if (infoOpt.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.sin_empresa"))).setEphemeral(true).queue();
            return;
        }
        Empresa e = infoOpt.get().empresa();
        // F4: solo al mirar la PROPIA empresa (sin nombre) se garantiza su canal de forma perezosa; al
        // buscar otra por nombre no se toca nada (no eres miembro, no debes provocar su creación).
        if (nombreOpt == null) {
            ensureCanal(evento.getGuild(), e);
        }
        List<MiembroEmpresa> miembros = infoOpt.get().miembros();
        StringBuilder lista = new StringBuilder();
        for (MiembroEmpresa m : miembros) {
            lista.append(Messages.get(locale, "empresa.info.miembro",
                    "<@" + m.discordId() + ">", Messages.get(locale, m.rango().claveI18n()))).append('\n');
        }
        String cuerpo = Messages.get(locale, "empresa.info.cuerpo",
                e.nombre(),
                Messages.get(locale, "rama." + e.rama().toLowerCase(Locale.ROOT)),
                "<@" + e.duenoId() + ">",
                e.nivel(),
                e.nivel() * 2, // bonus de ingresos actual: +2 % por nivel
                e.bote(),
                e.mercancia(),
                Produccion.capacidad(e.nivel()), // tope del almacén: acoplado al nivel vía Produccion (F5a)
                miembros.size(),
                lista.toString().strip());
        // F5b: si arrastra impagos de la cuota semanal, se avisa de la morosidad (cerca de la quiebra).
        if (e.impagos() > 0) {
            cuerpo += "\n" + Messages.get(locale, "empresa.info.morosa", e.impagos(), Impuesto.MOROSIDAD_MAX);
        }
        evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.info.titulo"), cuerpo).build()).queue();
    }

    /**
     * Sube un nivel a tu empresa pagándolo del bote (solo el dueño). El éxito es un embed
     * <b>público</b> de celebración con el nuevo nivel y el bonus de ingresos resultante; cualquier
     * fallo (no eres dueño, tope alcanzado, bote sin fondos) va <b>efímero</b> con el dato exacto.
     */
    private void mejorar(SlashCommandInteractionEvent evento, Locale locale) {
        evento.deferReply(false).queue();
        SalidaMejora r = empresa.mejorar(evento.getUser().getIdLong());
        if (r.estado() == ResultadoMejora.OK) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.mejora.ok.titulo"),
                    Messages.get(locale, "empresa.mejora.ok",
                            r.nivelNuevo(), r.coste(), r.nivelNuevo() * 2)).build()).queue();
            return;
        }
        String mensaje = switch (r.estado()) {
            case NO_ERES_DUENO -> Messages.get(locale, "empresa.mejora.noeresdueno");
            case TOPE -> Messages.get(locale, "empresa.mejora.tope", EmpresaService.NIVEL_MAX);
            case SIN_FONDOS -> Messages.get(locale, "empresa.mejora.sinfondos", r.coste());
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje))
                .setEphemeral(true).queue();
    }

    /**
     * Garantiza que la empresa tiene canal privado (F4, creación perezosa): si {@code canalId} es null, lo
     * crea, persiste su id con fijarCanal y resincroniza a TODOS los miembros actuales. Cubre las empresas
     * fundadas antes de F4. Best-effort.
     */
    private void ensureCanal(Guild guild, Empresa emp) {
        if (guild == null || emp.canalId() != null) {
            return;
        }
        EmpresaCanal.materializar(guild, emp, repo);
    }

    /** Busca una empresa por nombre recorriendo las ramas (nombre único por rama; primera coincide). */
    private Optional<InfoEmpresa> buscarPorNombre(String nombre) {
        for (Ascensos.Rama r : Ascensos.Rama.values()) {
            Optional<InfoEmpresa> o = empresa.infoPorNombreYRama(nombre, r.name());
            if (o.isPresent()) {
                return o;
            }
        }
        return Optional.empty();
    }

    /**
     * Disolver es <b>destructivo</b> e irreversible (se borra la empresa y sus miembros en cascada),
     * así que se pide confirmación por botón, en efímero (decisión del dueño, no hay nada que celebrar).
     * La disolución real la ejecuta {@link com.gymprofit.bot.events.EmpresaBotonesListener}.
     */
    private void disolver(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        Optional<InfoEmpresa> infoOpt = empresa.infoDe(userId);
        if (infoOpt.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.disolver.sin_empresa"))).setEphemeral(true).queue();
            return;
        }
        Empresa e = infoOpt.get().empresa();
        if (e.duenoId() != userId) {
            evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.disolver.no_eres_dueno"))).setEphemeral(true).queue();
            return;
        }
        evento.replyEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                        Messages.get(locale, "empresa.disolver.confirmar", e.nombre())))
                .setComponents(botonesDisolver(locale, userId))
                .setEphemeral(true).queue();
    }

    /**
     * Botones Sí/No de la confirmación de disolver. El customId lleva el id del dueño
     * ({@code empresa:disolver:<accion>:<ownerId>}), mismo patrón que la dimisión de {@code /trabajo}.
     */
    public static ActionRow botonesDisolver(Locale locale, long ownerId) {
        return ActionRow.of(
                Button.danger(BOTON_DISOLVER_SI + ":" + ownerId, Messages.get(locale, "empresa.boton.si")),
                Button.secondary(BOTON_DISOLVER_NO + ":" + ownerId, Messages.get(locale, "empresa.boton.no")));
    }

    /**
     * El dueño invita a un jugador. La confirmación al que invita va <b>efímera</b>; la invitación en
     * sí se <b>publica en el canal</b> con la mención del invitado y botones Aceptar/Rechazar, para que
     * la vea aunque tenga los MDs cerrados y no dependa de estar mirando el chat justo entonces.
     */
    private void invitar(SlashCommandInteractionEvent evento, Locale locale) {
        long duenoId = evento.getUser().getIdLong();
        User objetivo = evento.getOption("usuario").getAsUser();
        evento.deferReply(true).queue();
        ResultadoIngreso r = empresa.invitar(duenoId, objetivo.getIdLong());
        if (r != ResultadoIngreso.OK) {
            evento.getHook().sendMessageEmbeds(
                    EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensajeIngreso(locale, r))).queue();
            return;
        }
        // Confirmación efímera al que invita.
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.invitacion.enviada", objetivo.getAsMention()))).queue();
        // Notificación pública al invitado, con los botones para resolverla. Se resuelve el id de la
        // pendiente recién creada buscando entre las invitaciones del objetivo la de esta empresa.
        Empresa e = empresa.infoDe(duenoId).map(InfoEmpresa::empresa).orElse(null);
        if (e == null) {
            return;
        }
        Empresa emp = e;
        repo.invitacionesA(objetivo.getIdLong()).stream()
                .filter(p -> p.empresaId() == emp.id())
                .findFirst()
                .ifPresent(p -> evento.getChannel().sendMessage(objetivo.getAsMention())
                        .setEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                Messages.get(locale, "empresa.invitacion.recibida.titulo"),
                                Messages.get(locale, "empresa.invitacion.recibida.desc",
                                        objetivo.getAsMention(), emp.nombre())).build())
                        .setComponents(botonesResolver(locale, p.id(), "empresa.boton.aceptar"))
                        .queue());
    }

    /**
     * El jugador solicita entrar en una empresa de su rama, con un motivo opcional. Va todo
     * <b>efímero</b>: el dueño la verá cuando haga {@code /empresa pendientes} (no se le hace ping para
     * no ensuciar el canal; decisión conservadora de F1).
     */
    private void solicitar(SlashCommandInteractionEvent evento, Locale locale) {
        String nombre = evento.getOption("nombre").getAsString();
        OptionMapping motivoOpt = evento.getOption("motivo");
        String motivo = motivoOpt == null ? null : motivoOpt.getAsString();
        evento.deferReply(true).queue();
        ResultadoIngreso r = empresa.solicitar(evento.getUser().getIdLong(), nombre, motivo);
        String mensaje = r == ResultadoIngreso.OK
                ? Messages.get(locale, "empresa.solicitud.enviada", nombre)
                : mensajeIngreso(locale, r);
        evento.getHook().sendMessageEmbeds(
                EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale, mensaje)).queue();
    }

    /**
     * Lista tus pendientes, en efímero (gestión personal). Si eres <b>dueño</b>, las solicitudes de
     * ingreso a tu empresa (con su motivo) con botones Aprobar/Rechazar; si no, tus invitaciones
     * recibidas con Aceptar/Rechazar. Cada pendiente va en su propio mensaje para que sus botones
     * queden inequívocamente ligados a ella.
     */
    private void pendientes(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        evento.deferReply(true).queue();
        Optional<InfoEmpresa> infoOpt = empresa.infoDe(userId);
        boolean esDueno = infoOpt.map(i -> i.empresa().duenoId() == userId).orElse(false);
        if (esDueno) {
            List<Pendiente> solicitudes = repo.pendientesDe(infoOpt.get().empresa().id(),
                    TipoPendiente.SOLICITUD);
            if (solicitudes.isEmpty()) {
                avisarSinPendientes(evento, locale);
                return;
            }
            solicitudes.stream().limit(MAX_PENDIENTES).forEach(p -> evento.getHook().sendMessageEmbeds(
                            EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                    Messages.get(locale, "empresa.pendientes.titulo"),
                                    Messages.get(locale, "empresa.pendiente.solicitud",
                                            "<@" + p.discordId() + ">", motivoTexto(locale, p.motivo()))).build())
                    .setComponents(botonesResolver(locale, p.id(), "empresa.boton.aprobar")).queue());
        } else {
            List<Pendiente> invitaciones = repo.invitacionesA(userId);
            if (invitaciones.isEmpty()) {
                avisarSinPendientes(evento, locale);
                return;
            }
            invitaciones.stream().limit(MAX_PENDIENTES).forEach(p -> {
                String nombreEmpresa = repo.porId(p.empresaId()).map(Empresa::nombre).orElse("?");
                evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                Messages.get(locale, "empresa.pendientes.titulo"),
                                Messages.get(locale, "empresa.pendiente.invitacion", nombreEmpresa)).build())
                        .setComponents(botonesResolver(locale, p.id(), "empresa.boton.aceptar")).queue();
            });
        }
    }

    private static void avisarSinPendientes(SlashCommandInteractionEvent evento, Locale locale) {
        evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.sin_pendientes"))).queue();
    }

    /** Motivo de una solicitud, o un guion si no lo dejó. */
    private static String motivoTexto(Locale locale, String motivo) {
        return (motivo == null || motivo.isBlank())
                ? Messages.get(locale, "empresa.pendiente.sin_motivo") : motivo;
    }

    /**
     * Botones Aceptar/Rechazar de una pendiente. El customId codifica el id de la pendiente y la acción
     * ({@code empresa:resolver:<pendienteId>:<1|0>}); la potestad (quién puede resolverla) la valida
     * {@link EmpresaService#resolver} en el listener, no el botón.
     *
     * @param aceptarKey clave i18n de la etiqueta positiva (aceptar una invitación / aprobar una solicitud)
     */
    private static ActionRow botonesResolver(Locale locale, long pendienteId, String aceptarKey) {
        return ActionRow.of(
                Button.success(BOTON_RESOLVER + ":" + pendienteId + ":1", Messages.get(locale, aceptarKey)),
                Button.danger(BOTON_RESOLVER + ":" + pendienteId + ":0",
                        Messages.get(locale, "empresa.boton.rechazar")));
    }

    /**
     * Gestión de plantilla (F2): cambiar rango, sacar o despedir. Delega toda la autoridad en
     * {@link EmpresaGestionService#gestionar}. Si el actor es dueño la acción se ejecuta y se anuncia
     * <b>pública</b> (se ve a la vista del canal); si es directivo se abre una propuesta pública con
     * botones de voto para los altos cargos; los errores van <b>efímeros</b>.
     */
    private void gestionar(SlashCommandInteractionEvent evento, Locale locale, TipoPropuesta tipo) {
        long actorId = evento.getUser().getIdLong();
        User objetivo = evento.getOption("usuario").getAsUser();
        RangoEmpresa rangoNuevo = tipo == TipoPropuesta.CAMBIAR_RANGO
                ? RangoEmpresa.valueOf(evento.getOption("rango").getAsString()) : null;
        evento.deferReply(false).queue();
        ResultadoGestion r = gestion.gestionar(actorId, tipo, objetivo.getIdLong(), rangoNuevo);
        switch (r) {
            case EJECUTADA -> {
                evento.getHook().sendMessageEmbeds(
                        EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                Messages.get(locale, "empresa.gestion.hecha.titulo"),
                                mensajeEjecutada(locale, tipo, objetivo, rangoNuevo)).build()).queue();
                // F4: sacar/despedir saca al objetivo de la plantilla → se le quita también el acceso al
                // canal. Cambiar de rango NO (sigue siendo miembro). Best-effort. El canalId sale de la
                // empresa del actor (dueño), que permanece tras la acción.
                if ((tipo == TipoPropuesta.SACAR || tipo == TipoPropuesta.DESPEDIR)
                        && evento.getGuild() != null) {
                    empresa.infoDe(actorId).map(i -> i.empresa().canalId()).ifPresent(canalId ->
                            EmpresaCanal.quitar(evento.getGuild(), canalId, objetivo.getIdLong()));
                }
            }
            case PROPUESTA_CREADA -> anunciarPropuesta(evento, locale, actorId, tipo, objetivo.getIdLong());
            default -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA,
                    locale, mensajeGestionFallida(locale, r))).setEphemeral(true).queue();
        }
    }

    /** Frase pública de una acción ya ejecutada por el dueño, según el tipo. */
    private static String mensajeEjecutada(Locale locale, TipoPropuesta tipo, User objetivo,
                                           RangoEmpresa rangoNuevo) {
        return switch (tipo) {
            case CAMBIAR_RANGO -> Messages.get(locale, "empresa.rango.cambiado",
                    objetivo.getAsMention(), Messages.get(locale, rangoNuevo.claveI18n()));
            case SACAR -> Messages.get(locale, "empresa.sacado", objetivo.getAsMention());
            case DESPEDIR -> Messages.get(locale, "empresa.despedido", objetivo.getAsMention());
            // ASCENSO (patrocinio de carrera) tiene su propio flujo de F3; no llega por esta vía de gestión.
            case ASCENSO -> throw new IllegalStateException("ASCENSO no se ejecuta por gestión");
        };
    }

    /** Traduce un fallo de gestión a su mensaje i18n (los éxitos se tratan aparte). */
    private static String mensajeGestionFallida(Locale locale, ResultadoGestion r) {
        return switch (r) {
            case NO_AUTORIZADO -> Messages.get(locale, "empresa.gestion.no_autorizado");
            case RANGO_INVALIDO -> Messages.get(locale, "empresa.gestion.rango_invalido");
            case NO_ES_MIEMBRO -> Messages.get(locale, "empresa.gestion.no_es_miembro");
            case YA_HAY_PROPUESTA -> Messages.get(locale, "empresa.gestion.ya_hay_propuesta");
            case EJECUTADA, PROPUESTA_CREADA -> throw new IllegalStateException("éxito ya tratado");
        };
    }

    /**
     * Patrocina el ascenso de carrera de un miembro pagándolo del bote (F3). El dueño lo ejecuta directo
     * (embed <b>público</b> de celebración con la mención, el puesto y los coins quemados del bote); un
     * directivo abre una propuesta pública con botones de voto. Los errores van <b>efímeros</b>. Toda la
     * autoridad y las reglas de dinero viven en {@link EmpresaGestionService#ascensoPatrocinado}.
     */
    private void ascender(SlashCommandInteractionEvent evento, Locale locale) {
        long actorId = evento.getUser().getIdLong();
        User objetivo = evento.getOption("miembro").getAsUser();
        String puesto = evento.getOption("puesto").getAsString();
        long coste = trabajos.costeAscenso(puesto);
        evento.deferReply(false).queue();
        ResultadoAscensoPatrocinado r = gestion.ascensoPatrocinado(actorId, objetivo.getIdLong(), puesto);
        switch (r) {
            case OK -> evento.getHook().sendMessageEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA,
                    locale, Messages.get(locale, "empresa.ascenso.ok.titulo"),
                    Messages.get(locale, "empresa.ascenso.ok",
                            objetivo.getAsMention(),
                            Messages.get(locale, "trabajo." + puesto), coste)).build()).queue();
            case PROPUESTA_CREADA ->
                    anunciarPropuesta(evento, locale, actorId, TipoPropuesta.ASCENSO, objetivo.getIdLong());
            default -> evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA,
                    locale, mensajeAscensoFallido(locale, r, coste))).setEphemeral(true).queue();
        }
    }

    /** Traduce un resultado de ascenso patrocinado fallido a su mensaje i18n (los éxitos se tratan aparte). */
    private static String mensajeAscensoFallido(Locale locale, ResultadoAscensoPatrocinado r, long coste) {
        return switch (r) {
            case NO_AUTORIZADO -> Messages.get(locale, "empresa.ascenso.no_autorizado");
            case NO_ES_MIEMBRO -> Messages.get(locale, "empresa.ascenso.no_es_miembro");
            case RANGO_INVALIDO -> Messages.get(locale, "empresa.ascenso.rango_invalido");
            case REQUISITOS -> Messages.get(locale, "empresa.ascenso.requisitos");
            case SIN_FONDOS -> Messages.get(locale, "empresa.ascenso.sin_fondos", coste);
            case YA_HAY_PROPUESTA -> Messages.get(locale, "empresa.ascenso.ya_hay_propuesta");
            case OK, PROPUESTA_CREADA -> throw new IllegalStateException("éxito ya tratado");
        };
    }

    /**
     * Autocompleta el puesto del ascenso con los destinos válidos del <b>miembro elegido</b> (los del
     * siguiente tier de su rama). Si aún no eligió a nadie, cae a las opciones del propio invocador; el
     * service revalida en cualquier caso. Responde siempre (aunque vacío) y sin {@code defer}: Discord da
     * 3 s. Mismo patrón que {@code /trabajo ascender}.
     */
    @Override
    public void autocompletar(CommandAutoCompleteInteractionEvent evento) {
        if (!"puesto".equals(evento.getFocusedOption().getName())) {
            evento.replyChoices(List.of()).queue();
            return;
        }
        Locale locale = Messages.desdeTag(evento.getUserLocale().getLocale());
        // El USER ya elegido se resuelve por su snowflake aunque no esté en caché (getAsLong).
        OptionMapping miembro = evento.getOption("miembro");
        long objetivoId = miembro != null ? miembro.getAsLong() : evento.getUser().getIdLong();
        List<Command.Choice> opciones = trabajos.opcionesAscenso(objetivoId).stream()
                .map(t -> new Command.Choice(etiqueta(locale, t.id()), t.id()))
                .limit(MAX_SUGERENCIAS)
                .toList();
        evento.replyChoices(opciones).queue();
    }

    /** Nombre localizado del puesto para una sugerencia, recortado al máximo que admite Discord. */
    private static String etiqueta(Locale locale, String puestoId) {
        String texto = Messages.get(locale, "trabajo." + puestoId);
        return texto.length() > MAX_LARGO_SUGERENCIA
                ? texto.substring(0, MAX_LARGO_SUGERENCIA) : texto;
    }

    /**
     * Anuncia públicamente la propuesta recién creada por un directivo, con botones de voto Sí/No.
     * {@code gestionar} no devuelve el id de la propuesta, así que se recupera leyendo las propuestas
     * activas de la empresa y quedándose con la única que coincide en tipo y objetivo (la UNIQUE
     * {@code uq_propuesta_activa} garantiza que no hay dos iguales abiertas a la vez). Es la vía simple
     * frente a exponer el id desde el service: los listados de propuestas son consultas de apoyo.
     */
    private void anunciarPropuesta(SlashCommandInteractionEvent evento, Locale locale, long actorId,
                                   TipoPropuesta tipo, long objetivoId) {
        Optional<InfoEmpresa> infoOpt = empresa.infoDe(actorId);
        if (infoOpt.isEmpty()) {
            return;
        }
        propuestasRepo.activasDe(infoOpt.get().empresa().id()).stream()
                .filter(p -> p.tipo() == tipo && p.objetivoId() == objetivoId)
                .findFirst()
                .ifPresent(p -> evento.getHook().sendMessageEmbeds(
                                EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                        Messages.get(locale, "empresa.propuesta.anuncio.titulo"),
                                        cuerpoPropuesta(locale, p)).build())
                        .setComponents(botonesVoto(locale, p.id())).queue());
    }

    /**
     * Lista las propuestas abiertas de tu empresa, en efímero (gestión interna). Solo para altos cargos
     * (Dueño/Directivo); cada propuesta va en su propio mensaje con sus botones de voto, tope 5 filas
     * como los pendientes de F1.
     */
    private void propuestas(SlashCommandInteractionEvent evento, Locale locale) {
        long userId = evento.getUser().getIdLong();
        evento.deferReply(true).queue();
        Optional<InfoEmpresa> infoOpt = empresa.infoDe(userId);
        if (infoOpt.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.sin_empresa"))).queue();
            return;
        }
        InfoEmpresa info = infoOpt.get();
        RangoEmpresa rango = rangoDe(info.miembros(), userId);
        if (rango != RangoEmpresa.DUENO && rango != RangoEmpresa.DIRECTIVO) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.propuesta.no_alto_cargo"))).queue();
            return;
        }
        List<Propuesta> activas = propuestasRepo.activasDe(info.empresa().id());
        if (activas.isEmpty()) {
            evento.getHook().sendMessageEmbeds(EmbedFactory.aviso(EmbedFactory.Tipo.ECONOMIA, locale,
                    Messages.get(locale, "empresa.propuesta.sin_propuestas"))).queue();
            return;
        }
        activas.stream().limit(MAX_PENDIENTES).forEach(p -> evento.getHook().sendMessageEmbeds(
                        EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                                Messages.get(locale, "empresa.propuesta.lista.titulo"),
                                cuerpoPropuesta(locale, p)).build())
                .setComponents(botonesVoto(locale, p.id())).queue());
    }

    /**
     * Vende la mercancía del almacén de tu empresa (F5a): solo altos cargos. El neto entra al bote y el
     * impuesto se quema; toda la regla de dinero (gate atómico incluido) vive en {@link EmpresaVentaService}.
     * Público (la economía se juega a la vista): el éxito celebra las cifras y los errores llevan el dato
     * exacto, todo con el mismo {@code Tipo.ECONOMIA} que el resto del comando.
     */
    private void vender(SlashCommandInteractionEvent evento, Locale locale) {
        OptionMapping opt = evento.getOption("cantidad");
        // Sin opción = vender todo el almacén (OptionalLong.empty); el service acota con Math.min.
        OptionalLong cantidad = opt == null ? OptionalLong.empty() : OptionalLong.of(opt.getAsLong());
        EmpresaVentaService.Resultado r = venta.vender(evento.getUser().getIdLong(), cantidad);
        String msg = switch (r.estado()) {
            case SIN_EMPRESA -> Messages.get(locale, "empresa.venta.sin_empresa");
            case NO_AUTORIZADO -> Messages.get(locale, "empresa.venta.no_autorizado");
            case SIN_MERCANCIA -> Messages.get(locale, "empresa.venta.sin_mercancia");
            case OK -> Messages.get(locale, "empresa.venta.ok",
                    r.unidades(), r.bruto(), r.impuesto(), r.neto(), r.restante());
        };
        evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.ECONOMIA, locale,
                Messages.get(locale, "empresa.venta.titulo"), msg).build()).queue();
    }

    /**
     * Pinta el top {@value #TOP} de empresas por prestigio (F4). Público. Reusa el estilo de podio de
     * {@code TopComando} (medallas para el podio, número para el resto).
     */
    private void ranking(SlashCommandInteractionEvent evento, Locale locale) {
        List<EmpresaService.FilaRanking> top = empresa.ranking(TOP);
        if (top.isEmpty()) {
            evento.replyEmbeds(EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                    Messages.get(locale, "empresa.ranking.titulo"),
                    Messages.get(locale, "empresa.ranking.vacio")).build()).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        int puesto = 1;
        for (EmpresaService.FilaRanking f : top) {
            // Podio con medallas, resto con el número de puesto en negrita (mismo criterio que TopComando).
            String medalla = switch (puesto) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "**" + puesto + ".**";
            };
            sb.append(Messages.get(locale, "empresa.ranking.fila",
                    medalla, f.nombre(),
                    Messages.get(locale, "rama." + f.rama().toLowerCase(Locale.ROOT)),
                    f.nivel(), f.miembros(), f.bote()));
            sb.append('\n');
            puesto++;
        }
        var embed = EmbedFactory.base(EmbedFactory.Tipo.STATS, locale,
                Messages.get(locale, "empresa.ranking.titulo"), sb.toString().strip()).build();
        evento.replyEmbeds(embed).queue();
    }

    /** Texto de una propuesta: sobre quién, qué propone quién y cuándo caduca (timestamp relativo de Discord). */
    private static String cuerpoPropuesta(Locale locale, Propuesta p) {
        String accion = switch (p.tipo()) {
            case CAMBIAR_RANGO -> Messages.get(locale, "empresa.propuesta.tipo.cambiar_rango",
                    Messages.get(locale, p.rangoNuevo().claveI18n()));
            case SACAR -> Messages.get(locale, "empresa.propuesta.tipo.sacar");
            case DESPEDIR -> Messages.get(locale, "empresa.propuesta.tipo.despedir");
            // ASCENSO (patrocinio de carrera, F3): el puesto destino viaja en `dato`.
            case ASCENSO -> Messages.get(locale, "empresa.propuesta.tipo.ascenso",
                    Messages.get(locale, "trabajo." + p.dato()));
        };
        return Messages.get(locale, "empresa.propuesta.cuerpo",
                "<@" + p.proponenteId() + ">",
                accion,
                "<@" + p.objetivoId() + ">",
                "<t:" + p.expira().getEpochSecond() + ":R>");
    }

    /** Botones Sí/No de una propuesta: {@code empresa:voto:<propuestaId>:<1|0>}. */
    private static ActionRow botonesVoto(Locale locale, long propuestaId) {
        return ActionRow.of(
                Button.success(BOTON_VOTO + ":" + propuestaId + ":1",
                        Messages.get(locale, "empresa.boton.voto_si")),
                Button.danger(BOTON_VOTO + ":" + propuestaId + ":0",
                        Messages.get(locale, "empresa.boton.voto_no")));
    }

    /** Rango de un miembro dentro de la lista, o {@code null} si no está. */
    private static RangoEmpresa rangoDe(List<MiembroEmpresa> miembros, long discordId) {
        return miembros.stream()
                .filter(m -> m.discordId() == discordId)
                .map(MiembroEmpresa::rango)
                .findFirst().orElse(null);
    }

    /** Traduce un resultado de ingreso (invitar/solicitar) fallido a su mensaje i18n. */
    private static String mensajeIngreso(Locale locale, ResultadoIngreso r) {
        return switch (r) {
            case SIN_TRABAJO -> Messages.get(locale, "empresa.ingreso.sin_trabajo");
            case OTRA_RAMA -> Messages.get(locale, "empresa.ingreso.otra_rama");
            case YA_EN_EMPRESA -> Messages.get(locale, "empresa.ingreso.ya_en_empresa");
            case EMPRESA_NO_EXISTE -> Messages.get(locale, "empresa.ingreso.empresa_no_existe");
            case YA_PENDIENTE -> Messages.get(locale, "empresa.ingreso.ya_pendiente");
            case ES_MISMO -> Messages.get(locale, "empresa.ingreso.es_mismo");
            case OK -> throw new IllegalStateException("OK ya tratado");
        };
    }
}
