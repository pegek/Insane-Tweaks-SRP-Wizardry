package com.spege.insanetweaks.entities.magic;

/**
 * Where a magic parasite may teleport, as horizontal offsets {@code {dx, dz}} from a base point.
 * Pure maths, no Minecraft types, so it is unit-tested. The caller tries the candidates in order
 * and takes the first that {@code attemptTeleport} accepts.
 *
 * <p>Minecraft yaw: 0 looks towards +z and 90 towards -x, so the look vector is
 * {@code (-sin yaw, cos yaw)}.
 */
public final class BlinkPlanner {

    private BlinkPlanner() {
    }

    /**
     * Five candidates at {@code distance} around a target facing {@code yawDegrees}: straight
     * behind, the two rear diagonals, then the two sides.
     */
    public static double[][] behind(float yawDegrees, double distance) {
        double yaw = Math.toRadians(yawDegrees);
        double bx = Math.sin(yaw);
        double bz = -Math.cos(yaw);
        double[][] dirs = {
                { bx, bz },
                rotate(bx, bz, 45.0D),
                rotate(bx, bz, -45.0D),
                rotate(bx, bz, 90.0D),
                rotate(bx, bz, -90.0D)
        };
        for (double[] d : dirs) {
            d[0] *= distance;
            d[1] *= distance;
        }
        return dirs;
    }

    /**
     * Two candidates perpendicular to a projectile's horizontal motion, {@code distance} away. A
     * projectile with no horizontal motion falls back to the dodger's own facing.
     */
    public static double[][] sidestep(double motionX, double motionZ, double distance, boolean leftFirst,
            float fallbackYawDegrees) {
        double mx = motionX;
        double mz = motionZ;
        double len = Math.sqrt(mx * mx + mz * mz);
        if (len < 1.0E-4D) {
            double yaw = Math.toRadians(fallbackYawDegrees);
            mx = -Math.sin(yaw);
            mz = Math.cos(yaw);
            len = 1.0D;
        }
        double nx = mx / len;
        double nz = mz / len;
        double[] left = { -nz * distance, nx * distance };
        double[] right = { nz * distance, -nx * distance };
        return leftFirst ? new double[][] { left, right } : new double[][] { right, left };
    }

    /** {@code min + (max - min) * roll}, for a roll in [0, 1). */
    public static double lerp(double min, double max, double roll) {
        return min + (max - min) * roll;
    }

    private static double[] rotate(double x, double z, double degrees) {
        double r = Math.toRadians(degrees);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new double[] { x * cos - z * sin, x * sin + z * cos };
    }
}
