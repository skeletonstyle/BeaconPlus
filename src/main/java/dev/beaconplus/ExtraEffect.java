package dev.beaconplus;

import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

/** One effect defined in config.yml. */
public record ExtraEffect(String id, PotionEffectType type, String name, Material icon,
                          int amplifier, int minTier, int durationSeconds) {
}
