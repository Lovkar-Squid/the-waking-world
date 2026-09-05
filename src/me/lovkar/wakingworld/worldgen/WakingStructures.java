package me.lovkar.wakingworld.worldgen;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's structures: the Titan's arena in the End, the six shrines, the vaults, the ruins and hamlets. */
public final class WakingStructures {
    private WakingStructures() {
    }

    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, WakingWorld.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, WakingWorld.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<TitanArenaStructure>> TITAN_ARENA =
            TYPES.register("titan_arena", () -> typeOf());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> TITAN_ARENA_PIECE =
            PIECES.register("titan_arena", () -> (StructurePieceType) (context, tag) -> new TitanArenaPiece(tag));
    public static final DeferredHolder<StructureType<?>, StructureType<ShrineStructure>> SHRINE =
            TYPES.register("shrine", () -> shrineType());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> SHRINE_PIECE =
            PIECES.register("shrine", () -> (StructurePieceType) (context, tag) -> new ShrinePiece(tag));
    public static final DeferredHolder<StructureType<?>, StructureType<VaultStructure>> VAULT =
            TYPES.register("vault", () -> vaultType());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> VAULT_PIECE =
            PIECES.register("vault", () -> (StructurePieceType) (context, tag) -> new VaultPiece(tag));
    public static final DeferredHolder<StructureType<?>, StructureType<RuinStructure>> RUIN =
            TYPES.register("ruin", () -> ruinType());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RUIN_PIECE =
            PIECES.register("ruin", () -> (StructurePieceType) (context, tag) -> new RuinPiece(tag));
    public static final DeferredHolder<StructureType<?>, StructureType<DungeonStructure>> DUNGEON =
            TYPES.register("dungeon", () -> dungeonType());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> DUNGEON_PIECE =
            PIECES.register("dungeon", () -> (StructurePieceType) (context, tag) -> "forge".equals(tag.getString("Kind")) ? new ForgePiece(tag) : new CisternPiece(tag));
    public static final DeferredHolder<StructureType<?>, StructureType<ReliquaryStructure>> RELIQUARY =
            TYPES.register("void_reliquary", () -> reliquaryType());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> RELIQUARY_PIECE =
            PIECES.register("void_reliquary", () -> (StructurePieceType) (context, tag) -> new ReliquaryPiece(tag));
    public static final DeferredHolder<StructureType<?>, StructureType<me.lovkar.wakingworld.kingdom.KingdomStructure>> KINGDOM =
            TYPES.register("kingdom", () -> kingdomType());
    public static final DeferredHolder<StructurePieceType, StructurePieceType> KINGDOM_WALL_PIECE =
            PIECES.register("kingdom_wall", () -> (StructurePieceType) (context, tag) -> new me.lovkar.wakingworld.kingdom.KingdomWallPiece(tag));
    public static final DeferredHolder<StructurePieceType, StructurePieceType> KEEP_PIECE =
            PIECES.register("keep", () -> (StructurePieceType) (context, tag) -> new me.lovkar.wakingworld.kingdom.KeepPiece(tag));

    /** The structure key and the tag the Key of the Titan looks for. */
    public static final ResourceKey<Structure> TITAN_ARENA_KEY = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "titan_arena"));
    public static final TagKey<Structure> TITAN_ARENA_TAG = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "titan_arena"));

    private static StructureType<TitanArenaStructure> typeOf() {
        return () -> TitanArenaStructure.CODEC;
    }

    private static StructureType<ShrineStructure> shrineType() {
        return () -> ShrineStructure.CODEC;
    }

    private static StructureType<VaultStructure> vaultType() {
        return () -> VaultStructure.CODEC;
    }

    private static StructureType<RuinStructure> ruinType() {
        return () -> RuinStructure.CODEC;
    }

    private static StructureType<DungeonStructure> dungeonType() {
        return () -> DungeonStructure.CODEC;
    }

    private static StructureType<ReliquaryStructure> reliquaryType() {
        return () -> ReliquaryStructure.CODEC;
    }

    private static StructureType<me.lovkar.wakingworld.kingdom.KingdomStructure> kingdomType() {
        return () -> me.lovkar.wakingworld.kingdom.KingdomStructure.CODEC;
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }
}
