package com.spege.commandsuggest.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Drzewo komend do bajtow i z powrotem, z pula stringow i opcjonalnym gzipem.
 *
 * <p>Uklad: jeden bajt flagi (0 = surowo, 1 = gzip), a dalej — po ewentualnej dekompresji —
 * pula stringow (varint licznik + {@code writeUTF}), potem komendy. Pula placi za siebie od razu:
 * identyfikatory typow powtarzaja sie w kazdym argumencie, a nazwy komend w aliasach.
 *
 * <p>Identyfikator typu idzie jako STRING z puli, nie jako {@code ordinal()} — dopisanie
 * wartosci do {@link ArgType} nie ma prawa uniewaznic pakietu ani opisow na dysku.
 */
public final class TreeCodec {

    /** Ponizej tego rozmiaru gzip kosztuje wiecej, niz oszczedza. */
    private static final int GZIP_THRESHOLD = 4096;

    private static final int NODE_HAS_LITERAL = 1;
    private static final int NODE_EXECUTABLE = 2;
    private static final int NODE_HAS_USAGE = 4;

    private static final int ARG_HAS_MIN = 1;
    private static final int ARG_HAS_MAX = 2;
    private static final int ARG_HAS_CHOICES = 4;

    private TreeCodec() {
    }

    public static byte[] encode(CommandIndex index) {
        try {
            Map<String, Integer> pool = new LinkedHashMap<String, Integer>();
            for (CommandTree t : index.getCommands()) {
                intern(pool, t.getName());
                for (String a : t.getAliases()) {
                    intern(pool, a);
                }
                collectNode(t.getRoot(), pool);
            }

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(body);
            VarInt.write(out, pool.size());
            for (String s : pool.keySet()) {
                out.writeUTF(s);
            }
            VarInt.write(out, index.getCommands().size());
            for (CommandTree t : index.getCommands()) {
                VarInt.write(out, pool.get(t.getName()).intValue());
                VarInt.write(out, t.getAliases().size());
                for (String a : t.getAliases()) {
                    VarInt.write(out, pool.get(a).intValue());
                }
                writeNode(out, t.getRoot(), pool);
            }
            out.flush();
            byte[] payload = body.toByteArray();

            ByteArrayOutputStream result = new ByteArrayOutputStream(payload.length + 1);
            if (payload.length >= GZIP_THRESHOLD) {
                result.write(1);
                GZIPOutputStream gz = new GZIPOutputStream(result);
                gz.write(payload);
                gz.close();
            } else {
                result.write(0);
                result.write(payload);
            }
            return result.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("nie udalo sie zakodowac drzewa komend", e);
        }
    }

    public static CommandIndex decode(byte[] data) {
        try {
            if (data == null || data.length < 1) {
                return CommandIndex.EMPTY;
            }
            ByteArrayInputStream raw = new ByteArrayInputStream(data);
            int flag = raw.read();
            DataInputStream in = new DataInputStream(flag == 1 ? new GZIPInputStream(raw) : raw);

            int poolSize = VarInt.read(in);
            String[] pool = new String[poolSize];
            for (int i = 0; i < poolSize; i++) {
                pool[i] = in.readUTF();
            }
            int count = VarInt.read(in);
            List<CommandTree> trees = new ArrayList<CommandTree>(count);
            for (int i = 0; i < count; i++) {
                String name = pool[VarInt.read(in)];
                int aliasCount = VarInt.read(in);
                List<String> aliases = new ArrayList<String>(aliasCount);
                for (int a = 0; a < aliasCount; a++) {
                    aliases.add(pool[VarInt.read(in)]);
                }
                trees.add(new CommandTree(name, aliases, readNode(in, pool)));
            }
            return new CommandIndex(trees);
        } catch (IOException e) {
            throw new IllegalStateException("nie udalo sie odkodowac drzewa komend", e);
        }
    }

    private static void intern(Map<String, Integer> pool, String s) {
        if (!pool.containsKey(s)) {
            pool.put(s, Integer.valueOf(pool.size()));
        }
    }

