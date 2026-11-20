package dev.beaconplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Beacon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class BeaconPlus extends JavaPlugin {

    private final Map<String, ExtraEffect> effects = new LinkedHashMap<>();
    private NamespacedKey dataKey;
    private int maxExtra;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataKey = new NamespacedKey(this, "extras");
        loadEffects();
        getServer().getPluginManager().registerEvents(new BeaconListener(this), this);
    }

    public void loadEffects() {
        reloadConfig();
        effects.clear();
        maxExtra = Math.max(1, getConfig().getInt("max-extra-effects", 1));

        ConfigurationSection section = getConfig().getConfigurationSection("effects");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection e = section.getConfigurationSection(id);
            if (e == null) continue;

            String keyText = e.getString("effect", id).toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(keyText); // no namespace = minecraft:
            PotionEffectType type = key == null ? null : PotionEffectType.getByKey(key);
            if (type == null) {
                getLogger().warning("Skipping '" + id + "': unknown potion effect '" + keyText + "'");
                continue;
            }
            Material icon = Material.matchMaterial(e.getString("icon", "POTION"));
            if (icon == null || icon.isAir()) icon = Material.POTION;

            effects.put(id, new ExtraEffect(
                    id, type,
                    e.getString("name", id),
                    icon,
                    Math.max(0, e.getInt("amplifier", 0)),
                    Math.max(1, Math.min(4, e.getInt("min-tier", 1))),
                    Math.max(0, e.getInt("duration-seconds", 0))));
        }
        getLogger().info("Loaded " + effects.size() + " extra beacon effect(s).");
    }

    public List<ExtraEffect> effectList() { return new ArrayList<>(effects.values()); }
    public ExtraEffect effect(String id) { return effects.get(id); }
    public int maxExtra() { return maxExtra; }

    /** Reads a MiniMessage string from the messages: section. */
    public Component msg(String path, String fallback, Map<String, String> placeholders) {
        String raw = getConfig().getString("messages." + path, fallback);
        var resolvers = placeholders.entrySet().stream()
                .map(en -> Placeholder.unparsed(en.getKey(), en.getValue()))
                .toList();
        return MiniMessage.miniMessage().deserialize(raw,
                resolvers.toArray(new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]));
    }

    // ---- per-beacon storage (saved in the beacon block itself) ----

    public Set<String> selected(Beacon beacon) {
        String raw = beacon.getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        Set<String> out = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) out.addAll(Arrays.asList(raw.split(",")));
        return out;
    }

    public void save(Beacon beacon, Set<String> ids) {
        beacon.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, String.join(",", ids));
        beacon.update();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadEffects();
            sender.sendMessage("BeaconPlus reloaded: " + effects.size() + " effect(s).");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            effects.values().forEach(x -> sender.sendMessage(
                    x.id() + " -> " + x.type().getKey() + " (level " + (x.amplifier() + 1) + ", tier " + x.minTier() + "+)"));
            return true;
        }
        return false;
    }
}
