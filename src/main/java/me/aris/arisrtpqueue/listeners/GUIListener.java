package me.aris.arisrtpqueue.listeners;

import me.aris.arisrtpqueue.ArisRTPQueue;
import me.aris.arisrtpqueue.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
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
            openInviteSign(p);
        } else if (slot == ArisRTPQueue.getInstance().getConfig().getInt("gui.buttons.search.slot")) {
            ArisRTPQueue.getInstance().getQueueManager().add(p);
            p.closeInventory();
        } else if (slot == ArisRTPQueue.getInstance().getConfig().getInt("gui.buttons.cancel.slot")) {
            ArisRTPQueue.getInstance().getQueueManager().remove(p);
            p.closeInventory();
        }
    }

    private void openInviteSign(Player p) {
        Location loc = p.getLocation().clone();
        loc.setY((double) p.getWorld().getMinHeight()); 

        p.sendBlockChange(loc, Material.OAK_SIGN.createBlockData());

        Sign sign = (Sign) Material.OAK_SIGN.createBlockData().createBlockState();
        List<String> lines = ArisRTPQueue.getInstance().getMessages().getStringList("sign-gui");
        
        for (int i = 0; i < 4; i++) {
            String line = (lines != null && i < lines.size()) ? ColorUtils.format(lines.get(i)) : "";
            sign.setLine(i, line);
        }

        p.openSign(sign);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        String name = e.getLine(0);
        if (name != null && !name.isEmpty()) {
            e.getPlayer().performCommand("rtpq invite " + name);
        }
    }
            }
