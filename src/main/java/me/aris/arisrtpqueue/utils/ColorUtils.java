package me.aris.arisrtpqueue.utils;

import me.aris.arisrtpqueue.ArisRTPQueue;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String format(String msg) {
        if (msg == null) return "";
        Matcher m = HEX_PATTERN.matcher(msg);
        StringBuilder b = new StringBuilder();
        while (m.find()) m.appendReplacement(b, ChatColor.of("#" + m.group(1)).toString());
        return ChatColor.translateAlternateColorCodes('&', m.appendTail(b).toString());
    }

    public static void send(Player p, String key) {
        String prefix = ArisRTPQueue.getInstance().getMessages().getString("prefix", "");
        String chat = ArisRTPQueue.getInstance().getMessages().getString("chat-" + key);
        String action = ArisRTPQueue.getInstance().getMessages().getString("actionbar-" + key);
        if (chat != null) p.sendMessage(format(prefix + chat));
        if (action != null) p.sendActionBar(Component.text(format(action)));
    }
                                            }
