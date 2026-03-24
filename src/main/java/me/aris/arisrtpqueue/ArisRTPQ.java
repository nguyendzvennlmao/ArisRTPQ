package me.aris.arisrtpqueue;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArisRTPQ extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final List<Player> queue = new ArrayList<>();
    private final Map<UUID, BukkitTask> autoCancelTasks = new HashMap<>();
    private final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("rtpq").setExecutor(this);
        getCommand("rtpq").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "join" -> {
                    if (queue.contains(player)) {
                        sendCustomMsg(player, "messages.already-in-queue", null);
                    } else {
                        addToQueue(player);
                    }
                    return true;
                }
                case "leave" -> {
                    if (queue.contains(player)) {
                        removeFromQueue(player);
                        sendCustomMsg(player, "messages.left-queue", null);
                    } else {
                        sendCustomMsg(player, "messages.not-in-queue", null);
                    }
                    return true;
                }
                case "reload" -> {
                    if (player.hasPermission("rtpq.admin")) {
                        reloadConfig();
                        sendCustomMsg(player, "messages.reload", null);
                    }
                    return true;
                }
                case "help" -> {
                    getConfig().getStringList("messages.help").forEach(line -> player.sendMessage(format(line)));
                    return true;
                }
                case "world" -> {
                    if (player.hasPermission("rtpq.admin") && args.length > 1) {
                        String worldName = args[1];
                        if (Bukkit.getWorld(worldName) != null) {
                            getConfig().set("settings.world-name", worldName);
                            saveConfig();
                            sendCustomMsg(player, "messages.world-set", worldName);
                        } else {
                            sendCustomMsg(player, "messages.world-not-found", worldName);
                        }
                    }
                    return true;
                }
            }
        }
        openGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("join", "leave"));
            if (sender.hasPermission("rtpq.admin")) {
                suggestions.addAll(Arrays.asList("help", "reload", "world"));
            }
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("world") && sender.hasPermission("rtpq.admin")) {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, format(getConfig().getString("gui.title")));
        String currentWorld = getConfig().getString("settings.world-name");
        setItem(gui, "cancel", null);
        setItem(gui, "world", currentWorld);
        setItem(gui, "queue", String.valueOf(queue.size()));
        setItem(gui, "stats", null);
        setItem(gui, "confirm", null);
        player.openInventory(gui);
    }

    private void setItem(Inventory inv, String key, String placeholder) {
        int slot = getConfig().getInt("gui.items." + key + ".slot");
        Material mat = Material.valueOf(getConfig().getString("gui.items." + key + ".material"));
        String name = getConfig().getString("gui.items." + key + ".name");
        String lore = getConfig().getString("gui.items." + key + ".lore");
        if (placeholder != null) lore = lore.replace("%world%", placeholder).replace("%count%", placeholder);
        inv.setItem(slot, createItem(mat, name, lore));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null || !e.getView().getTitle().equals(format(getConfig().getString("gui.title")))) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot == getConfig().getInt("gui.items.confirm.slot")) {
            if (queue.contains(p)) return;
            addToQueue(p);
            p.closeInventory();
        } else if (slot == getConfig().getInt("gui.items.cancel.slot")) {
            if (queue.contains(p)) {
                removeFromQueue(p);
                sendCustomMsg(p, "messages.left-queue", null);
            }
            p.closeInventory();
        }
    }

    private void addToQueue(Player p) {
        queue.add(p);
        sendCustomMsg(p, "messages.joined-queue", null);
        String broadcastMsg = format(getConfig().getString("messages.prefix") + 
                getConfig().getString("messages.broadcast-joined.text").replace("%player%", p.getName()));
        Bukkit.broadcastMessage(broadcastMsg);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (queue.contains(p)) {
                    removeFromQueue(p);
                    sendCustomMsg(p, "messages.left-queue", null);
                }
            }
        }.runTaskLater(this, 300 * 20L);
        autoCancelTasks.put(p.getUniqueId(), task);

        if (queue.size() >= 2) {
            Player p1 = queue.remove(0); 
            Player p2 = queue.remove(0);
            cancelAutoTask(p1); cancelAutoTask(p2);
            startCountdown(p1, p2);
        } else {
            sendCustomMsg(p, "messages.waiting-others", null);
        }
    }

    private void cancelAutoTask(Player p) {
        if (autoCancelTasks.containsKey(p.getUniqueId())) {
            autoCancelTasks.get(p.getUniqueId()).cancel();
            autoCancelTasks.remove(p.getUniqueId());
        }
    }

    private void removeFromQueue(Player p) {
        queue.remove(p);
        cancelAutoTask(p);
    }

    private void startCountdown(Player p1, Player p2) {
        new BukkitRunnable() {
            int t = getConfig().getInt("settings.countdown-seconds");
            @Override
            public void run() {
                if (t <= 0) { doRTP(p1, p2); cancel(); return; }
                String m = format(getConfig().getString("messages.countdown.text").replace("%time%", String.valueOf(t)));
                sendDirect(p1, m, "messages.countdown"); 
                sendDirect(p2, m, "messages.countdown");
                t--;
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void doRTP(Player p1, Player p2) {
        World w = Bukkit.getWorld(getConfig().getString("settings.world-name"));
        if (w == null) w = p1.getWorld();
        Random r = new Random();
        int max = getConfig().getInt("settings.max-radius");
        int x = r.nextInt(max * 2) - max;
        int z = r.nextInt(max * 2) - max;
        Location loc = new Location(w, x, w.getHighestBlockYAt(x, z) + 1, z);
        p1.teleportAsync(loc); 
        p2.teleportAsync(loc.clone().add(getConfig().getInt("settings.player-distance"), 0, 0));
        sendCustomMsg(p1, "messages.success", null); 
        sendCustomMsg(p2, "messages.success", null);
    }

    private String format(String m) {
        if (m == null) return "";
        Matcher mt = hexPattern.matcher(m);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (mt.find()) {
            sb.append(ChatColor.translateAlternateColorCodes('&', m.substring(last, mt.start())));
            sb.append(ChatColor.of(mt.group().substring(1)));
            last = mt.end();
        }
        sb.append(ChatColor.translateAlternateColorCodes('&', m.substring(last)));
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private void sendCustomMsg(Player p, String pt, String placeholder) {
        String msg = getConfig().getString(pt + ".text");
        if (placeholder != null) msg = msg.replace("%world%", placeholder);
        String prefix = getConfig().getString("messages.prefix");
        sendDirect(p, format(prefix + msg), pt);
    }

    private void sendDirect(Player p, String m, String pt) {
        if (getConfig().getBoolean(pt + ".chat", true)) p.sendMessage(m);
        if (getConfig().getBoolean(pt + ".actionbar", false)) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(m));
        }
    }

    private ItemStack createItem(Material m, String n, String l) {
        ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta();
        mt.setDisplayName(format(n));
        if (l != null) mt.setLore(Collections.singletonList(format(l)));
        i.setItemMeta(mt); return i;
    }
  }
