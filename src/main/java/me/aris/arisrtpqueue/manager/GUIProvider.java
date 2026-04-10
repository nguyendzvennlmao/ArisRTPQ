package me.aris.arisrtpqueue.manager;

import me.aris.arisrtpqueue.ArisRTPQueue;
import me.aris.arisrtpqueue.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;
import java.util.stream.Collectors;

public class GUIProvider {
    public static void open(Player player) {
        FileConfiguration c = ArisRTPQueue.getInstance().getConfig();
        Inventory inv = Bukkit.createInventory(null, c.getInt("gui.size"), ColorUtils.format(c.getString("gui.title")));
        String[] keys = {"cancel", "wait-time", "invite", "region", "search"};
        for (String k : keys) {
            String p = "gui.buttons." + k + ".";
            ItemStack item = new ItemStack(Material.valueOf(c.getString(p + "material")));
            ItemMeta m = item.getItemMeta();
            m.setDisplayName(ColorUtils.format(c.getString(p + "name")));
            List<String> lore = c.getStringList(p + "lore").stream()
                .map(l -> ColorUtils.format(l.replace("%queue_size%", String.valueOf(ArisRTPQueue.getInstance().getQueueManager().getSize())).replace("%ping%", String.valueOf(player.getPing()))))
                .collect(Collectors.toList());
            m.setLore(lore);
            item.setItemMeta(m);
            inv.setItem(c.getInt(p + "slot"), item);
        }
        player.openInventory(inv);
    }
}
