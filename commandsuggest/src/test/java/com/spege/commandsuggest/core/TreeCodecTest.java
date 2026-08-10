package com.spege.commandsuggest.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TreeCodecTest {

    private static CommandIndex przykladowyIndeks() {
        CmdArg target = new CmdArg("target", ArgType.PLAYER, null, null, null);
        CmdArg points = new CmdArg("points", ArgType.INT, null, Double.valueOf(0.0d), Double.valueOf(99.0d));
        CmdNode set = new CmdNode("set", Arrays.asList(target, points), null, true, null);
        CmdArg rule = new CmdArg("rule", ArgType.CHOICE, Arrays.asList("doFireTick", "keepInventory"), null, null);
        CmdNode gamerule = new CmdNode(null, Collections.singletonList(rule), null, true, "/gamerule <rule>");
        return new CommandIndex(Arrays.asList(
                new CommandTree("srpevolution", Arrays.asList("srpevo"),
                        new CmdNode(null, null, Collections.singletonList(set), false, null)),
                new CommandTree("gamerule", null, gamerule)));
    }

    @Test
    public void varIntPrzezywaRoundTrip() throws Exception {
        int[] wartosci = { 0, 1, 127, 128, 255, 300, 16383, 16384, 1 << 20, Integer.MAX_VALUE,
                -1, Integer.MIN_VALUE };
        for (int v : wartosci) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bos);
            VarInt.write(out, v);
            out.flush();
            java.io.DataInputStream in =
                    new java.io.DataInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()));
            assertEquals(v, VarInt.read(in));
        }
    }

    @Test
    public void roundTripZachowujeStrukture() {
        CommandIndex oryginal = przykladowyIndeks();
        CommandIndex odkodowany = TreeCodec.decode(TreeCodec.encode(oryginal));

        assertEquals(2, odkodowany.getCommands().size());
        CommandTree srp = odkodowany.byName("srpevo");
        assertEquals("srpevolution", srp.getName());
        assertEquals(Arrays.asList("srpevo"), srp.getAliases());

        CmdNode set = srp.getRoot().getSub().get(0);
        assertEquals("set", set.getLiteral());
        assertTrue(set.isExecutable());
        assertSame(ArgType.PLAYER, set.getArgs().get(0).getType());
        assertEquals("points", set.getArgs().get(1).getName());
        assertEquals(Double.valueOf(0.0d), set.getArgs().get(1).getMin());
        assertEquals(Double.valueOf(99.0d), set.getArgs().get(1).getMax());

        CommandTree gr = odkodowany.byName("gamerule");
        assertEquals("/gamerule <rule>", gr.getRoot().getUsage());
        assertEquals(Arrays.asList("doFireTick", "keepInventory"), gr.getRoot().getArgs().get(0).getChoices());
        assertNull(gr.getRoot().getArgs().get(0).getMin());
        assertTrue(gr.getAliases().isEmpty());
    }

    @Test
    public void ponowneZakodowanieDajeIdentyczneBajty() {
        byte[] raz = TreeCodec.encode(przykladowyIndeks());
        byte[] dwa = TreeCodec.encode(TreeCodec.decode(raz));
        assertArrayEquals(raz, dwa);
    }

    @Test
    public void pustyIndeksTezPrzechodzi() {
        CommandIndex pusty = TreeCodec.decode(TreeCodec.encode(CommandIndex.EMPTY));
        assertTrue(pusty.getCommands().isEmpty());
    }

    @Test
    public void duzeDrzewoIdzieGzipemIWracaCaleTo() {
        List<CommandTree> duzo = new ArrayList<CommandTree>();
        for (int i = 0; i < 400; i++) {
            CmdArg a = new CmdArg("argument-o-dlugiej-nazwie-" + i, ArgType.WORD, null, null, null);
            duzo.add(new CommandTree("komenda-numer-" + i, Collections.singletonList("alias-" + i),
                    new CmdNode(null, Collections.singletonList(a), null, true,
                            "/komenda-numer-" + i + " <argument>")));
        }
        CommandIndex idx = new CommandIndex(duzo);
        byte[] bajty = TreeCodec.encode(idx);
        assertEquals("powyzej progu ma byc flaga gzipa", 1, bajty[0]);

        CommandIndex odkodowany = TreeCodec.decode(bajty);
        assertEquals(400, odkodowany.getCommands().size());
        assertEquals("/komenda-numer-399 <argument>",
                odkodowany.byName("komenda-numer-399").getRoot().getUsage());
    }

    @Test
    public void maleDrzewoIdzieBezGzipa() {
        byte[] bajty = TreeCodec.encode(przykladowyIndeks());
        assertEquals("ponizej progu bez gzipa", 0, bajty[0]);
    }
}
