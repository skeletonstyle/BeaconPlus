package dev.beaconplus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import com.destroystokyo.paper.event.block.BeaconEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BeaconListener implements Listener {

    private final BeaconPlus plugin;

    public BeaconListener(BeaconPlus plugin) { this.plugin = plugin; }

    /** Marks our menu inventory and remembers which beacon it edits. */
    private static final class Menu implements InventoryHolder {
        final Location beacon;
        Inventory inventory;
        Menu(Location beacon) { this.beacon = beacon; }
        @Override public Inventory getInventory() { return inventory; }
    }

    // Sneak + right-click a beacon with an empty hand -> open the extra effects menu
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        Player player = e.getPlayer();
        if (block == null || block.getType() != Material.BEACON) return;
        if (!player.isSneaking() || e.getItem() != null) return;
        if (!player.hasPermission("beaconplus.use")) return;
        if (!(block.getState() instanceof Beacon beacon)) return;

        e.setCancelled(true);
        List<ExtraEffect> list = plugin.effectList();
        int size = Math.min(54, Math.max(9, ((list.size() + 8) / 9) * 9));
        Menu menu = new Menu(block.getLocation());
        menu.inventory = Bukkit.createInventory(menu, size,
                plugin.msg("menu-title", "<dark_purple>Extra Beacon Effects", Map.of()));
        render(menu, beacon);
        player.openInventory(menu.inventory);
    }

    private void render(Menu menu, Beacon beacon) {
        Set<String> selected = plugin.selected(beacon);
        int tier = beacon.getTier();
        List<ExtraEffect> list = plugin.effectList();
        menu.inventory.clear();
        for (int i = 0; i < list.size() && i < menu.inventory.getSize(); i++) {
            ExtraEffect x = list.get(i);
            boolean on = selected.contains(x.id());
            boolean locked = tier < x.minTier();

            ItemStack item = new ItemStack(x.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(x.name(), on ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(plain("Level " + (x.amplifier() + 1), NamedTextColor.DARK_GRAY));
            lore.add(plain("Needs pyramid tier " + x.minTier(), locked ? NamedTextColor.RED : NamedTextColor.DARK_GRAY));
            lore.add(plain(on ? "Active - click to turn off" : "Click to turn on",
                    on ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            meta.lore(lore);
            if (on) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
            menu.inventory.setItem(i, item);
        }
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getInventory()) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        List<ExtraEffect> list = plugin.effectList();
        int slot = e.getSlot();
        if (slot < 0 || slot >= list.size()) return;

        if (!(menu.beacon.getBlock().getState() instanceof Beacon beacon)) {
            player.closeInventory();
            return;
        }
        ExtraEffect x = list.get(slot);
        Set<String> selected = plugin.selected(beacon);

        if (!selected.remove(x.id())) {
            if (beacon.getTier() < x.minTier()) {
                player.sendMessage(plugin.msg("tier-too-low", "<red>This beacon pyramid is too small for that effect.", Map.of()));
                return;
            }
            if (selected.size() >= plugin.maxExtra()) {
                player.sendMessage(plugin.msg("max-reached", "<red>Max <max> extra effect(s).",
                        Map.of("max", String.valueOf(plugin.maxExtra()))));
                return;
            }
            selected.add(x.id());
        }
        plugin.save(beacon, selected);
        render(menu, beacon);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // Piggyback on the beacon's own effect cycle: it already handles range, tier, power, etc.
    @EventHandler
    public void onBeaconEffect(BeaconEffectEvent e) {
        if (!e.isPrimary()) return; // once per player per cycle
        if (!(e.getBlock().getState() instanceof Beacon beacon)) return;

        Set<String> selected = plugin.selected(beacon);
        if (selected.isEmpty()) return;
        int tier = beacon.getTier();
        PotionEffect base = e.getEffect();

        for (String id : selected) {
            ExtraEffect x = plugin.effect(id);
            if (x == null || tier < x.minTier()) continue;
            int duration = x.durationSeconds() > 0 ? x.durationSeconds() * 20 : base.getDuration();
            e.getPlayer().addPotionEffect(new PotionEffect(x.type(), duration, x.amplifier(), true, true, true));
        }
    }
}
