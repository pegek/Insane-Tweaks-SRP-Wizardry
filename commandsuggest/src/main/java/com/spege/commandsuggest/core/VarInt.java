package com.spege.commandsuggest.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Liczba calkowita w zapisie zmiennej dlugosci, taka sama jak w protokole Minecrafta.
 * Wlasna, a nie {@code PacketBuffer}, bo {@code core} nie ma prawa znac klas Minecrafta.
 */
public final class VarInt {

    private VarInt() {
    }

    public static void write(DataOutput out, int value) throws IOException {
        int v = value;
        while ((v & 0xFFFFFF80) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v & 0x7F);
    }

    public static int read(DataInput in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = in.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt dluzszy niz piec bajtow");
            }
        }
    }
}
