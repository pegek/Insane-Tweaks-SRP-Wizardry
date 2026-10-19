package com.spege.insanetweaks.worldgen.sanctum;

/** An inclusive integer box. Pure Java, so the layout maths is unit-tested. */
public final class SanctumBox {

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public SanctumBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean intersects(SanctumBox o) {
        return this.minX <= o.maxX && this.maxX >= o.minX && this.minY <= o.maxY && this.maxY >= o.minY
                && this.minZ <= o.maxZ && this.maxZ >= o.minZ;
    }

    /** The overlap, or null when the boxes do not intersect. */
    public SanctumBox intersection(SanctumBox o) {
        if (!intersects(o)) {
            return null;
        }
        return new SanctumBox(Math.max(this.minX, o.minX), Math.max(this.minY, o.minY), Math.max(this.minZ, o.minZ),
                Math.min(this.maxX, o.maxX), Math.min(this.maxY, o.maxY), Math.min(this.maxZ, o.maxZ));
    }

    public SanctumBox union(SanctumBox o) {
        return new SanctumBox(Math.min(this.minX, o.minX), Math.min(this.minY, o.minY), Math.min(this.minZ, o.minZ),
                Math.max(this.maxX, o.maxX), Math.max(this.maxY, o.maxY), Math.max(this.maxZ, o.maxZ));
    }

    /** The box shrunk by {@code n} on every side (a room's interior), or null if nothing is left. */
    public SanctumBox shrink(int n) {
        if (this.maxX - this.minX < 2 * n || this.maxY - this.minY < 2 * n || this.maxZ - this.minZ < 2 * n) {
            return null;
        }
        return new SanctumBox(this.minX + n, this.minY + n, this.minZ + n, this.maxX - n, this.maxY - n, this.maxZ - n);
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SanctumBox)) {
            return false;
        }
        SanctumBox b = (SanctumBox) o;
        return b.minX == this.minX && b.minY == this.minY && b.minZ == this.minZ && b.maxX == this.maxX
                && b.maxY == this.maxY && b.maxZ == this.maxZ;
    }

    @Override
    public int hashCode() {
        int h = this.minX;
        h = 31 * h + this.minY;
        h = 31 * h + this.minZ;
        h = 31 * h + this.maxX;
        h = 31 * h + this.maxY;
        return 31 * h + this.maxZ;
    }

    @Override
    public String toString() {
        return "[" + this.minX + "," + this.minY + "," + this.minZ + " -> " + this.maxX + "," + this.maxY + ","
                + this.maxZ + "]";
    }
}
