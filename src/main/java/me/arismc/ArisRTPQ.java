package me.arismc;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArisRTPQ extends JavaPlugin implements Listener {

    private final List<UUID> queue = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("rtpqueue").setExecutor(new RTPQCommand());

        // Update Action Bar cho người đang đợi
        Bukkit.getAsyncScheduler().runAtFixedRate(this, (task) -> {
            for (UUID id : queue) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        new TextComponent(format("&#facc15Waiting... &7(" + queue.size() + "/2)")));
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public String format(String msg) {
        if (msg == null) return "";
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(msg);
        while (matcher.find()) {
            String color = msg.substring(matcher.start(), matcher.end());
            msg = msg.replace(color, ChatColor.of(color.substring(1)).toString());
            matcher = pattern.matcher(msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, format(getConfig().getString("gui.title")));
        
        // Decor nền
        ItemStack decor = createItem(Material.valueOf(getConfig().getString("gui.items.decor.material")), " ", null);
        for (int slot : getConfig().getIntegerList("gui.items.decor.slots")) inv.setItem(slot, decor);

        inv.setItem(10, createItem(Material.valueOf(getConfig().getString("gui.items.leave.material")), getConfig().getString("gui.items.leave.name"), getConfig().getStringList("gui.items.leave.lore")));
        inv.setItem(16, createItem(Material.valueOf(getConfig().getString("gui.items.join.material")), getConfig().getString("gui.items.join.name"), getConfig().getStringList("gui.items.join.lore")));
        inv.setItem(12, createItem(Material.valueOf(getConfig().getString("gui.items.world.material")), getConfig().getString("gui.items.world.name"), List.of(getConfig().getStringList("gui.items.world.lore").get(0).replace("%world%", player.getWorld().getName()))));
        inv.setItem(13, createItem(Material.valueOf(getConfig().getString("gui.items.queue.material")), getConfig().getString("gui.items.queue.name"), List.of(getConfig().getStringList("gui.items.queue.lore").get(0).replace("%count%", String.valueOf(queue.size())))));
        inv.setItem(14, createItem(Material.valueOf(getConfig().getString("gui.items.ping.material")), getConfig().getString("gui.items.ping.name"), List.of(getConfig().getStringList("gui.items.ping.lore").get(0).replace("%ping%", String.valueOf(player.getPing())))));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(format(getConfig().getString("gui.title")))) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            int slot = e.getRawSlot();

            if (slot == 16) { // Nút Join
                if (queue.contains(p.getUniqueId())) {
                    p.sendMessage(format(getConfig().getString("messages.prefix") + getConfig().getString("messages.already-in-queue")));
                } else {
                    queue.add(p.getUniqueId());
                    p.sendMessage(format(getConfig().getString("messages.prefix") + getConfig().getString("messages.joined")));
                    p.closeInventory();
                    if (queue.size() >= 2) startCountdown();
                }
            } else if (slot == 10) { // Nút Leave
                if (queue.remove(p.getUniqueId())) {
                    p.sendMessage(format(getConfig().getString("messages.prefix") + getConfig().getString("messages.left")));
                    p.closeInventory();
                } else {
                    p.sendMessage(format(getConfig().getString("messages.prefix") + getConfig().getString("messages.not-in-queue")));
                }
            }
        }
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(format(name));
            if (lore != null) meta.setLore(lore.stream().map(this::format).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private class RTPQCommand implements CommandExecutor {
        public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
            if (!(s instanceof Player p)) return true;
            if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                s.sendMessage(format(getConfig().getString("messages.prefix") + getConfig().getString("messages.reload")));
                return true;
            }
            openMenu(p);
            return true;
        }
    }

    private void startCountdown() {
        final List<UUID> players = new ArrayList<>(queue);
        queue.clear();
        AtomicInteger timeLeft = new AtomicInteger(5); // Đếm ngược 5 giây

        Bukkit.getAsyncScheduler().runAtFixedRate(this, (task) -> {
            int current = timeLeft.getAndDecrement();
            for (UUID id : players) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage(format(getConfig().getString("messages.prefix") + 
                        getConfig().getString("messages.countdown-msg").replace("%time%", String.valueOf(current))));
                }
            }
            if (current <= 0) { 
                task.cancel(); 
                executeTeleport(players); 
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void executeTeleport(List<UUID> players) {
        String worldName = getConfig().getStringList("settings.allowed-worlds").get(0);
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        findSafeLocation(world, 0, players);
    }

    private void findSafeLocation(World world, int attempts, List<UUID> players) {
        if (attempts > 15) {
            for (UUID id : players) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.sendMessage(format(getConfig().getString("messages.prefix") + getConfig().getString("messages.no-safe-location")));
            }
            return;
        }

        int r = getConfig().getInt("settings.max-radius");
        int x = random.nextInt(r * 2) - r;
        int z = random.nextInt(r * 2) - r;

        // Folia Region Scheduler
        Bukkit.getRegionScheduler().execute(this, world, x, z, () -> {
            Block block = world.getHighestBlockAt(x, z);
            if (block.getType() == Material.LAVA || block.getType() == Material.WATER || block.getType() == Material.AIR) {
                findSafeLocation(world, attempts + 1, players);
                return;
            }
            Location loc = block.getLocation().add(0.5, 1, 0.5);
            for (UUID id : players) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.teleportAsync(loc).thenAccept(s -> 
                        p.sendTitle("", format(getConfig().getString("messages.teleport-subtitle")), 5, 30, 5));
                }
            }
        });
    }
}
