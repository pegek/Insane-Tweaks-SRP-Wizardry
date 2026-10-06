package com.spege.insanetweaks.entities.magic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BlinkPlannerTest {

    private static final double EPS = 1.0E-9D;

    @Test
    public void behindATargetFacingSouthIsNorthOfIt() {
        // yaw 0 looks towards +z, so behind is -z
        double[][] offsets = BlinkPlanner.behind(0.0F, 2.0D);
        assertEquals(0.0D, offsets[0][0], EPS);
        assertEquals(-2.0D, offsets[0][1], EPS);
    }

    @Test
    public void behindATargetFacingWestIsEastOfIt() {
        // yaw 90 looks towards -x, so behind is +x
        double[][] offsets = BlinkPlanner.behind(90.0F, 2.0D);
        assertEquals(2.0D, offsets[0][0], EPS);
        assertEquals(0.0D, offsets[0][1], EPS);
    }

    @Test
    public void everyCandidateKeepsTheRequestedDistance() {
        double[][] offsets = BlinkPlanner.behind(37.0F, 2.5D);
        assertEquals(5, offsets.length);
        for (double[] o : offsets) {
            assertEquals(2.5D, Math.sqrt(o[0] * o[0] + o[1] * o[1]), EPS);
        }
    }

    @Test
    public void aSidestepIsPerpendicularToTheProjectile() {
        double[][] offsets = BlinkPlanner.sidestep(1.0D, 0.0D, 3.0D, true, 0.0F);
        assertEquals(0.0D, offsets[0][0], EPS);
        assertEquals(3.0D, offsets[0][1], EPS);
        assertEquals(0.0D, offsets[1][0], EPS);
        assertEquals(-3.0D, offsets[1][1], EPS);
    }

    @Test
    public void rightFirstSwapsTheOrder() {
        double[][] offsets = BlinkPlanner.sidestep(1.0D, 0.0D, 3.0D, false, 0.0F);
        assertEquals(-3.0D, offsets[0][1], EPS);
        assertEquals(3.0D, offsets[1][1], EPS);
    }

    @Test
    public void aMotionlessProjectileFallsBackToTheFacing() {
        // facing yaw 0 = +z; perpendicular "left" of +z is -x
        double[][] offsets = BlinkPlanner.sidestep(0.0D, 0.0D, 4.0D, true, 0.0F);
        assertEquals(-4.0D, offsets[0][0], EPS);
        assertEquals(0.0D, offsets[0][1], EPS);
    }

    @Test
    public void lerpMapsTheRollIntoTheBand() {
        assertEquals(1.5D, BlinkPlanner.lerp(1.5D, 2.5D, 0.0D), EPS);
        assertEquals(2.0D, BlinkPlanner.lerp(1.5D, 2.5D, 0.5D), EPS);
    }
}
