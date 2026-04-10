package me.aris.arisrtpqueue.listeners;

import me.aris.arisrtpqueue.ArisRTPQueue;
import me.aris.arisrtpqueue.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import java.util.List;

public class GUIListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = ColorUtils.format(ArisRTPQueue.getInstance().getConfig().getString("gui.title"));
        if (!e.getView().getTitle().equals(title)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        if (slot == ArisRTPQueue.getInstance().getConfig().getInt("gui.buttons.invite.slot")) {
            ArisRTPQueue.getInstance().getQueueManager().playSound(p, "gui-click");
            Location loc = p.getLocation().clone().add(0, -2, 0);
            p.sendBlockChange(loc, Material.OAK_SIGN.createBlockData());
            List<String> lines = ArisRTPQueue.getInstance().getMessages().getStringList("sign-gui");
            String[] signLines = new String[4];
            for(int i=0; i<4; i++) signLines[i] = i < lines.size() ? ColorUtils.format(lines.get(i)) : "";
            p.openSign(loc, signLines);
        } else if (slot == ArisRTPQueue.getInstance().getConfig().getInt("gui.buttons.search.slot")) {
            ArisRTPQueue.getInstance().getQueueManager().add(p);
            p.closeInventory();
        } else if (slot == ArisRTPQueue.getInstance().getConfig().getInt("gui.buttons.cancel.slot")) {
            ArisRTPQueue.getInstance().getQueueManager().remove(p);
            p.closeInventory();
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        String name = e.getLine(0);
        if (name != null && !name.isEmpty()) e.getPlayer().performCommand("rtpq invite " + name);
    }
            }
