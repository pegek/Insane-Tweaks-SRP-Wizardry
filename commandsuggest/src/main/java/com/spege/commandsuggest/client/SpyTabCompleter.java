package com.spege.commandsuggest.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.network.play.client.CPacketTabComplete;
import net.minecraft.util.TabCompleter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Przechwytuje odpowiedz serwera na tab-complete, zamiast mutowac pole tekstowe.
 *
 * <p>To jest cala sztuczka, ktora pozwala obejsc sie bez mixinow w
 * {@code NetHandlerPlayClient} i {@code GuiChat$ChatTabCompleter}. Sciezka odpowiedzi to
 * {@code NetHandlerPlayClient.handleTabComplete} -&gt; {@code GuiChat.setCompletions(...)} -&gt;
 * {@code tabCompleter.setCompletions(...)}. Waniliowa implementacja przy
 * {@code requestedCompletions == false} nie robi NIC (odpowiedz przepada), a przy {@code true}
 * przepisuje pole tekstowe wspolnym prefiksem i przy jego braku wola {@code complete()},
 * wstawiajac pierwszego kandydata. Tu obie te sciezki sa zastapione zapisem do listy.
 *
 * <p>Zweryfikowane na {@code net.minecraft.util.TabCompleter} z forge-1.12.2-14.23.5.2860-sources:
 * konstruktor to {@code TabCompleter(GuiTextField, boolean)}, {@code setCompletions} i
 * {@code complete()} nie sa {@code final}, a {@code getTargetBlockPos()} jest jedyna metoda
 * abstrakcyjna. Implementacja {@link #getTargetBlockPos()} ponizej to doslowna kopia
 * {@code GuiChat.ChatTabCompleter.getTargetBlockPos()} (ktora tez konstruuje sie z
 * {@code hasTargetBlockIn == false}, jak tutaj) - identycznosc jest celowa, to jedyne pole tej
 * klasy, ktore serwer faktycznie czyta z {@code CPacketTabComplete}.
 *
 * <p>{@code complete()} jest nadpisane pustka celowo: nasz handler i tak anuluje TAB, ale gdyby
 * jakikolwiek inny kod je zawolal, ma nie ruszac tekstu gracza.
 */
@SideOnly(Side.CLIENT)
public final class SpyTabCompleter extends TabCompleter {

    private final List<String> captured = new ArrayList<String>();
    private boolean fresh;

    /** {@code false} = "to nie jest komenda blokowa", tak samo jak w {@code GuiChat}. */
    public SpyTabCompleter(GuiTextField textField) {
        super(textField, false);
    }

    /**
     * Wysyla zapytanie o wartosci, ktorych klient nie zna.
     *
     * @param lineUpToCursor cala linia od ukosnika do kursora - dokladnie to, co wysyla wanilia
     *                       w {@code TabCompleter.requestCompletions}
     */
    public void request(String lineUpToCursor) {
        this.captured.clear();
        this.fresh = false;
        if (lineUpToCursor == null || lineUpToCursor.isEmpty()) {
            return;
        }
        // komendy klienckie Forge nie ida przez siec - odpowiadaja od razu, do latestAutoComplete
        ClientCommandHandler.instance.autoComplete(lineUpToCursor);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendPacket(
                    new CPacketTabComplete(lineUpToCursor, getTargetBlockPos(), false));
        }
    }

    @Override
    public void setCompletions(String... newCompletions) {
        this.captured.clear();
        String[] fromClientCommands = ClientCommandHandler.instance.latestAutoComplete;
        if (fromClientCommands != null) {
            for (String s : fromClientCommands) {
                if (s != null && !s.isEmpty()) {
                    this.captured.add(s);
                }
            }
        }
        if (newCompletions != null) {
            for (String s : newCompletions) {
                if (s != null && !s.isEmpty()) {
                    this.captured.add(s);
                }
            }
        }
        this.fresh = true;
    }

    /** Celowo pusto - patrz javadoc klasy. */
    @Override
    public void complete() {
        // nic
    }

    @Override
    @Nullable
    public BlockPos getTargetBlockPos() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            return mc.objectMouseOver.getBlockPos();
        }
        return null;
    }

    /** {@code true} od przyjscia odpowiedzi do jej odebrania przez {@link #drain()}. */
    public boolean isFresh() {
        return this.fresh;
    }

    /** Oddaje przechwycone wartosci i kasuje flage swiezosci. */
    public List<String> drain() {
        this.fresh = false;
        return this.captured.isEmpty()
                ? Collections.<String>emptyList()
                : new ArrayList<String>(this.captured);
    }
}
