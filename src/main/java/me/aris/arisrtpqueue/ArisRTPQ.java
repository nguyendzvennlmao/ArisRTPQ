package me.aris.arisrtpqueue;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
    private final Map<UUID, BukkitTask> actionbarTasks = new HashMap<>();
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
                    if (queue.contains(player)) { playS(player, "settings.error"); sendCustomMsg(player, "messages.already-in-queue", null); }
                    else addToQueue(player);
                    return true;
                }
                case "leave" -> {
                    if (queue.contains(player)) { removeFromQueue(player); bS("settings.leave-sound"); sendCustomMsg(player, "messages.left-queue", null); }
                    else { playS(player, "settings.error"); sendCustomMsg(player, "messages.not-in-queue", null); }
                    return true;
                }
                case "reload" -> {
                    if (player.hasPermission("rtpq.admin")) { reloadConfig(); playS(player, "settings.gui-click"); sendCustomMsg(player, "messages.reload", null); }
                    return true;
                }
                case "world" -> {
                    if (player.hasPermission("rtpq.admin") && args.length > 1) {
                        String wN = args[1];
                        if (Bukkit.getWorld(wN) != null) { getConfig().set("settings.world-name", wN); saveConfig(); playS(player, "settings.gui-click"); sendCustomMsg(player, "messages.world-set", wN); }
                        else { playS(player, "settings.error"); sendCustomMsg(player, "messages.world-not-found", wN); }
                    }
                    return true;
                }
            }
        }
        openGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("join", "leave"));
            if (s.hasPermission("rtpq.admin")) suggestions.addAll(Arrays.asList("help", "reload", "world"));
            return suggestions.stream().filter(str -> str.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void openGUI(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, f(getConfig().getString("gui.title")));
        String cW = getConfig().getString("settings.world-name");
        sI(gui, "cancel", null); sI(gui, "world", cW); sI(gui, "queue", String.valueOf(queue.size())); sI(gui, "stats", null); sI(gui, "confirm", null);
        p.openInventory(gui); playS(p, "settings.gui-click");
    }

    private void sI(Inventory inv, String key, String ph) {
        int slot = getConfig().getInt("gui.items." + key + ".slot");
        Material m = Material.valueOf(getConfig().getString("gui.items." + key + ".material"));
        String name = getConfig().getString("gui.items." + key + ".name");
        List<String> lore = getConfig().getStringList("gui.items." + key + ".lore");
        List<String> fL = lore.stream().map(l -> {
            if (ph != null) l = l.replace("%world%", ph).replace("%count%", ph);
            return f(l);
        }).collect(Collectors.toList());
        inv.setItem(slot, cI(m, name, fL));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null || !e.getView().getTitle().equals(f(getConfig().getString("gui.title")))) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot == getConfig().getInt("gui.items.confirm.slot")) { if (!queue.contains(p)) { playS(p, "settings.gui-click"); addToQueue(p); p.closeInventory(); } }
        else if (slot == getConfig().getInt("gui.items.cancel.slot")) { playS(p, "settings.gui-click"); if (queue.contains(p)) { removeFromQueue(p); bS("settings.leave-sound"); sendCustomMsg(p, "messages.left-queue", null); } p.closeInventory(); }
    }

    private void addToQueue(Player p) {
        queue.add(p);
        sendCustomMsg(p, "messages.joined-queue", null);
        Bukkit.broadcastMessage(f(getConfig().getString("messages.prefix") + getConfig().getString("messages.broadcast-joined.text").replace("%player%", p.getName())));
        bS("settings.join-sound");
        BukkitTask task = new BukkitRunnable() { @Override public void run() { if (queue.contains(p)) { removeFromQueue(p); bS("settings.leave-sound"); sendCustomMsg(p, "messages.left-queue", null); } } }.runTaskLater(this, 300 * 20L);
        autoCancelTasks.put(p.getUniqueId(), task);
        if (queue.size() >= 2) { Player p1 = queue.remove(0); Player p2 = queue.remove(0); cT(p1); cT(p2); startC(p1, p2); }
        else startWD(p);
    }

    private void bS(String path) { try { Sound s = Sound.valueOf(getConfig().getString(path)); for (Player a : Bukkit.getOnlinePlayers()) a.playSound(a.getLocation(), s, 1.0f, 1.0f); } catch (Exception ignored) {} }
    private void playS(Player p, String path) { try { p.playSound(p.getLocation(), Sound.valueOf(getConfig().getString(path)), 1.0f, 1.0f); } catch (Exception ignored) {} }

    private void startWD(Player p) {
        BukkitTask t = new BukkitRunnable() { @Override public void run() { if (!queue.contains(p)) { this.cancel(); return; } p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(f(getConfig().getString("messages.waiting-others.text")))); } }.runTaskTimer(this, 0, 40L);
        actionbarTasks.put(p.getUniqueId(), t);
    }

    private void startC(Player p1, Player p2) {
        new BukkitRunnable() {
            int t = getConfig().getInt("settings.countdown-seconds");
            @Override public void run() {
                if (t <= 0) { doR(p1, p2); cancel(); return; }
                playS(p1, "settings.countdown-tick"); playS(p2, "settings.countdown-tick");
                String m = f(getConfig().getString("messages.countdown.text").replace("%time%", String.valueOf(t)));
                sendD(p1, m, "messages.countdown"); sendD(p2, m, "messages.countdown");
                t--;
            }
        }.runTaskTimer(this, 0, 20);
    }

    private void doR(Player p1, Player p2) {
        World w = Bukkit.getWorld(getConfig().getString("settings.world-name")); if (w == null) w = p1.getWorld();
        Random r = new Random(); int max = getConfig().getInt("settings.max-radius");
        int x = r.nextInt(max * 2) - max; int z = r.nextInt(max * 2) - max;
        Location loc = new Location(w, x, w.getHighestBlockYAt(x, z) + 1, z);
        p1.teleportAsync(loc); p2.teleportAsync(loc.clone().add(getConfig().getInt("settings.player-distance"), 0, 0));
        playS(p1, "settings.teleport-success"); playS(p2, "settings.teleport-success");
        sendCustomMsg(p1, "messages.success", null); sendCustomMsg(p2, "messages.success", null);
    }

    private void cT(Player p) { if (autoCancelTasks.containsKey(p.getUniqueId())) { autoCancelTasks.get(p.getUniqueId()).cancel(); autoCancelTasks.remove(p.getUniqueId()); } if (actionbarTasks.containsKey(p.getUniqueId())) { actionbarTasks.get(p.getUniqueId()).cancel(); actionbarTasks.remove(p.getUniqueId()); } }
    private void removeFromQueue(Player p) { queue.remove(p); cT(p); }

    private String f(String m) {
        if (m == null) return "";
        Matcher mt = hexPattern.matcher(m); StringBuilder sb = new StringBuilder(); int last = 0;
        while (mt.find()) { sb.append(ChatColor.translateAlternateColorCodes('&', m.substring(last, mt.start()))); sb.append(ChatColor.of(mt.group().substring(1))); last = mt.end(); }
        sb.append(ChatColor.translateAlternateColorCodes('&', m.substring(last))); return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private void sendCustomMsg(Player p, String pt, String ph) { String m = getConfig().getString(pt + ".text"); if (ph != null) m = m.replace("%world%", ph); sendD(p, f(getConfig().getString("messages.prefix") + m), pt); }
    private void sendD(Player p, String m, String pt) { if (getConfig().getBoolean(pt + ".chat", true)) p.sendMessage(m); if (getConfig().getBoolean(pt + ".actionbar", false)) p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(m)); }
    private ItemStack cI(Material m, String n, List<String> l) { ItemStack i = new ItemStack(m); ItemMeta mt = i.getItemMeta(); mt.setDisplayName(f(n)); mt.setLore(l); i.setItemMeta(mt); return i; }
    }
