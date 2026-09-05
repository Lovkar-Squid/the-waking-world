package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Where the mod's creatures turn up on their own, rarely, besides the dungeons that spawn them:
 * Stone Thralls walk the ruins and the empty hamlets by night; Ember Wraiths drift over the deserts
 * and the badlands under the stars; Drowned Keepers wait in the swamps and the rivers. The biome
 * modifiers ({@code data/wakingworld/neoforge/biome_modifier}) give them small weights; the rules
 * here say where a natural spawn may stand. Spawners, eggs and commands are not bound by them.
 */
public final class WakingSpawns {
    private WakingSpawns() {
    }

    public static final TagKey<Structure> RUINS = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "ruins"));

    public static void register(RegisterSpawnPlacementsEvent event) {
        event.register(WakingWorld.STONE_THRALL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WakingSpawns::thrall, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(WakingWorld.EMBER_WRAITH.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WakingSpawns::wraith, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(WakingWorld.DROWNED_KEEPER.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, WakingSpawns::keeper, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static boolean natural(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION;
    }

    /** Thralls: by night, in the dark, and only within a ruin or a hamlet - the dead of the old people keep to their walls. */
    private static <T extends Monster> boolean thrall(EntityType<T> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!natural(spawnType)) return true;
        if (!level.getLevel().isNight() || !Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random)) return false;
        StructureStart ruin = level.getLevel().structureManager().getStructureWithPieceAt(pos, RUINS);
        return ruin.isValid();
    }

    /** Wraiths: under the open night sky of the deserts and badlands (the biome list picks the lands), in the dark. */
    private static <T extends Monster> boolean wraith(EntityType<T> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!natural(spawnType)) return true;
        return level.getLevel().isNight() && level.canSeeSky(pos) && Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /** Keepers: in the water of swamps and rivers, by night, in the dark. */
    private static <T extends Monster> boolean keeper(EntityType<T> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (!natural(spawnType)) return true;
        // as the drowned do: water under and around it, dark, and not in peace (a sturdy floor is not asked for in the water)
        if (!level.getFluidState(pos.below()).is(FluidTags.WATER) || !level.getFluidState(pos).is(FluidTags.WATER)) return false;
        return level.getLevel().isNight() && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL && Monster.isDarkEnoughToSpawn(level, pos, random);
    }
}
