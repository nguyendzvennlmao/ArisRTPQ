package me.aris.arisrtpqueue.manager;

import me.aris.arisrtpqueue.ArisRTPQueue;
import me.aris.arisrtpqueue.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueManager {
    private final List<UUID> queue = new ArrayList<>();
    private final Map<UUID, UUID> invites = new ConcurrentHashMap<>();

    public void add(Player player) {
        if (queue.contains(player.getUniqueId())) return;
        queue.add(player.getUniqueId());
        ColorUtils.send(player, "joined-queue");
        playSound(player, "join-queue");
        startActionbarTask(player);
        checkQueue();
    }

    public void remove(Player player) {
        if (queue.remove(player.getUniqueId())) {
            ColorUtils.send(player, "left-queue");
            playSound(player, "leave-queue");
        }
    }

    private void startActionbarTask(Player p) {
        p.getScheduler().runAtFixedRate(ArisRTPQueue.getInstance(), (task) -> {
            if (!p.isOnline() || !queue.contains(p.getUniqueId())) { task.cancel(); return; }
            int min = ArisRTPQueue.getInstance().getConfig().getInt("settings.min-players");
            int need = Math.max(0, min - queue.size());
            p.sendActionBar(Component.text(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("actionbar-waiting").replace("%need%", String.valueOf(need)))));
        }, null, 1L, 20L);
    }

    private void checkQueue() {
        int min = ArisRTPQueue.getInstance().getConfig().getInt("settings.min-players");
        if (queue.size() >= min) {
            List<UUID> match = new ArrayList<>(queue.subList(0, min));
            queue.removeAll(match);
            match.forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) startCountdown(p);
            });
        }
    }

    private void startCountdown(Player p) {
        AtomicInteger time = new AtomicInteger(ArisRTPQueue.getInstance().getConfig().getInt("settings.countdown-seconds"));
        p.getScheduler().runAtFixedRate(ArisRTPQueue.getInstance(), (task) -> {
            if (!p.isOnline()) { task.cancel(); return; }
            if (time.get() <= 0) { findSafeRTP(p); task.cancel(); return; }
            p.sendActionBar(Component.text(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("actionbar-teleporting-in").replace("%time%", String.valueOf(time.get())))));
            playSound(p, "countdown-tick");
            time.decrementAndGet();
        }, null, 1L, 20L);
    }

    private void findSafeRTP(Player p) {
        World w = Bukkit.getWorld(ArisRTPQueue.getInstance().getConfig().getString("settings.world"));
        int r = ArisRTPQueue.getInstance().getConfig().getInt("settings.rtp-range");
        for (int i = 0; i < 30; i++) {
            int x = ThreadLocalRandom.current().nextInt(-r, r);
            int z = ThreadLocalRandom.current().nextInt(-r, r);
            int y = w.getHighestBlockYAt(x, z);
            Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
            if (isSafe(loc)) {
                p.teleportAsync(loc).thenAccept(s -> { if (s) sendSuccess(p); });
                return;
            }
        }
        ColorUtils.send(p, "no-safe-location");
        findSafeRTP(p);
    }

    private boolean isSafe(Location l) {
        Material m = l.clone().subtract(0, 1, 0).getBlock().getType();
        return !m.isAir() && m.isSolid() && m != Material.LAVA && m != Material.WATER && l.getBlock().getType().isAir();
    }

    private void sendSuccess(Player p) {
        p.showTitle(Title.title(Component.text(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("success-title"))), Component.text(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("success-subtitle")))));
        p.sendMessage(ColorUtils.format(ArisRTPQueue.getInstance().getMessages().getString("prefix") + ArisRTPQueue.getInstance().getMessages().getString("success-chat")));
        playSound(p, "teleport-success");
    }

    public void addInvite(UUID target, UUID inviter) { invites.put(target, inviter); }
    public UUID getInviter(UUID target) { return invites.remove(target); }
    public void playSound(Player p, String k) {
        String s = ArisRTPQueue.getInstance().getConfig().getString("sounds." + k);
        if (s != null) p.playSound(p.getLocation(), Sound.valueOf(s), 1f, 1f);
    }
    public int getSize() { return queue.size(); }
                                                                     }
