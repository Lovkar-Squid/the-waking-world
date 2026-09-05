package me.lovkar.wakingworld.entity;

import me.lovkar.wakingworld.WakingConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * How a colossus fights. It walks straight at you - through trees, over hills, across lakes - and
 * only falls back to pathfinding when it has been pushing against solid rock for a while. When the
 * pathfinder has nothing either (a cliff too tall to step, a pit, standing in deep water where no
 * path starts) it sidesteps along the obstacle for a couple of seconds and tries again. When an
 * attack is ready it asks the entity to pick one for the distance and the phase (weighted, with a
 * memory of the last three so nothing repeats), and the entity runs it. While an attack runs the
 * goal only keeps the giant facing its target. The Titan, with its target on another island of the
 * End, hops island to island towards it first ({@link ColossusEntity#hop}).
 */
public class ColossusCombatGoal extends Goal {
    private final ColossusEntity mob;
    private int pathTimer;
    private int treeCheck;
    private boolean treeNear;
    private int detourTicks;
    private Vec3 detourDir = Vec3.ZERO;
    private int detourSide = 1;
    private int detourFails;

    public ColossusCombatGoal(ColossusEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        pathTimer = 0;
        treeCheck = 0;
        detourTicks = 0;
        detourFails = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        Vec3 stone = mob.hopTurn();
        if (stone != null) mob.getLookControl().setLookAt(stone.x, stone.y + mob.bodyHeight() * 0.7, stone.z); // facing the island it will jump for
        else mob.getLookControl().setLookAt(target, 6.0F, 6.0F);

        if (mob.isAttacking()) {
            if (mob.currentAttack() != Attack.CHARGE) mob.getNavigation().stop();
            return;
        }

        double h = mob.bodyHeight();
        double melee = h * 0.36;
        double d = mob.distanceTo(target);

        if (d > melee) {
            if (detourTicks > 0) {
                // walking along the obstacle for a moment
                detourTicks--;
                Vec3 to = mob.position().add(detourDir.scale(12));
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(to.x, mob.getY(), to.z, 1.0);
            } else if (WakingConfig.trample() && !mob.isStuck()) {
                // straight at them; the world in between is the world's problem
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
            } else if (--pathTimer <= 0) {
                pathTimer = 8 + mob.getRandom().nextInt(6);
                if (!mob.getNavigation().moveTo(target, 1.0)) {
                    // nothing to path along either: sidestep, alternating sides so it does not
                    // shuffle back and forth in one spot - and after two useless sidesteps it is
                    // in a hole: it jumps out
                    Vec3 to = target.position().subtract(mob.position());
                    double flat = to.horizontalDistance();
                    if (++detourFails >= 3 && mob.canUse(Attack.LEAP) && flat <= 48) {
                        detourFails = 0;
                        mob.clearStuck();
                        mob.startAttack(Attack.LEAP);
                    } else if (flat > 0.01) {
                        detourSide = -detourSide;
                        detourDir = new Vec3(-to.z / flat * detourSide, 0, to.x / flat * detourSide);
                        detourTicks = 30 + mob.getRandom().nextInt(25);
                        mob.clearStuck();
                    }
                } else {
                    detourFails = 0;
                }
            }
        } else {
            mob.getNavigation().stop();
            detourTicks = 0;
            detourFails = 0;
        }

        if (!mob.attackReady()) return;
        // the quarry across the void: the Titan crosses first (island by island) and fights when it gets there
        if (mob.hop(target)) return;
        if (--treeCheck <= 0) {
            treeCheck = 40;
            treeNear = mob.treeNearby();
        }
        Attack pick = mob.chooseAttack(d, treeNear);
        if (pick != Attack.NONE) mob.startAttack(pick);
    }
}
