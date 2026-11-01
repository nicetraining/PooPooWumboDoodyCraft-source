package com.fartypoopy.sexmod.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Original gu: the armour of Jenny, Ellie, Bia and Luna lives in their own inventory, so vanilla armour and protection
 * enchantments never apply to them. Instead, once the damage is final (LivingDamageEvent), the 1.12 armour formula runs
 * with gu's own table (diamond toughness 3, unknown materials 3 armour), then each piece's protection enchantments take
 * their share one after another, and thorns hits back (15 % per level, 1-5, at most 4 in total).
 */
public final class GirlArmour {
    private GirlArmour() {
    }

    /** The girls whose armour the original handled itself (the e2 girls). */
    public static boolean applies(CompanionEntity girl) {
        CompanionKind kind = girl.kind();
        return kind == CompanionKind.JENNY || kind == CompanionKind.ELLIE || kind == CompanionKind.BIA || kind == CompanionKind.LUNA;
    }

    public static float apply(CompanionEntity girl, DamageSource source, float amount) {
        List<ItemStack> worn = new ArrayList<>();
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = girl.getItemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem) {
                worn.add(stack);
            }
        }
        if (worn.isEmpty()) {
            return amount;
        }
        int armour = 0;
        int toughness = 0;
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            for (ItemStack stack : worn) {
                ArmorItem item = (ArmorItem) stack.getItem();
                int[] values = values(item.getEquipmentSlot(), item.getMaterial());
                armour += values[0];
                toughness += values[1];
            }
        }
        float damage = amount;
        damage *= 1.0F - Math.min(20.0F, Math.max(armour / 5.0F, armour - 4.0F * damage / (toughness + 8.0F))) / 25.0F;
        float thorns = 0.0F;
        var random = girl.getRandom();
        for (ItemStack stack : worn) {
            damage -= level(girl, stack, Enchantments.PROTECTION) * 0.04F * damage;
            int thornLevel = level(girl, stack, Enchantments.THORNS);
            thorns += random.nextFloat() < 0.15F * thornLevel ? random.nextFloat() * 4.0F + 1.0F : 0.0F;
            thorns = Math.min(4.0F, thorns);
            if (source.is(DamageTypeTags.IS_FIRE)) {
                damage -= level(girl, stack, Enchantments.FIRE_PROTECTION) * 0.08F * damage;
            }
            if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                damage -= level(girl, stack, Enchantments.BLAST_PROTECTION) * 0.08F * damage;
            }
            if (source.is(DamageTypeTags.IS_FALL)) {
                damage -= level(girl, stack, Enchantments.FEATHER_FALLING) * 0.12F * damage;
            }
            if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                damage -= level(girl, stack, Enchantments.PROJECTILE_PROTECTION) * 0.08F * damage;
            }
        }
        if (thorns > 0.0F && source.getEntity() != null) {
            source.getEntity().hurt(girl.damageSources().thorns(girl), thorns);
        }
        return damage;
    }

    private static int level(CompanionEntity girl, ItemStack stack, ResourceKey<Enchantment> key) {
        var registry = girl.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(key).map(stack::getEnchantmentLevel).orElse(0);
    }

    /** gu's table: {armour, toughness} per slot and material; anything else counts 3 armour, 0 toughness. */
    private static int[] values(EquipmentSlot slot, Holder<ArmorMaterial> material) {
        int column = material.is(ArmorMaterials.LEATHER) ? 0 : material.is(ArmorMaterials.GOLD) ? 1
                : material.is(ArmorMaterials.CHAIN) ? 2 : material.is(ArmorMaterials.IRON) ? 3
                : material.is(ArmorMaterials.DIAMOND) ? 4 : -1;
        if (column < 0) {
            return new int[] {3, 0};
        }
        int[] armour = switch (slot) {
            case HEAD -> new int[] {1, 2, 2, 2, 3};
            case CHEST -> new int[] {3, 5, 5, 6, 8};
            case LEGS -> new int[] {2, 3, 4, 5, 6};
            default -> new int[] {1, 1, 1, 2, 3};
        };
        return new int[] {armour[column], column == 4 ? 3 : 0};
    }
}
