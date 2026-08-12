package com.spege.commandsuggest.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.spege.commandsuggest.config.CommandSuggestConfig;
import com.spege.commandsuggest.core.ArgType;
import com.spege.commandsuggest.core.Suggestion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Lista podpowiedzi rysowana nad polem czatu, plus jej nawigacja.
 *
 * <p>Geometria jest przypieta do {@code GuiChat.initGui()}, ktore tworzy pole jako
 * {@code new GuiTextField(0, fontRenderer, 4, height - 12, width - 4, 12)} i wylacza rysowanie
 * tla ({@code setEnableBackgroundDrawing(false)}) — a przy wylaczonym tle
 * {@code GuiTextField.drawTextBox} rysuje tekst od {@code x}, nie {@code x + 4}
 * ({@code int l = this.enableBackgroundDrawing ? this.x + 4 : this.x;}). Stad stala {@code 4}
 * ponizej: zweryfikowana na zrodlach, nie zgadywana. {@code GuiTextField.x} jest w rzeczywistosci
 * polem publicznym (nie prywatnym), ale ten popup i tak nie dostaje referencji do pola tekstowego
 * — sygnatura {@link #draw} celowo ogranicza sie do tego, co {@code ChatScreenHandler} juz ma pod
 * reka (szerokosc/wysokosc ekranu i tekst przed tokenem), zeby nie parowac tej klasy z konkretnym
 * {@code GuiTextField}.
 *
 * <p>Czarny pasek pola czatu zaczyna sie w {@code screenHeight - 14}
 * ({@code GuiChat.drawScreen}: {@code drawRect(2, height - 14, width - 2, height - 2, ...)}) —
 * lista i linia usage stoja NAD nim, linia usage bezposrednio nad paskiem, a lista nad linia usage.
 *
 * <p>Pomiar szerokosci tekstu jest cache'owany ({@link #dirty}), bo {@link #set} i {@link #append}
 * nie dostaja {@code FontRenderer} (nie ma go poza {@link #draw}) — przeliczenie maksymalnej
 * szerokosci wsrod (potencjalnie kilkuset) pozycji odbywa sie wiec najwyzej raz na zmiane tresci,
 * a nie w kazdej klatce.
 */
@SideOnly(Side.CLIENT)
public final class SuggestionPopup {

    private static final int PADDING_X = 4;
    private static final int MIN_WIDTH = 20;
    private static final int INPUT_BAR_TOP_OFFSET = 14; // GuiChat.drawScreen: height - 14

    private static final int COLOR_LITERAL = 0xFFFFFF;
    private static final int COLOR_GREY = 0xAAAAAA;
    private static final int COLOR_GREEN = 0x55FF55;
    private static final int COLOR_ORANGE = 0xFFAA00;
    private static final int COLOR_MAGENTA = 0xFF55FF;
    private static final int COLOR_CYAN = 0x55FFFF;
    private static final int COLOR_YELLOW = 0xFFFF55;
    private static final int COLOR_SELECTED_TEXT = 0xFFFFFF;
    private static final int COLOR_USAGE = 0x999999;
    private static final int COLOR_BACKGROUND = 0xC0101010;
    private static final int COLOR_HIGHLIGHT = 0x803A6EA5;

    private final List<Suggestion> items = new ArrayList<Suggestion>();
    private final Set<String> textsSeen = new HashSet<String>();

    private String usage;
    private int replaceStart;
    private int selected;
    private int scrollOffset;
    private boolean open;
    private boolean dirty;
    private int maxTextWidthPx;

    // Ostatnia narysowana geometria - jedyne zrodlo prawdy dla click(), bo mysz jest obslugiwana
    // poza draw(). Niewazna (geomValid == false), dopoki draw() nie narysuje choc jednego wiersza.
    private boolean geomValid;
    private int geomX;
    private int geomY;
    private int geomWidth;
    private int geomRowHeight;
    private int geomVisibleRows;

    /** Zastepuje cala zawartosc, resetuje zaznaczenie i przewiniecie. */
    public void set(List<Suggestion> newItems, String usageLine, int newReplaceStart) {
        this.items.clear();
        this.textsSeen.clear();
        if (newItems != null) {
            for (Suggestion s : newItems) {
                if (s != null && s.getText() != null && this.textsSeen.add(s.getText())) {
                    this.items.add(s);
                }
            }
        }
        this.usage = usageLine;
        this.replaceStart = newReplaceStart;
        this.selected = 0;
        this.scrollOffset = 0;
        this.dirty = true;
        this.geomValid = false;
        this.open = !this.items.isEmpty() || (usageLine != null && CommandSuggestConfig.showUsageLine);
    }

    /**
     * Dokleja pozycje bez ruszania zaznaczenia, przewiniecia ani linii usage — tak przychodzi
     * asynchroniczna odpowiedz serwera, kilka klatek po tym, jak popup jest juz na ekranie.
     * Teksty juz obecne sa pomijane.
     */
    public void append(List<Suggestion> extra) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        boolean added = false;
        for (Suggestion s : extra) {
            if (s != null && s.getText() != null && this.textsSeen.add(s.getText())) {
                this.items.add(s);
                added = true;
            }
        }
        if (added) {
            this.dirty = true;
            this.geomValid = false;
            if (!this.items.isEmpty()) {
                this.open = true;
            }
        }
    }

    public void close() {
        this.open = false;
        this.items.clear();
        this.textsSeen.clear();
        this.usage = null;
        this.selected = 0;
        this.scrollOffset = 0;
        this.dirty = true;
        this.geomValid = false;
    }

    public boolean isOpen() {
        return this.open;
    }

    @Nullable
    public Suggestion getSelected() {
        if (this.items.isEmpty() || this.selected < 0 || this.selected >= this.items.size()) {
            return null;
        }
        return this.items.get(this.selected);
    }

    public int getReplaceStart() {
        return this.replaceStart;
    }

    /** Klawisze strzalek - zawija sie na obu koncach. */
    public void move(int delta) {
        int n = this.items.size();
        if (n == 0) {
            return;
        }
        this.selected = Math.floorMod(this.selected + delta, n);
        ensureSelectedVisible();
    }

    /** Kolko myszy - przesuwa widok, nie zaznaczenie. */
    public void scrollBy(int delta) {
        int rows = visibleRowCount();
        if (rows <= 0) {
            return;
        }
        int maxScroll = Math.max(0, this.items.size() - rows);
        this.scrollOffset = clamp(this.scrollOffset + delta, 0, maxScroll);
    }

    /** {@code true}, gdy klikniecie trafilo w wiersz - i wtedy tez ustawia zaznaczenie. */
    public boolean click(int mouseX, int mouseY) {
        if (!this.open || !this.geomValid) {
            return false;
        }
        if (mouseX < this.geomX || mouseX >= this.geomX + this.geomWidth) {
            return false;
        }
        if (mouseY < this.geomY || mouseY >= this.geomY + this.geomRowHeight * this.geomVisibleRows) {
            return false;
        }
        int row = (mouseY - this.geomY) / this.geomRowHeight;
        int index = this.scrollOffset + row;
        if (index < 0 || index >= this.items.size()) {
            return false;
        }
        this.selected = index;
        return true;
    }

    public void draw(Minecraft mc, int screenWidth, int screenHeight, String textBeforeToken) {
        if (!this.open) {
            this.geomValid = false;
            return;
        }
        boolean showUsage = this.usage != null && CommandSuggestConfig.showUsageLine;
        int visibleItemRows = visibleRowCount();
        if (visibleItemRows == 0 && !showUsage) {
            this.geomValid = false;
            return;
        }

        FontRenderer fr = mc.fontRenderer;
        int rowH = fr.FONT_HEIGHT + 2;

        if (this.dirty) {
            int max = 0;
            for (Suggestion s : this.items) {
                int w = fr.getStringWidth(s.getText());
                if (w > max) {
                    max = w;
                }
            }
            this.maxTextWidthPx = max;
            this.dirty = false;
        }
        int usageWidth = showUsage ? fr.getStringWidth(this.usage) : 0;
        int boxWidth = Math.max(this.maxTextWidthPx, usageWidth) + PADDING_X * 2;
        if (boxWidth < MIN_WIDTH) {
            boxWidth = MIN_WIDTH;
        }

        int x = 4 + fr.getStringWidth(textBeforeToken == null ? "" : textBeforeToken);
        if (x + boxWidth > screenWidth) {
            x = screenWidth - boxWidth;
        }
        if (x < 0) {
            x = 0;
        }

        int inputTop = screenHeight - INPUT_BAR_TOP_OFFSET;
        int usageY = inputTop - rowH;
        int listBottom = showUsage ? usageY : inputTop;
        int listHeight = visibleItemRows * rowH;
        int listY = listBottom - listHeight;
        int boxTop = visibleItemRows > 0 ? listY : usageY;

        Gui.drawRect(x - PADDING_X, boxTop, x + boxWidth, inputTop, COLOR_BACKGROUND);

        for (int i = 0; i < visibleItemRows; i++) {
            int idx = this.scrollOffset + i;
            if (idx >= this.items.size()) {
                break;
            }
            Suggestion s = this.items.get(idx);
            int rowY = listY + i * rowH;
            boolean isSel = idx == this.selected;
            if (isSel) {
                Gui.drawRect(x - PADDING_X, rowY, x + boxWidth, rowY + rowH, COLOR_HIGHLIGHT);
            }
            int color = isSel ? COLOR_SELECTED_TEXT : colorFor(s.getType());
            fr.drawStringWithShadow(s.getText(), x, rowY + 1, color);
        }
        if (showUsage) {
            fr.drawStringWithShadow(this.usage, x, usageY + 1, COLOR_USAGE);
        }

        if (visibleItemRows > 0) {
            this.geomX = x - PADDING_X;
            this.geomY = listY;
            this.geomWidth = boxWidth;
            this.geomRowHeight = rowH;
            this.geomVisibleRows = visibleItemRows;
            this.geomValid = true;
        } else {
            this.geomValid = false;
        }
    }

    private void ensureSelectedVisible() {
        int rows = visibleRowCount();
        if (rows <= 0) {
            return;
        }
        if (this.selected < this.scrollOffset) {
            this.scrollOffset = this.selected;
        } else if (this.selected >= this.scrollOffset + rows) {
            this.scrollOffset = this.selected - rows + 1;
        }
        int maxScroll = Math.max(0, this.items.size() - rows);
        this.scrollOffset = clamp(this.scrollOffset, 0, maxScroll);
    }

    private int visibleRowCount() {
        return Math.min(this.items.size(), Math.max(1, CommandSuggestConfig.maxVisibleRows));
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int colorFor(ArgType type) {
        if (type == null) {
            return COLOR_LITERAL;
        }
        if (!CommandSuggestConfig.colourArgumentsByType) {
            return COLOR_GREY;
        }
        switch (type) {
            case PLAYER:
                return COLOR_GREEN;
            case ITEM:
            case BLOCK:
                return COLOR_ORANGE;
            case ENTITY:
                return COLOR_MAGENTA;
            case INT:
            case FLOAT:
            case DIMENSION:
                return COLOR_CYAN;
            case BOOL:
            case CHOICE:
                return COLOR_YELLOW;
            default:
                return COLOR_GREY;
        }
    }
}
