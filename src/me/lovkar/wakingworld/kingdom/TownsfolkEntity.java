package me.lovkar.wakingworld.kingdom;

import me.lovkar.wakingworld.item.WakingItems;
import me.lovkar.wakingworld.story.Letters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

/**
 * The people of the kingdom: traders with the vanilla trading screen and wares of this mod's world.
 * The surveyor sells maps to the shrines and the vaults; the relic-monger deals in embers, runes
 * and letters at a price; the smith, the provisioner, the chandler and the scribe keep a town
 * running. They keep to their stalls and houses, run from the dead, and turn their backs on anyone
 * the kingdom is angry with.
 */
public class TownsfolkEntity extends AbstractVillager {
    public static final int SURVEYOR = 0, RELIC_MONGER = 1, SMITH = 2, PROVISIONER = 3, CHANDLER = 4, SCRIBE = 5;
    public static final String[] PROFESSIONS = {"surveyor", "relic_monger", "smith", "provisioner", "chandler", "scribe"};
    private static final EntityDataAccessor<Integer> DATA_PROFESSION = SynchedEntityData.defineId(TownsfolkEntity.class, EntityDataSerializers.INT);

    private BlockPos center = BlockPos.ZERO;

    public TownsfolkEntity(EntityType<? extends TownsfolkEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.MAX_HEALTH, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFESSION, PROVISIONER);
    }

    public int profession() {
        return entityData.get(DATA_PROFESSION);
    }

    public BlockPos center() {
        return center;
    }

    public void assign(BlockPos kingdomCenter, BlockPos post, int profession) {
        this.center = kingdomCenter;
        entityData.set(DATA_PROFESSION, profession);
        restrictTo(post, 10);
        equipForTrade(profession);
    }

    /** The tool of the trade in the hand: the surveyor's compass, the smith's iron, the monger's ember, the provisioner's loaf, the chandler's candle, the scribe's book. */
    private void equipForTrade(int profession) {
        ItemStack tool = switch (Math.floorMod(profession, PROFESSIONS.length)) {
            case SURVEYOR -> new ItemStack(Items.COMPASS);
            case RELIC_MONGER -> new ItemStack(me.lovkar.wakingworld.item.WakingItems.SLEEPERS_EMBER.get());
            case SMITH -> new ItemStack(Items.IRON_INGOT);
            case PROVISIONER -> new ItemStack(Items.BREAD);
            case CHANDLER -> new ItemStack(Items.CANDLE);
            default -> new ItemStack(Items.WRITABLE_BOOK);
        };
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) setDropChance(slot, 0.0F);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 0.6));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zombie.class, 8.0F, 0.5, 0.6));
        goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(4, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isAlive() && !isTrading() && !isBaby() && !stack.is(Items.VILLAGER_SPAWN_EGG)) {
            if (hand == InteractionHand.MAIN_HAND) player.awardStat(Stats.TALKED_TO_VILLAGER);
            if (level() instanceof ServerLevel server) {
                if (KingdomData.get(server).isAngry(server, center, player.getUUID())) {
                    setUnhappyCounter(40);
                    playSound(me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_NO.get(), getSoundVolume(), getVoicePitch());
                    player.displayClientMessage(Component.translatable("entity.wakingworld.townsfolk.refuse"), true);
                    return InteractionResult.CONSUME;
                }
                if (getOffers().isEmpty()) return InteractionResult.CONSUME;
                playSound(me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_GREET.get(), getSoundVolume(), getVoicePitch());
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) me.lovkar.wakingworld.network.WakingNet.openTrade(sp, this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** True while this trader is being written to NBT (chunk unload, world save). */
    private boolean saving;

    /**
     * The wares are made up on the first trade, never while the trader is being saved - neither by the
     * structure that spawns it into its chunk nor by the chunk unloading or the world saving: the
     * surveyor's maps search the world for structures, and a search on the server thread in the middle
     * of a save held the game for 42 seconds once (a whole kingdom's townsfolk saved at once).
     */
    @Override
    public MerchantOffers getOffers() {
        if (!level().isClientSide && offers == null && (saving || !((net.neoforged.neoforge.common.extensions.IEntityExtension) this).isAddedToLevel())) return new MerchantOffers();
        return super.getOffers();
    }

    @Override
    protected void updateTrades() {
        MerchantOffers offers = getOffers();
        switch (profession()) {
            case SURVEYOR -> {
                offers.add(map(Letters.SHRINES, "item.wakingworld.map.shrine", 9));
                offers.add(map(Letters.VAULTS, "item.wakingworld.map.vault", 12));
                offers.add(map(Letters.KINGDOMS, "item.wakingworld.map.kingdom", 16));
                offers.add(new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.COMPASS), 8, 4, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.MAP), 12, 2, 0.05F));
            }
            case RELIC_MONGER -> {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 26), new ItemStack(WakingItems.SLEEPERS_EMBER.get()), 1, 20, 0.2F));
                int[] runes = shuffledRunes();
                for (int i = 0; i < 2; i++) offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 18), new ItemStack(WakingItems.runes().get(runes[i]).get()), 1, 15, 0.2F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(WakingItems.DEAD_LETTER.get()), 3, 5, 0.1F));
                offers.add(new MerchantOffer(new ItemCost(Items.AMETHYST_SHARD, 6), new ItemStack(Items.EMERALD), 8, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(WakingItems.COLOSSUS_HEART.get()), new ItemStack(Items.EMERALD, 40), 2, 30, 0.05F));
            }
            case SMITH -> {
                offers.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT, 4), new ItemStack(Items.EMERALD), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 7), new ItemStack(Items.IRON_SWORD), 6, 5, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 9), new ItemStack(Items.IRON_CHESTPLATE), 4, 8, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.SHIELD), 6, 4, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 24), new ItemStack(Items.DIAMOND_PICKAXE), 2, 15, 0.1F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.CROSSBOW), 3, 8, 0.05F));
            }
            case PROVISIONER -> {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 5), 16, 1, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.COOKED_BEEF, 4), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.ARROW, 12), 12, 1, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.GOLDEN_CARROT, 4), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.GOLDEN_APPLE), 3, 8, 0.1F));
            }
            case CHANDLER -> {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.TORCH, 12), 16, 1, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.LANTERN, 2), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.SOUL_LANTERN), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.CANDLE, 4), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.GLOW_BERRIES, 8), 8, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.HONEYCOMB, 6), new ItemStack(Items.EMERALD), 8, 2, 0.05F));
            }
            default -> {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.PAPER, 8), 16, 1, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BOOK, 2), 12, 2, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(WakingItems.ALMANAC.get()), 4, 3, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(WakingItems.DEAD_LETTER.get()), 2, 6, 0.1F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(Items.NAME_TAG), 3, 10, 0.05F));
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 30), enchantedBook(), 1, 20, 0.2F));
            }
        }
    }

    private int[] shuffledRunes() {
        int[] a = {0, 1, 2, 3, 4, 5};
        for (int i = a.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return a;
    }

    private ItemStack enchantedBook() {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var reg = level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var ench = reg.getHolder(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
        ench.ifPresent(h -> {
            net.minecraft.world.item.enchantment.ItemEnchantments.Mutable m = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            m.set(h, 3);
            book.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        });
        return book;
    }

    /**
     * A map to the nearest structure of a tag, the way the cartographer sells them. The search skips
     * structures a map has already pointed at; for the map to the next kingdom the town this surveyor
     * stands in is marked as known first, so the map never points home.
     */
    private MerchantOffer map(net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag, String nameKey, int price) {
        ItemStack map = new ItemStack(Items.MAP);
        if (level() instanceof ServerLevel server) {
            if (tag == Letters.KINGDOMS) {
                net.minecraft.world.level.levelgen.structure.StructureStart home = server.structureManager().getStructureWithPieceAt(blockPosition(), Letters.KINGDOMS);
                if (home.isValid()) while (home.canBeReferenced()) server.structureManager().addReference(home);
            }
            BlockPos pos = server.findNearestMapStructure(tag, blockPosition(), tag == Letters.KINGDOMS ? 8 : 20, true); // placement cells; each costs a site test
            if (pos != null) {
                map = MapItem.create(server, pos.getX(), pos.getZ(), (byte) 2, true, true);
                MapItem.renderBiomePreviewMap(server, map);
                MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecorationTypes.RED_X);
                map.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
            }
        }
        return new MerchantOffer(new ItemCost(Items.EMERALD, price), Optional.of(new ItemCost(Items.COMPASS)), map, 2, 10, 0.2F);
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
        if (offer.shouldRewardExp()) {
            int xp = 3 + random.nextInt(4);
            level().addFreshEntity(new ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), xp));
        }
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean did = super.hurt(source, amount);
        if (did && source.getEntity() instanceof Player player && level() instanceof ServerLevel server && !player.isCreative()) {
            Kingdoms.offend(server, center, player, KingdomData.ANGER_TICKS, "townsfolk");
        }
        return did;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (getMainHandItem().isEmpty()) equipForTrade(tag.getInt("Profession"));
        if (tag.contains("Profession")) entityData.set(DATA_PROFESSION, tag.getInt("Profession"));
        if (tag.contains("Center")) NbtUtils.readBlockPos(tag, "Center").ifPresent(p -> center = p);
        if (tag.contains("Post")) NbtUtils.readBlockPos(tag, "Post").ifPresent(p -> restrictTo(p, 10));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        saving = true;
        try {
            super.addAdditionalSaveData(tag); // writes the offers only if they exist
        } finally {
            saving = false;
        }
        tag.putInt("Profession", profession());
        tag.put("Center", NbtUtils.writeBlockPos(center));
        if (hasRestriction()) tag.put("Post", NbtUtils.writeBlockPos(getRestrictCenter()));
    }

    @Override
    public Component getName() {
        if (hasCustomName()) return super.getName();
        return Component.translatable("entity.wakingworld.townsfolk." + PROFESSIONS[Math.floorMod(profession(), PROFESSIONS.length)]);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** A customer who walked off, logged out or died is no longer being served. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Player customer = getTradingPlayer();
        if (customer != null && tickCount % 20 == 0 && (!customer.isAlive() || customer.isRemoved() || distanceToSqr(customer) > 100)) setTradingPlayer(null);
    }

    // ---- its voice (tools/sfx/mobs.py) ----

    @Override
    protected SoundEvent getAmbientSound() {
        return isTrading() ? null : me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_DEATH.get();
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean gotTrade) {
        return gotTrade ? me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_YES.get() : me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_NO.get();
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return me.lovkar.wakingworld.WakingSounds.TOWNSFOLK_YES.get();
    }
}
