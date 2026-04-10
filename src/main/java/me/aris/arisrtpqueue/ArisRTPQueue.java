package me.aris.arisrtpqueue;

import me.aris.arisrtpqueue.commands.RTPCommand;
import me.aris.arisrtpqueue.listeners.GUIListener;
import me.aris.arisrtpqueue.manager.QueueManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class ArisRTPQueue extends JavaPlugin {
    private static ArisRTPQueue instance;
    private QueueManager queueManager;
    private FileConfiguration messageConfig;

    @Override
    public void onEnable() {
        instance = this;
        reloadPluginConfig();
        this.queueManager = new QueueManager();
        RTPCommand cmd = new RTPCommand();
        getCommand("rtpq").setExecutor(cmd);
        getCommand("rtpq").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
    }

    public void reloadPluginConfig() {
        saveDefaultConfig();
        reloadConfig();
        File msgFile = new File(getDataFolder(), "message.yml");
        if (!msgFile.exists()) saveResource("message.yml", false);
        messageConfig = YamlConfiguration.loadConfiguration(msgFile);
    }

    public static ArisRTPQueue getInstance() { return instance; }
    public QueueManager getQueueManager() { return queueManager; }
    public FileConfiguration getMessages() { return messageConfig; }
          }
