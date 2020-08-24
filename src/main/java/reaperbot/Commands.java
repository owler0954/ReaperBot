package reaperbot;

import arc.files.Fi;
import arc.math.Mathf;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.Streams;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.type.ItemStack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static reaperbot.ReaperBot.*;

public class Commands{
    private final String prefix = "$";
    private final CommandHandler handler = new CommandHandler(prefix);
    private final CommandHandler adminHandler = new CommandHandler(prefix);
    private final String[] warningStrings = {"once", "twice", "thrice"};

    public Guild roleGuild;

    Commands() {
        handler.register("help", "Displays all bot commands.", args -> {
            StringBuilder builder = new StringBuilder();

            for (Command command : handler.getCommandList()) {
                builder.append(prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if (command.params.length > 0) {
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append("*");
                }
                builder.append(" - ");
                builder.append(command.description);
                builder.append("\n");
            }
            messages.info("Commands", builder.toString());
        });
        handler.register("postmap", "Post a .msav file to the #maps--����� channel.", args -> {
            Message message = messages.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".msav")){
                messages.err("You must have one .msav file in the same message as the command!");
                messages.deleteMessages();
                return;
            }

            Message.Attachment a = message.getAttachments().get(0);

            try{
                ContentHandler.Map map = contentHandler.parseMap(net.download(a.getUrl()));
                new File("maps/").mkdir();
                File mapFile = new File("maps/" + a.getFileName());
                File imageFile = new File("maps/image_" + a.getFileName().replace(".msav", ".png"));
                Streams.copy(net.download(a.getUrl()), new FileOutputStream(mapFile));
                ImageIO.write(map.image, "png", imageFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                .setImage("attachment://" + imageFile.getName())
                .setAuthor(messages.lastUser.getName(), messages.lastUser.getAvatarUrl(), messages.lastUser.getAvatarUrl()).setTitle(map.name == null ? a.getFileName().replace(".msav", "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                messages.channel.getGuild().getTextChannelById(mapsChannelID).sendFile(mapFile).addFile(imageFile).embed(builder.build()).queue();

                messages.text("*Map posted successfully.*");
            }catch(Exception e){
                e.printStackTrace();
                messages.err("Error parsing map.", Strings.parseException(e, true));
                messages.deleteMessages();
            }
        });
        handler.register("postschem", "[schem]", "Post a .msch to the #schematics--����� channel.", args -> {
            Message message = messages.lastMessage;

            try{
                Schematic schem = message.getAttachments().size() == 1 ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl()) : contentHandler.parseSchematic(args[0]);

                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = new File("schem/img_" + UUID.randomUUID().toString() + ".png");
                File schemFile = new File("schem/" + schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                        .setImage("attachment://" + previewFile.getName())
                        .setAuthor(message.getAuthor().getName(), message.getAuthor().getAvatarUrl(), message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = jda.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? jda.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.addField("Requirements", field.toString(), false);

                messages.channel.getGuild().getTextChannelById(schematicsChannelID).sendFile(schemFile).addFile(previewFile).embed(builder.build()).queue();
                message.delete().queue();
            }catch(Exception e){
                message.delete().queue();
                Log.err("Failed to parse schematic, skipping.");
            }
        });
        adminHandler.register("delete", "<amount>", "Delete some messages.", args -> {
            try {
                int number = Integer.parseInt(args[0]) + 1;
                MessageHistory hist = messages.channel.getHistoryBefore(messages.lastMessage, number).complete();
                messages.channel.deleteMessages(hist.getRetrievedHistory()).queue();
                Log.info("Deleted {0} messages.", number);
            } catch (NumberFormatException e) {
                messages.err("Invalid number.");
            }
        });
        adminHandler.register("warn", "<@user> [reason...]", "Warn a user.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);

            try {
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();

                data.addWarn(l);

                int warnings = data.getWarns(l);
                messages.text("**{0}**, you've been warned *{1}*.", user.getAsMention(), warningStrings[Mathf.clamp(warnings - 1, 0, warningStrings.length - 1)]);

                if (data.getWarns(l) >= 3) {
                    EmbedBuilder builder = new EmbedBuilder();
                    builder.setColor(messages.normalColor);
                    builder.addField("Mute", Strings.format("������������ **{0}** ��������!", user.getName()), true);
                    builder.setFooter(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now()));

                    jda.getTextChannelById(moderationChannelID).sendMessage(builder.build()).queue();
                    roleGuild.addRoleToMember(user.getIdLong(), jda.getRolesByName("Mute", true).get(0)).queue();
                }
            } catch (Exception e) {
                Log.err(e);
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });
        adminHandler.register("warnings", "<@user>", "Get number of warnings a user has.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);
            try {
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();
                int warnings = data.getWarns(l);
                messages.text("User '{0}' has **{1}** {2}.", user.getName(), warnings, warnings == 1 ? "warning" : "warnings");
            } catch (Exception e) {
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });
        adminHandler.register("unwarn", "<@user> [count]", "Unwarn a user.", args -> {
            if(args.length > 1 && !Strings.canParseInt(args[1])){
                messages.lastSentMessage.getTextChannel().sendMessage("'count' must be a integer!").queue();
                messages.deleteMessages();
                return;
            }

            String author = args[0].substring(2, args[0].length() - 1);
            int warnings = args.length > 1 ? Strings.parseInt(args[1]) : 1;
            if (author.startsWith("!")) author = author.substring(1);

            try {
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();
                data.removeWarns(l, warnings);

                messages.text("{0} was removed from {1}", warnings > 1 ? "warnings" : "Warning", user.getName());
            } catch (Exception e) {
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });
    }

    void handle(MessageReceivedEvent event){
        if(event.getAuthor().isBot()) return;

        String text = event.getMessage().getContentRaw();
        roleGuild = event.getGuild();

        if(event.getMessage().getContentRaw().startsWith(prefix) && (isAdmin(event.getMember()) || event.getTextChannel().getIdLong() == commandChannelID)){
            messages.channel = event.getTextChannel();
            messages.lastUser = event.getAuthor();
            messages.lastMessage = event.getMessage();
        }

        if(isAdmin(event.getMember())){
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }else{
            handleResponse(handler.handleMessage(text), true);
        }
    }

    boolean isAdmin(Member member) {
        try {
            return member.getRoles().stream().anyMatch(r -> r.getIdLong() == ownerRoleID || r.getIdLong() == adminRoleID);
        } catch (Exception e) {
            return false;
        }
    }

    boolean handleResponse(CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                messages.err("Unknown command. Type " + prefix + "help for a list of commands.");
                messages.deleteMessages();
            }
            return false;
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                messages.err("Invalid arguments.", "Usage: {0}{1}", prefix, response.command.text);
            }else{
                messages.err("Invalid arguments.", "Usage: {0}{1} *{2}*", prefix, response.command.text, response.command.paramText);
            }
            messages.deleteMessages();
            return false;
        }
        return true;
    }
}