    /** Kolejnosc MUSI byc ta sama, co w {@link #writeNode} — inaczej indeksy sie rozjada. */
    private static void collectNode(CmdNode n, Map<String, Integer> pool) {
        if (n.getLiteral() != null) {
            intern(pool, n.getLiteral());
        }
        if (n.getUsage() != null) {
            intern(pool, n.getUsage());
        }
        for (CmdArg a : n.getArgs()) {
            intern(pool, a.getName());
            intern(pool, a.getType().getId());
            for (String c : a.getChoices()) {
                intern(pool, c);
            }
        }
        for (CmdNode s : n.getSub()) {
            collectNode(s, pool);
        }
    }

    private static void writeNode(DataOutputStream out, CmdNode n, Map<String, Integer> pool)
            throws IOException {
        int flags = 0;
        if (n.getLiteral() != null) {
            flags |= NODE_HAS_LITERAL;
        }
        if (n.isExecutable()) {
            flags |= NODE_EXECUTABLE;
        }
        if (n.getUsage() != null) {
            flags |= NODE_HAS_USAGE;
        }
        out.writeByte(flags);
        if (n.getLiteral() != null) {
            VarInt.write(out, pool.get(n.getLiteral()).intValue());
        }
        if (n.getUsage() != null) {
            VarInt.write(out, pool.get(n.getUsage()).intValue());
        }
        VarInt.write(out, n.getArgs().size());
        for (CmdArg a : n.getArgs()) {
            writeArg(out, a, pool);
        }
        VarInt.write(out, n.getSub().size());
        for (CmdNode s : n.getSub()) {
            writeNode(out, s, pool);
        }
    }

    private static CmdNode readNode(DataInputStream in, String[] pool) throws IOException {
        int flags = in.readByte();
        String literal = (flags & NODE_HAS_LITERAL) != 0 ? pool[VarInt.read(in)] : null;
        String usage = (flags & NODE_HAS_USAGE) != 0 ? pool[VarInt.read(in)] : null;
        int argCount = VarInt.read(in);
        List<CmdArg> args = new ArrayList<CmdArg>(argCount);
        for (int i = 0; i < argCount; i++) {
            args.add(readArg(in, pool));
        }
        int subCount = VarInt.read(in);
        List<CmdNode> sub = new ArrayList<CmdNode>(subCount);
        for (int i = 0; i < subCount; i++) {
            sub.add(readNode(in, pool));
        }
        return new CmdNode(literal, args, sub, (flags & NODE_EXECUTABLE) != 0, usage);
    }

    private static void writeArg(DataOutputStream out, CmdArg a, Map<String, Integer> pool)
            throws IOException {
        VarInt.write(out, pool.get(a.getName()).intValue());
        VarInt.write(out, pool.get(a.getType().getId()).intValue());
        int flags = 0;
        if (a.getMin() != null) {
            flags |= ARG_HAS_MIN;
        }
        if (a.getMax() != null) {
            flags |= ARG_HAS_MAX;
        }
        if (!a.getChoices().isEmpty()) {
            flags |= ARG_HAS_CHOICES;
        }
        out.writeByte(flags);
        if (a.getMin() != null) {
            out.writeDouble(a.getMin().doubleValue());
        }
        if (a.getMax() != null) {
            out.writeDouble(a.getMax().doubleValue());
        }
        if (!a.getChoices().isEmpty()) {
            VarInt.write(out, a.getChoices().size());
            for (String c : a.getChoices()) {
                VarInt.write(out, pool.get(c).intValue());
            }
        }
    }

    private static CmdArg readArg(DataInputStream in, String[] pool) throws IOException {
        String name = pool[VarInt.read(in)];
        ArgType type = ArgType.byId(pool[VarInt.read(in)]);
        int flags = in.readByte();
        Double min = (flags & ARG_HAS_MIN) != 0 ? Double.valueOf(in.readDouble()) : null;
        Double max = (flags & ARG_HAS_MAX) != 0 ? Double.valueOf(in.readDouble()) : null;
        List<String> choices = null;
        if ((flags & ARG_HAS_CHOICES) != 0) {
            int n = VarInt.read(in);
            choices = new ArrayList<String>(n);
            for (int i = 0; i < n; i++) {
                choices.add(pool[VarInt.read(in)]);
            }
        }
        return new CmdArg(name, type, choices, min, max);
    }
}
