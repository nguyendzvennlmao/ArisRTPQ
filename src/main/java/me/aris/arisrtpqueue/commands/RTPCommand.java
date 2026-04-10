package me.aris.arisrtpqueue.commands;

import me.aris.arisrtpqueue.ArisRTPQueue;
import me.aris.arisrtpqueue.manager.GUIProvider;
import me.aris.arisrtpqueue.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.stream.Collectors;

public class RTPCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("accept")) {
                UUID inv = ArisRTPQueue.getInstance().getQueueManager().getInviter(p.getUniqueId());
                if (inv != null) {
                    ArisRTPQueue.getInstance().getQueueManager().add(p);
                    Player target = Bukkit.getPlayer(inv);
                    if (target != null) ArisRTPQueue.getInstance().getQueueManager().add(target);
                } else ColorUtils.send(p, "no-invite");
                return true;
            }
            if (sub.equals("invite") && args.length > 1) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { ColorUtils.send(p, "player-not-found"); return true; }
                ArisRTPQueue.getInstance().getQueueManager().addInvite(target.getUniqueId(), p.getUniqueId());
                p.sendMessage(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("prefix") + ArisRTPQueue.getInstance().getMessages().getString("chat-invite-sent").replace("%player%", target.getName())));
                String raw = ArisRTPQueue.getInstance().getMessages().getString("chat-invite-received").replace("%player%", p.getName());
                Component msg = Component.text(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("prefix") + raw.replace("[CHẤP NHẬN]", "")))
                    .append(Component.text(ColorUtils.format("&a&l[CHẤP NHẬN]"))
                        .clickEvent(ClickEvent.runCommand("/rtpq accept"))
                        .hoverEvent(HoverEvent.showText(Component.text(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("chat-invite-hover"))))));
                target.sendMessage(msg);
                ArisRTPQueue.getInstance().getQueueManager().playSound(target, "invite-received");
                return true;
            }
            if (sub.equals("reload") && p.hasPermission("rtpq.admin")) {
                ArisRTPQueue.getInstance().reloadPluginConfig();
                ColorUtils.send(p, "reload-success");
                return true;
            }
            if (sub.equals("join")) { ArisRTPQueue.getInstance().getQueueManager().add(p); return true; }
            if (sub.equals("leave")) { ArisRTPQueue.getInstance().getQueueManager().remove(p); return true; }
        }
        GUIProvider.open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a, String[] args) {
        if (args.length == 1) return List.of("join", "leave", "invite", "accept", "reload").stream().filter(i -> i.startsWith(args[0])).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[1])).collect(Collectors.toList());
        return Collections.emptyList();
    }
  }
