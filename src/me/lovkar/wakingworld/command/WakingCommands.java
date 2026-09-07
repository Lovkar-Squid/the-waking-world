package me.lovkar.wakingworld.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /wakingworld summon [variant] [height] [instant] - a colossus rises out of the ground in front of the caller
 * (6 s of awakening; "instant" skips it).
 * /wakingworld kill - removes every colossus in the caller's dimension.
 * Development commands; the real giants will come out of the ground at their shrines.
 */
public final class WakingCommands {
    private WakingCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("wakingworld")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("summon")
                        .executes(ctx -> summon(ctx, "terrain", ColossusEntity.DEFAULT_HEIGHT, false))
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    List<String> names = new ArrayList<>(Palette.presetNames());
                                    names.add(0, "terrain");
                                    return SharedSuggestionProvider.suggest(names, builder);
                                })
                                .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "variant"), ColossusEntity.DEFAULT_HEIGHT, false))
                                .then(Commands.argument("height", IntegerArgumentType.integer(8, 96))
                                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "variant"),
                                                IntegerArgumentType.getInteger(ctx, "height"), false))
                                        .then(Commands.literal("instant")
                                                .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "variant"),
                                                        IntegerArgumentType.getInteger(ctx, "height"), true))))))
                .then(Commands.literal("kill").executes(WakingCommands::killAll))
                .then(Commands.literal("meteor").executes(ctx -> meteor(ctx, null, 2))
                        .then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> meteor(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"), 2))
                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 3))
                                        .executes(ctx -> meteor(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"),
                                                IntegerArgumentType.getInteger(ctx, "size"))))))
                .then(Commands.literal("shower").executes(WakingCommands::shower))
                .then(Commands.literal("lands").executes(WakingCommands::lands)
                        .then(Commands.literal("name").executes(ctx -> nameLand(ctx, null))
                                .then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                        .executes(ctx -> nameLand(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"))))))
                .then(Commands.literal("tornado").executes(ctx -> tornado(ctx, null, 0))
                        .then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> tornado(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"), 0))
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 1200))
                                        .executes(ctx -> tornado(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("earthquake").executes(ctx -> earthquake(ctx, null))
                        .then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> earthquake(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at")))))
                .then(Commands.literal("bloodmoon")
                        .then(Commands.literal("on").executes(ctx -> bloodMoon(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> bloodMoon(ctx, false)))
                        .then(Commands.literal("wave").executes(ctx -> bloodWave(ctx, 6))
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 40))
                                        .executes(ctx -> bloodWave(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("volcano").executes(ctx -> volcano(ctx, null, 0, 0))
                        .then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> volcano(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"), 0, 0))
                                .then(Commands.argument("height", com.mojang.brigadier.arguments.IntegerArgumentType.integer(6, 120))
                                        .executes(ctx -> volcano(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "height"), 0))
                                        .then(Commands.argument("foot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(6, 48))
                                                .executes(ctx -> volcano(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "height"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "foot")))
                                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 600))
                                                        .executes(ctx -> volcano(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getSpawnablePos(ctx, "at"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "height"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "foot"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds"))))))))
                .then(Commands.literal("restore").executes(WakingCommands::restore))
                .then(Commands.literal("target").then(Commands.argument("who", net.minecraft.commands.arguments.EntityArgument.entity())
                        .executes(ctx -> target(ctx, net.minecraft.commands.arguments.EntityArgument.getEntity(ctx, "who")))))
                .then(Commands.literal("snapshot").then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> snapshot(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))
                .then(Commands.literal("diff").executes(WakingCommands::diff))
                .then(Commands.literal("site").executes(ctx -> site(ctx, 70, 260))
                        .then(Commands.argument("maxOut", IntegerArgumentType.integer(16, 2000))
                                .executes(ctx -> site(ctx, 0, IntegerArgumentType.getInteger(ctx, "maxOut")))))
                .then(Commands.literal("cine").then(Commands.argument("scene", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"shrine", "rite", "fight", "kingdom", "titan", "all",
                                "lands", "tornado", "earthquake", "volcano", "meteor", "bloodmoon", "cataclysms", "stop"}, b))
                        .executes(ctx -> cine(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "scene"), me.lovkar.wakingworld.story.Cinematics.DEFAULT_RENDER_DISTANCE))
                        .then(Commands.argument("renderDistance", IntegerArgumentType.integer(4, 24))
                                .executes(ctx -> cine(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "scene"), IntegerArgumentType.getInteger(ctx, "renderDistance"))))))
                .then(Commands.literal("letter").executes(WakingCommands::letter)
                        .then(Commands.literal("gemini").executes(WakingCommands::letterGemini)))
                .then(Commands.literal("dump").then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> dump(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))
                .then(Commands.literal("rite").then(Commands.argument("altar", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .executes(ctx -> rite(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "altar")))))
                .then(Commands.literal("terrain").then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .executes(ctx -> terrain(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "at")))))
                .then(Commands.literal("kingdomscan").then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .then(Commands.argument("cells", IntegerArgumentType.integer(1, 40))
                                .executes(ctx -> kingdomScan(ctx, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(ctx, "at"), IntegerArgumentType.getInteger(ctx, "cells")))))));
    }

    /** Debug: tries the kingdom's site test on a grid of would-be cells round a point and counts why they fail. */
    private static int kingdomScan(CommandContext<CommandSourceStack> ctx, BlockPos at, int cells) {
        ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.structure.Structure kingdom = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(me.lovkar.wakingworld.WakingWorld.MODID, "kingdom"));
        java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes = kingdom == null ? b -> true : kingdom.biomes()::contains;
        int[] counts = new int[6];
        long t0 = System.nanoTime();
        int step = 640; // a placement cell at spacing 40
        for (int i = -cells; i <= cells; i++) {
            for (int j = -cells; j <= cells; j++) {
                int x = at.getX() + i * step + 8, z = at.getZ() + j * step + 8;
                net.minecraft.world.level.levelgen.structure.Structure.GenerationContext gc = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                        level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(), level.getServer().getStructureManager(),
                        level.getSeed(), new net.minecraft.world.level.ChunkPos(new BlockPos(x, 64, z)), level, biomes);
                int[] why = new int[1];
                int y = me.lovkar.wakingworld.kingdom.KingdomStructure.kingdomSite(gc, x, z, why);
                counts[y == Integer.MIN_VALUE ? why[0] : 0]++;
            }
        }
        int n = (2 * cells + 1) * (2 * cells + 1);
        String summary = String.format("%d cells in %.1f s: pass %d, biome %d, middle wet %d, wet %d, uneven %d, real ground %d", n, (System.nanoTime() - t0) / 1e9, counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]);
        me.lovkar.wakingworld.WakingWorld.LOGGER.info("kingdomscan {}: {}", at.toShortString(), summary);
        ctx.getSource().sendSuccess(() -> Component.literal(summary), true);
        return 1;
    }

    /** Debug: the quick (preliminary) surface against the real one on a grid round a point, and how long each costs. */
    private static int terrain(CommandContext<CommandSourceStack> ctx, BlockPos at) {
        ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.structure.Structure.GenerationContext gc = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(), level.getServer().getStructureManager(),
                level.getSeed(), new net.minecraft.world.level.ChunkPos(at), level, b -> true);
        StringBuilder out = new StringBuilder("\n");
        int worst = 0, worstF = 0, n = 0;
        long tq = 0, tf = 0, tr = 0;
        for (int dx = -48; dx <= 48; dx += 12) {
            for (int dz = -48; dz <= 48; dz += 12) {
                int x = at.getX() + dx, z = at.getZ() + dz;
                long t0 = System.nanoTime();
                int q = me.lovkar.wakingworld.worldgen.Terrain.quickSurface(gc, x, z);
                long t1 = System.nanoTime();
                int fsurf = me.lovkar.wakingworld.worldgen.Terrain.fastSurface(gc, x, z);
                long t2 = System.nanoTime();
                int r = gen.getFirstOccupiedHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
                long t3 = System.nanoTime();
                tq += t1 - t0;
                tf += t2 - t1;
                tr += t3 - t2;
                worst = Math.max(worst, Math.abs(q - r));
                worstF = Math.max(worstF, Math.abs(fsurf - r));
                n++;
                out.append(String.format("%d,%d quick %d fast %d real %d  ", x, z, q, fsurf, r));
            }
            out.append('\n');
        }
        String summary = String.format("%d columns: quick %.2f ms (worst off %d), fast %.2f ms (worst off %d), real %.2f ms each", n, tq / 1e6 / n, worst, tf / 1e6 / n, worstF, tr / 1e6 / n);
        me.lovkar.wakingworld.WakingWorld.LOGGER.info("terrain {}: {}{}", at.toShortString(), summary, out);
        ctx.getSource().sendSuccess(() -> Component.literal(summary), true);
        return 1;
    }

    private static int summon(CommandContext<CommandSourceStack> ctx, String variant, int height, boolean instant) {
        CommandSourceStack src = ctx.getSource();
        boolean terrain = variant.equalsIgnoreCase("terrain");
        Palette palette = terrain ? null : Palette.preset(variant);
        if (!terrain && palette == null) {
            src.sendFailure(Component.translatable("commands.wakingworld.bad_variant", variant));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 from = src.getPosition();
        Vec2 rot = src.getRotation();
        Vec3 dir = Vec3.directionFromRotation(0.0F, rot.y);
        double distance = height * 0.6 + 10.0;
        Vec3 at = from.add(dir.scale(distance));
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));

        if (terrain) {
            palette = Palette.fromTerrain(level, ground, Math.max(10, height / 2));
        }
        // a summoned giant is the caller's giant: a supporter's comes dressed in their style, like one their rite woke
        java.util.UUID caller = src.getEntity() instanceof net.minecraft.world.entity.player.Player p ? p.getUUID() : null;
        palette = me.lovkar.wakingworld.supporter.SupporterCosmetics.dress(palette, caller);
        ColossusEntity colossus = WakingWorld.COLOSSUS.get().create(level);
        if (colossus == null) return 0;
        colossus.setBodyParams(palette, level.random.nextInt(), height);
        colossus.setWaker(caller);
        // face the caller
        float yaw = (float) (Math.toDegrees(Math.atan2(-(from.x - ground.getX()), from.z - ground.getZ())));
        colossus.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, yaw, 0.0F);
        colossus.yBodyRot = yaw;
        colossus.yHeadRot = yaw;
        colossus.setPersistenceRequired();
        if (!instant) colossus.setWake(colossus.wakeTotal()); // it comes up out of the ground
        level.addFreshEntity(colossus);

        final int h = colossus.bodyHeight();
        final String what = palette.name().equals("terrain") ? "terrain-born" : palette.name();
        src.sendSuccess(() -> Component.translatable("commands.wakingworld.summon.done", what, h,
                ground.getX(), ground.getY(), ground.getZ()), true);
        return 1;
    }

    /** Puts the nearest finished fight's land back - what an Hourglass of Restoration does, without the hourglass. */
    private static int restore(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        me.lovkar.wakingworld.ruin.RuinLedger ledger = me.lovkar.wakingworld.ruin.RuinLedger.get(level);
        me.lovkar.wakingworld.ruin.FightRecord ruin = ledger.nearestFinished(at, 256);
        if (ruin == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.wakingworld.restore.none", 256));
            return 0;
        }
        final int n = ruin.size();
        ledger.restore(ruin);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.wakingworld.restore.done", n), true);
        return n;
    }

    /** Admin: begins the rite at an altar with no offerings and no horn. */
    private static int rite(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        ServerLevel level = ctx.getSource().getLevel();
        if (!(level.getBlockEntity(pos) instanceof me.lovkar.wakingworld.ritual.AltarBlockEntity altar)) {
            ctx.getSource().sendFailure(Component.literal("No altar at " + pos.toShortString()));
            return 0;
        }
        altar.forceStart();
        ctx.getSource().sendSuccess(() -> Component.literal("The rite begins at " + pos.toShortString() + " (" + altar.kind() + ")"), true);
        return 1;
    }

    // ---- debug: a snapshot of a region, and what differs from it now (tests the Hourglass) ----
    private static final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> SNAPSHOT = new java.util.HashMap<>();

    private static int snapshot(CommandContext<CommandSourceStack> ctx, BlockPos a, BlockPos b) {
        ServerLevel level = ctx.getSource().getLevel();
        SNAPSHOT.clear();
        for (BlockPos p : BlockPos.betweenClosed(a, b)) SNAPSHOT.put(p.immutable(), level.getBlockState(p));
        final int n = SNAPSHOT.size();
        ctx.getSource().sendSuccess(() -> Component.literal("snapshot: " + n + " blocks"), true);
        return n;
    }

    /** Debug: writes a Dead Letter for where the caller stands and prints it to the log. */
    private static int letter(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        long t0 = System.nanoTime();
        net.minecraft.server.level.ServerPlayer who = ctx.getSource().getPlayer();
        if (who == null) who = level.getServer().getPlayerList().getPlayers().isEmpty() ? null : level.getServer().getPlayerList().getPlayers().get(0);
        me.lovkar.wakingworld.story.Letters.Facts facts = me.lovkar.wakingworld.story.Letters.gather(level, who, at, level.getRandom());
        net.minecraft.world.item.component.WrittenBookContent c = me.lovkar.wakingworld.story.Letters.compose(facts, level.getRandom()).book();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        StringBuilder sb = new StringBuilder("\n== " + c.title().raw() + " (by " + c.author() + ", " + ms + " ms) ==\n");
        for (net.minecraft.server.network.Filterable<Component> page : c.pages()) sb.append(page.raw().getString()).append("\n----\n");
        me.lovkar.wakingworld.WakingWorld.LOGGER.info("letter at {}:{}", at.toShortString(), sb);
        ctx.getSource().sendSuccess(() -> Component.literal("letter written to the log: " + c.title().raw()), true);
        return 1;
    }

    /** Debug: asks Gemini for a letter for where the caller stands (needs the key in the config) and logs what came back. */
    private static int letterGemini(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        net.minecraft.server.level.ServerPlayer who = ctx.getSource().getPlayer();
        if (who == null) who = level.getServer().getPlayerList().getPlayers().isEmpty() ? null : level.getServer().getPlayerList().getPlayers().get(0);
        if (!me.lovkar.wakingworld.story.GeminiLetters.enabled()) { ctx.getSource().sendFailure(Component.literal("Gemini letters are off or no key is set (or a failure a minute ago)")); return 0; }
        me.lovkar.wakingworld.story.Letters.Facts facts = me.lovkar.wakingworld.story.Letters.gather(level, who, at, level.getRandom());
        if (!facts.pointsSomewhere()) { ctx.getSource().sendFailure(Component.literal("this letter would point nowhere - try again")); return 0; }
        java.util.UUID id = me.lovkar.wakingworld.story.GeminiLetters.request(facts);
        long t0 = System.currentTimeMillis();
        new Thread(() -> {
            me.lovkar.wakingworld.story.GeminiLetters.Result r;
            while ((r = me.lovkar.wakingworld.story.GeminiLetters.poll(id)) == null && System.currentTimeMillis() - t0 < 40000) {
                try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            }
            if (r == null) { me.lovkar.wakingworld.WakingWorld.LOGGER.info("gemini letter: still nothing after 40 s"); return; }
            if (!r.ok()) { me.lovkar.wakingworld.WakingWorld.LOGGER.info("gemini letter: failed after {} ms (template would be used)", System.currentTimeMillis() - t0); return; }
            StringBuilder sb = new StringBuilder("\n== " + r.written().book().title().raw() + " (by " + r.written().book().author() + ", " + (System.currentTimeMillis() - t0) + " ms) ==\n");
            for (net.minecraft.server.network.Filterable<Component> page : r.written().book().pages()) sb.append(page.raw().getString()).append("\n----\n");
            me.lovkar.wakingworld.WakingWorld.LOGGER.info("gemini letter:{}", sb);
        }, "wakingworld-letter-debug").start();
        ctx.getSource().sendSuccess(() -> Component.literal("asked Gemini; the letter goes to the log when it comes"), true);
        return 1;
    }

    /** Debug: prints the region layer by layer to the server log, one letter per block (a legend follows). */
    private static int dump(CommandContext<CommandSourceStack> ctx, BlockPos a, BlockPos b) {
        ServerLevel level = ctx.getSource().getLevel();
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        Map<String, Character> legend = new java.util.LinkedHashMap<>();
        String pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!$%&()*+,-/:;<>?@^_{}|~"
                + "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df"
                + "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff"
                + "\u03b1\u03b2\u03b3\u03b4\u03b5\u03b6\u03b7\u03b8\u03b9\u03ba\u03bb\u03bc\u03bd\u03be\u03bf\u03c0\u03c1\u03c3\u03c4\u03c5\u03c6\u03c7\u03c8\u03c9"
                + "\u0391\u0392\u0393\u0394\u0395\u0396\u0397\u0398\u0399\u039a\u039b\u039c\u039d\u039e\u039f\u03a0\u03a1\u03a3\u03a4\u03a5\u03a6\u03a7\u03a8\u03a9"; // ~195 kinds before '#'

        StringBuilder out = new StringBuilder("\n");
        for (int y = y1; y >= y0; y--) {
            out.append("y=").append(y).append('\n');
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(new BlockPos(x, y, z));
                    if (st.isAir()) { out.append('.'); continue; }
                    String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    Character c = legend.get(name);
                    if (c == null) { c = legend.size() < pool.length() ? pool.charAt(legend.size()) : '#'; legend.put(name, c); }
                    out.append(c);
                }
                out.append('\n');
            }
        }
        out.append("legend: ");
        for (Map.Entry<String, Character> e : legend.entrySet()) out.append(e.getValue()).append('=').append(e.getKey()).append(' ');
        me.lovkar.wakingworld.WakingWorld.LOGGER.info("dump {}..{}:{}", a.toShortString(), b.toShortString(), out);
        ctx.getSource().sendSuccess(() -> Component.literal("dumped to the log (" + legend.size() + " block kinds)"), true);
        return 1;
    }

    private static int diff(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        Map<String, Integer> kinds = new java.util.HashMap<>();
        List<String> samples = new ArrayList<>();
        int n = 0;
        for (Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState> e : SNAPSHOT.entrySet()) {
            net.minecraft.world.level.block.state.BlockState now = level.getBlockState(e.getKey());
            if (now == e.getValue()) continue;
            n++;
            String k = e.getValue().getBlock().getName().getString() + " -> " + now.getBlock().getName().getString();
            kinds.merge(k, 1, Integer::sum);
            if (samples.size() < 8) samples.add(e.getKey().toShortString() + " " + k);
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>(kinds.entrySet());
        top.sort((x, y) -> y.getValue() - x.getValue());
        StringBuilder sb = new StringBuilder("diff: " + n + " blocks differ (rubble unmatched " + me.lovkar.wakingworld.entity.RubbleEntity.unmatched + ", orphans " + me.lovkar.wakingworld.entity.RubbleEntity.orphans + ")");
        for (int i = 0; i < Math.min(12, top.size()); i++) sb.append(" | ").append(top.get(i).getKey()).append(" x").append(top.get(i).getValue());
        for (String smp : samples) sb.append(" || ").append(smp);
        final String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
        return n;
    }

    /** Debug: every colossus in the level goes for the given entity. */
    /** The director: plays a trailer scene with the caller as the camera. */
    /**
     * Where the camera would set a cataclysm up: the same search the scenes run, reported rather than
     * used, so a spot can be looked at before three minutes are spent recording at it.
     */
    private static int site(CommandContext<CommandSourceStack> ctx, int minOut, int maxOut) {
        ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.core.BlockPos from = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
        long began = System.nanoTime();
        net.minecraft.core.BlockPos at = me.lovkar.wakingworld.story.Cinematics.scout(level, from, minOut, maxOut);
        long took = (System.nanoTime() - began) / 1_000_000L;
        if (at == null) {
            ctx.getSource().sendFailure(Component.literal("No open ground within " + maxOut + " blocks - all sea or all hillside."));
            return 0;
        }
        int spread = me.lovkar.wakingworld.story.Cinematics.levelness(level, at);
        int away = (int) Math.sqrt(at.distSqr(from));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Open ground at " + at.getX() + " " + at.getY() + " " + at.getZ()
                        + " (" + away + " m away, " + spread + " blocks of fall across 52, found in " + took + " ms)"), false);
        return 1;
    }

    private static int cine(CommandContext<CommandSourceStack> ctx, String scene, int renderDistance) {
        if (scene.equals("stop")) {
            if (!me.lovkar.wakingworld.story.Cinematics.running()) ctx.getSource().sendFailure(Component.literal("Nothing is rolling."));
            me.lovkar.wakingworld.story.Cinematics.stop(); // says "Cut." itself
            return 1;
        }
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("The camera needs a player."));
            return 0;
        }
        String result = me.lovkar.wakingworld.story.Cinematics.start(player, scene, renderDistance);
        if (result == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No such scene. 0.1: shrine, rite, fight, kingdom, titan, all. "
                            + "0.2: lands, tornado, earthquake, volcano, meteor, bloodmoon, cataclysms."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int target(CommandContext<CommandSourceStack> ctx, Entity who) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = 0;
        for (Entity e : level.getEntities(WakingWorld.COLOSSUS.get(), x -> true)) {
            if (e instanceof ColossusEntity c && who instanceof net.minecraft.world.entity.LivingEntity living) {
                c.setTarget(living);
                n++;
            }
        }
        final int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal(count + " colossi now target " + who.getName().getString()), true);
        return n;
    }

    /** One star, on its way: where you are looking, or where you say. */
    private static int meteor(CommandContext<CommandSourceStack> ctx, BlockPos at, int size) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.world.phys.Vec3 target;
        if (at != null) {
            target = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        } else {
            net.minecraft.world.phys.Vec3 from = ctx.getSource().getPosition();
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 40 + level.random.nextDouble() * 40;
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(from.x + Math.cos(angle) * dist, 0, from.z + Math.sin(angle) * dist));
            target = new net.minecraft.world.phys.Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        }
        me.lovkar.wakingworld.cataclysm.Cataclysms.fall(level, target, size, true);
        final String where = String.format("%.0f %.0f %.0f", target.x, target.y, target.z);
        ctx.getSource().sendSuccess(() -> Component.literal("A star is falling towards " + where + " (size " + size + ")"), true);
        return 1;
    }

    /** Name a piece of country now, without waiting for somebody to walk into it. */
    private static int nameLand(CommandContext<CommandSourceStack> ctx, BlockPos at) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        BlockPos where = at != null ? at : BlockPos.containing(ctx.getSource().getPosition());
        me.lovkar.wakingworld.land.Lands.Land land = me.lovkar.wakingworld.land.Lands.get(level).nameAt(level, where);
        if (land == null) {
            ctx.getSource().sendFailure(Component.literal("Could not name it."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(land.name()).withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.literal("  " + land.lore()).withStyle(net.minecraft.ChatFormatting.GRAY)), false);
        return 1;
    }

    /** Where you are, and everywhere you have been. */
    private static int lands(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        me.lovkar.wakingworld.land.Lands lands = me.lovkar.wakingworld.land.Lands.get(level);
        if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) {
            final int n = lands.count();
            ctx.getSource().sendSuccess(() -> Component.literal(n + " land(s) named in this world"), false);
            return n;
        }
        me.lovkar.wakingworld.land.Lands.Land here = lands.here(p);
        java.util.List<me.lovkar.wakingworld.land.Lands.Land> been = lands.visited(p);
        ctx.getSource().sendSuccess(() -> Component.literal(here == null ? "This country has no name yet." : "You are in ")
                .withStyle(net.minecraft.ChatFormatting.GRAY)
                .append(here == null ? Component.empty()
                        : Component.literal(here.name()).withStyle(net.minecraft.ChatFormatting.GOLD)), false);
        for (me.lovkar.wakingworld.land.Lands.Land l : been) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + l.name()).withStyle(net.minecraft.ChatFormatting.YELLOW)
                    .append(Component.literal("  " + l.lore()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)), false);
        }
        final int n = been.size();
        ctx.getSource().sendSuccess(() -> Component.literal(n + " of " + lands.count() + " named lands walked").withStyle(net.minecraft.ChatFormatting.DARK_GRAY), false);
        return n;
    }

    /** A column of wind, walking. */
    private static int tornado(CommandContext<CommandSourceStack> ctx, BlockPos at, int seconds) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.world.phys.Vec3 from = ctx.getSource().getPosition();
        BlockPos ground = at != null ? at : me.lovkar.wakingworld.cataclysm.Cataclysms.surface(level, from.x, from.z);
        me.lovkar.wakingworld.cataclysm.TornadoEntity.spawn(level,
                new net.minecraft.world.phys.Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5), seconds);
        final String w = ground.getX() + " " + ground.getY() + " " + ground.getZ();
        ctx.getSource().sendSuccess(() -> Component.literal("A column comes down at " + w), true);
        return 1;
    }

    /** The ground turns. */
    private static int earthquake(CommandContext<CommandSourceStack> ctx, BlockPos at) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.world.phys.Vec3 from = ctx.getSource().getPosition();
        BlockPos ground = at != null ? at : me.lovkar.wakingworld.cataclysm.Cataclysms.surface(level, from.x, from.z);
        me.lovkar.wakingworld.cataclysm.Weather.forceQuake(level,
                new net.minecraft.world.phys.Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5));
        ctx.getSource().sendSuccess(() -> Component.literal("The ground turns."), true);
        return 1;
    }

    /** Turn the moon, or turn it back. */
    private static int bloodMoon(CommandContext<CommandSourceStack> ctx, boolean on) {
        me.lovkar.wakingworld.cataclysm.BloodMoon.force(ctx.getSource().getLevel(), on);
        ctx.getSource().sendSuccess(() -> Component.literal(on ? "The moon turns." : "The moon is itself again."), true);
        return 1;
    }

    /** One wave of the siege, around wherever the command was run. */
    private static int bloodWave(CommandContext<CommandSourceStack> ctx, int count) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        net.minecraft.server.level.ServerPlayer target = ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer p ? p : null;
        int made = me.lovkar.wakingworld.cataclysm.BloodMoon.siege(level, ctx.getSource().getPosition(), target, count, level.random);
        final int n = made;
        ctx.getSource().sendSuccess(() -> Component.literal(n + " of " + count + " found a dark place to stand"), true);
        return n;
    }

    /** A mountain, here and now. */
    private static int volcano(CommandContext<CommandSourceStack> ctx, BlockPos at, int height, int foot) {
        return volcano(ctx, at, height, foot, 0);
    }

    /** {@code seconds} > 0 forces the pace of the rise - what the camera uses, and how to preview it. */
    private static int volcano(CommandContext<CommandSourceStack> ctx, BlockPos at, int height, int foot, int seconds) {
        net.minecraft.server.level.ServerLevel level = ctx.getSource().getLevel();
        BlockPos where = at;
        if (where == null) {
            net.minecraft.world.phys.Vec3 from = ctx.getSource().getPosition();
            for (int tries = 0; tries < 24 && where == null; tries++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double dist = 60 + level.random.nextDouble() * 40;
                BlockPos p = me.lovkar.wakingworld.cataclysm.Cataclysms.surface(level,
                        from.x + Math.cos(angle) * dist, from.z + Math.sin(angle) * dist);
                // dry land, or the cone comes up as an island and the shore is a wall of rock
                if (p.getY() >= level.getSeaLevel() + 2 && level.getFluidState(p.below()).isEmpty()) where = p;
            }
            if (where == null) {
                ctx.getSource().sendFailure(Component.literal("No dry ground near here - name a spot: /wakingworld volcano <x y z>"));
                return 0;
            }
        }
        me.lovkar.wakingworld.cataclysm.Volcano.force(level, where, height, foot, seconds);
        final String w = where.getX() + " " + where.getY() + " " + where.getZ();
        ctx.getSource().sendSuccess(() -> Component.literal("A vent opens at " + w
                + (seconds > 0 ? " - up in " + seconds + " s." : " - stand back.")), true);
        return 1;
    }

    /** The whole shower, right now. */
    private static int shower(CommandContext<CommandSourceStack> ctx) {
        me.lovkar.wakingworld.cataclysm.Cataclysms.force(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal("The sky turns: a meteor shower begins."), true);
        return 1;
    }

    private static int killAll(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = 0;
        for (Entity e : level.getEntities(WakingWorld.COLOSSUS.get(), x -> true)) {
            e.discard();
            n++;
        }
        final int count = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.wakingworld.kill.done", count), true);
        return n;
    }
}
