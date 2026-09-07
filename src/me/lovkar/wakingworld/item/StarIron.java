package me.lovkar.wakingworld.item;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The metal the sky brings, and what can be made of it.
 *
 * <p>Star iron sits between diamond and netherite: a little tougher than diamond, no faster, and it
 * cannot burn - it fell through the atmosphere to get here, so a lava pool is not going to trouble
 * it. It is deliberately NOT better than netherite at everything. What it has instead is the one
 * thing netherite does not: a full suit of it turns aside the sky itself. A meteor's blast, a lava
 * bomb, the debris a tornado is carrying, the ground opening underfoot - the set halves all of it,
 * and that is worth more in this mod than another point of armour.</p>
 *
 * <p>It is also the only tier here that has to be gone and found rather than dug for. There is no
 * star iron ore: there are craters, and somebody has to walk to one.</p>
 */
public final class StarIron {
    private StarIron() {
    }

    /** Between diamond and netherite, and it does not burn. */
    public static final Tier TIER = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1_900, 8.5F, 3.5F, 18,
            () -> Ingredient.of(WakingItems.STAR_IRON.get()));

    public static final ResourceKey<ArmorMaterial> ARMOUR_KEY =
            ResourceKey.create(Registries.ARMOR_MATERIAL, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "star_iron"));

    public static final DeferredRegister<ArmorMaterial> ARMOUR_MATERIALS =
            DeferredRegister.create(BuiltInRegistries.ARMOR_MATERIAL, WakingWorld.MODID);

    public static final net.neoforged.neoforge.registries.DeferredHolder<ArmorMaterial, ArmorMaterial> ARMOUR =
            ARMOUR_MATERIALS.register("star_iron", () -> {
        Map<ArmorItem.Type, Integer> defence = new EnumMap<>(ArmorItem.Type.class);
        defence.put(ArmorItem.Type.BOOTS, 3);
        defence.put(ArmorItem.Type.LEGGINGS, 6);
        defence.put(ArmorItem.Type.CHESTPLATE, 8);
        defence.put(ArmorItem.Type.HELMET, 3);
        defence.put(ArmorItem.Type.BODY, 11);
        return new ArmorMaterial(defence, 18, SoundEvents.ARMOR_EQUIP_NETHERITE,
                () -> Ingredient.of(WakingItems.STAR_IRON.get()),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "star_iron"))),
                2.5F, 0.05F);
    });

    /**
     * The holder the items need. It is the DeferredHolder itself rather than a lookup: ArmorItem
     * only stores the holder and dereferences it later, so this cannot depend on whether items or
     * armour materials happen to be registered first.
     */
    public static Holder<ArmorMaterial> armour() {
        return ARMOUR;
    }

    /** How much of a cataclysm's damage a full suit turns aside. */
    public static final float SHIELDED = 0.5F;

    /**
     * The share of the incoming damage that gets through, for somebody wearing however much of the
     * suit they are wearing. A full set halves it; a single piece is worth an eighth of that.
     */
    public static float sheltered(net.minecraft.world.entity.LivingEntity who) {
        int worn = 0;
        for (net.minecraft.world.item.ItemStack stack : who.getArmorSlots()) {
            if (stack.getItem() instanceof ArmorItem a && a.getMaterial().is(ARMOUR_KEY)) worn++;
        }
        if (worn == 0) return 1.0F;
        return 1.0F - SHIELDED * (worn / 4.0F);
    }

    /**
     * The damage kinds a suit of star iron is actually for: what falls out of the sky, what the
     * ground does when it opens, and the heat that comes with both. A sword is not on this list -
     * the metal is armour against the world, not against people.
     */
    private static boolean fromTheSky(net.minecraft.world.damagesource.DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
                || source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                || source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE)
                || source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE);
    }

    /** Hooked on the game bus: the suit is checked once, where every damage path already passes. */
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!fromTheSky(event.getSource())) return;
        float share = sheltered(event.getEntity());
        if (share < 1.0F) event.setAmount(event.getAmount() * share);
    }

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        ARMOUR_MATERIALS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(StarIron::onHurt);
    }
}
