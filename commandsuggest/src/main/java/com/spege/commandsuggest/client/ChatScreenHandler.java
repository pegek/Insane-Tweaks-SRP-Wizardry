package com.spege.commandsuggest.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.config.CommandSuggestConfig;
import com.spege.commandsuggest.core.ArgType;
import com.spege.commandsuggest.core.InputParser;
import com.spege.commandsuggest.core.ParsedInput;
import com.spege.commandsuggest.core.Suggestion;
import com.spege.commandsuggest.core.SuggestionEngine;
import com.spege.commandsuggest.core.Suggestions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Cztery eventy {@code GuiScreenEvent} plus podmiana {@code GuiChat.tabCompleter} - to jest caly
 * mod od strony gracza. Rejestrowany DOKLADNIE RAZ z {@code ClientProxy.init}.
 *
 * <p>🚨 Bez zadnego mixina: {@code InitGuiEvent.Post} podmienia pole refleksyjnie zamiast wchodzic
 * w {@code GuiChat}/{@code NetHandlerPlayClient} bajtkodem. Uzasadnienie podmiany w calosci -
 * patrz {@link SpyTabCompleter}.
 *
 * <p><b>Odlaczanie od zamknietego ekranu.</b> {@code Minecraft.displayGuiScreen(null)} (co robi
 * ESC w GuiChat i wyslanie wiadomosci) NIE odpala {@code InitGuiEvent} w ogole - vanilla wola
 * {@code initGui()} tylko gdy nowy ekran nie jest {@code null}. Nie ma wiec zadnego eventu, ktory
 * powiedzialby "czat sie zamknal". Nie jest to problem: {@code handleInput()}/{@code drawScreen()}
 * sa metodami instancyjnymi wolanymi WYLACZNIE przez {@code Minecraft} na {@code mc.currentScreen}
 * - jesli {@code mc.currentScreen} stanie sie {@code null}, te metody po prostu przestaja byc
 * wolane, wiec nasze trzy pozostale handlery (bramkowane przez {@code event.getGui() == this.screen})
 * cicho przestaja dzialac same z siebie. Referencje ({@code screen}/{@code inputField}/{@code spy})
 * zostaja "zawieszone" (nieosiagalne przez gre, ale nadal trzymane przez to pole) do czasu
 * nastepnego {@code InitGuiEvent.Post} - a wtedy albo to znowu {@code GuiChat} (podmieniamy od
 * nowa), albo inny ekran (galaz {@code else} czysci referencje). Ograniczony, samo-naprawiajacy
 * sie "wyciek" jednego obiektu na czas jednej sesji GUI - nie kumuluje sie.
 *
 * <p><b>Wykrywanie zmian w polu - zdarzeniowo, nie odpytywaniem.</b> Zmiany TEKSTU przychodza
 * przez {@link FieldResponder}, instalowany na {@code inputField.setGuiResponder(...)} w
 * {@link #onInitGuiPost}: {@code GuiTextField.setResponderEntryValue} jest wolane WYLACZNIE z
 * {@code writeText} i {@code deleteFromCursor} (zweryfikowane w zrodlach 1.12.2-14.23.5.2860),
 * wiec pisanie, kasowanie i wklejanie zawsze go odpalaja. Sam ruch KURSORA (strzalki, Home/End,
 * klik myszy w pole) nie idzie przez respondera w ogole - a to on decyduje, ktory token jest
 * edytowany - wiec {@link #onKeyboardPre} i {@link #onMousePre} doklejaja wlasne "dirty" dla
 * kazdego zdarzenia, ktorego same nie konsumuja. Rownolegle {@code setText} (nasz {@link #accept},
 * waniliowa historia czatu przez gora/dol) tez nie zglasza sie do respondera, wiec {@link #accept}
 * zaznacza dirty sam. Wynik: zero porownywania tekstu/kursora co klatke w {@link #onDrawScreenPost}.
 *
 * <p><b>Resize.</b> Zmiana rozmiaru okna tez odpala {@code InitGuiEvent.Post} - {@code GuiScreen.onResize}
 * wola {@code setWorldAndResolution}, ktore wola {@code initGui()} na TEJ SAMEJ instancji
 * {@code GuiChat}. Ale {@code GuiChat.initGui()} samo, bezwarunkowo, tworzy NOWE {@code inputField}
 * (tekst gracza jest przy tym gubiony - to zachowanie wanilii, nie nasze) i NOWY waniliowy
 * {@code tabCompleter}. Dlatego {@link #onInitGuiPost} NIE sprawdza "czy to ten sam ekran co
 * poprzednio" przed podmiana - podmienia BEZWARUNKOWO za kazdym razem, gdy {@code gui instanceof
 * GuiChat}, i zawsze zamyka popup - bo stary popup i tak wskazywalby na juz nieistniejace pole
 * tekstowe.
 *
 * <p><b>Kolizja z innym modem.</b> Gdyby inny mod (np. Chunk-Pregenerator) tez podmienial
 * {@code tabCompleter} z wlasnego {@code InitGuiEvent.Post}, a jego handler zarejestrowal sie
 * PO naszym (kolejnosc subskrybentow miedzy modami nie jest gwarantowana), jego podmiana
 * wygralaby - nasz {@link SpyTabCompleter} zostalby "sierota": wciaz istnieje, ale
 * {@code GuiChat.tabCompleter} juz na niego nie wskazuje, wiec {@code setCompletions} nigdy
 * wiecej sie na nim nie wywola. Objaw: podpowiedzi dla typow serwerowych (WORD/GREEDY/UNKNOWN)
 * po prostu nigdy nie doczekaja sie wartosci z serwera - bez wyjatku, bez linii w logu, popup po
 * prostu pokazuje tylko to, co rozwiazalismy lokalnie. Nie da sie tego wygrac deterministycznie
 * bez API priorytetu eventow, ktorego Forge tu nie daje miedzy modami - stad rejestracja z
 * {@code EventPriority.LOWEST} w {@code ClientProxy} (patrz tam), zeby zmniejszyc szanse na tego
 * rodzaju kolizje, a nie ja wykluczyc.
 */
@SideOnly(Side.CLIENT)
public final class ChatScreenHandler {

    private static final Field INPUT_FIELD_REF;
    private static final Field TAB_COMPLETER_REF;

    /**
     * {@code false} = albo lookup pol w statycznym bloku zawiodl, albo pozniejsza proba zapisu
     * przez refleksje rzucila w trakcie gry. W obu przypadkach mod cicho przestaje probowac na
     * reszte sesji - patrz {@link #logReflectionFailureOnce(Exception)}.
     */
    private static boolean reflectionOk;
    private static boolean reflectionFailureLogged;

    static {
        Field input = null;
        Field completer = null;
        boolean ok;
        try {
            // ObfuscationReflectionHelper.findField(Class, String) bierze WYLACZNIE nazwe SRG i
            // sam remapuje ja na MCP w srodowisku dev (FMLDeobfuscatingRemapper) - w odroznieniu
            // od ReflectionHelper.findField(Class, String, String) nie jest oznaczona @Deprecated
            // (deprecacja siedzi na calej klasie ReflectionHelper, nie na tym jednym przeciazeniu,
            // ktore ObfuscationReflectionHelper i tak wywoluje pod spodem).
            input = ObfuscationReflectionHelper.findField(GuiChat.class, "field_146415_a");
            completer = ObfuscationReflectionHelper.findField(GuiChat.class, "field_184096_i");
            ok = true;
        } catch (RuntimeException e) {
            CommandSuggest.LOGGER.error(
                    "Nie udalo sie znalezc pol GuiChat.inputField/tabCompleter przez refleksje - "
                    + "podpowiedzi komend wylaczone na ta sesje gry.", e);
            ok = false;
        }
        INPUT_FIELD_REF = input;
        TAB_COMPLETER_REF = completer;
        reflectionOk = ok;
    }

    private final SuggestionPopup popup = new SuggestionPopup();

    @Nullable
    private GuiChat screen;
    @Nullable
    private GuiTextField inputField;
    @Nullable
    private SpyTabCompleter spy;

    private long lastChangeAtMs;
    private boolean recomputePending;

    /** Kontekst ostatniego {@code spy.request(...)} - patrz {@link #drainServerResponse}. */
    private int pendingRequestReplaceStart = -1;
    @Nullable
    private ArgType pendingRequestArgType;

    // LOWEST: chcemy byc OSTATNIM handlerem Post w tym baniu eventu, zeby ewentualna wlasna
    // podmiana tabCompletera przez inny mod (patrz javadoc klasy, "Kolizja z innym modem") zostala
    // nadpisana przez nasza, a nie odwrotnie. Nie daje to gwarancji przeciw innemu modowi, ktory
    // tez zada LOWEST, ale zmniejsza szanse kolizji bez API priorytetu miedzy-modowego.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInitGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (gui != this.screen) {
            detach();
        }
        if (!(gui instanceof GuiChat) || !CommandSuggestConfig.enabled || !reflectionOk) {
            return;
        }

        GuiChat chat = (GuiChat) gui;
        GuiTextField field = readInputField(chat);
        if (field == null) {
            return;
        }
        SpyTabCompleter newSpy = new SpyTabCompleter(field);
        if (!writeTabCompleter(chat, newSpy)) {
            return;
        }

        // 🚨 To CELOWO nadpisuje kazdy responder juz zainstalowany na tym polu - na Cleanroomie to
        // wlasny SuggestionUpdater loadera, ktory binpatch GuiChat.initGui wpina wczesniej. My
        // rejestrujemy sie na Post, wiec wygrywamy. Efekt uboczny (rowniez celowy): ich refresh()
        // przestaje sie wywolywac, ich lastRequest zostaje pusty, wiec kazda odpowiedz serwera na
        // ich CPacketTabComplete odpada na bramce onServerCompletions i ich popup sam sie wylacza -
        // inaczej dwa popupy rysowalyby sie w tym samym miejscu. Nie doklejamy sie do istniejacego
        // respondera (chain) - to przywrociloby ich liste. Pelna analiza: spec §12.
        field.setGuiResponder(new FieldResponder(this));

        this.screen = chat;
        this.inputField = field;
        this.spy = newSpy;
        this.popup.close();
        // Wymusza pierwsze przeliczenie tego ekranu bez czekania na jakikolwiek input - inaczej
        // czat otwarty z gotowym tekstem (np. klawisz "/" -> GuiChat("/")) nie pokazalby popupu,
        // dopoki gracz czegos nie wcisnie: setText() (ktorym GuiChat.initGui wypelnia pole) nie
        // przechodzi przez respondera.
        markDirty();
        this.pendingRequestReplaceStart = -1;
        this.pendingRequestArgType = null;
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!CommandSuggestConfig.enabled || event.getGui() != this.screen || this.inputField == null) {
            return;
        }
        String text = this.inputField.getText();
        int cursor = this.inputField.getCursorPosition();

        // Zero porownywania tekstu/kursora tutaj - "dirty" jest ustawiane zdarzeniowo przez
        // FieldResponder / onKeyboardPre / onMousePre / accept(), patrz javadoc klasy. Ta metoda
        // tylko realizuje debounce na juz zaznaczonej fladze.
        if (this.recomputePending
                && System.currentTimeMillis() - this.lastChangeAtMs >= CommandSuggestConfig.recomputeDebounceMs) {
            recompute(text, cursor);
            this.recomputePending = false;
        }

        if (this.spy != null && this.spy.isFresh()) {
            drainServerResponse(text, cursor);
        }

        Minecraft mc = Minecraft.getMinecraft();
        this.popup.draw(mc, this.screen.width, this.screen.height, textBeforeToken(text));
    }

    @SubscribeEvent
    public void onKeyboardPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!CommandSuggestConfig.enabled || event.getGui() != this.screen) {
            return;
        }
        if (!Keyboard.getEventKeyState()) {
            // zwolnienie klawisza - nie interesuje nas, tylko wcisniecie
            return;
        }
        int key = Keyboard.getEventKey();

        // 🚨 TAB jest anulowany ZAWSZE, popup otwarty czy nie - w przeciwnym razie dojdzie do
        // GuiChat.keyTyped -> tabCompleter.complete(), ktore na SpyTabCompleter jest pustym
        // no-opem (patrz jego javadoc), wiec gracz dostalby TAB, ktory nic nie robi ZAMIAST
        // waniliowego dopelnienia. Anulowanie jest tez tym, co usuwa waniliowy dopisek przecinkowy
        // do czatu, bo ten kod tkwi w GuiChat.ChatTabCompleter.complete(), ktorego juz nie wolamy.
        if (key == Keyboard.KEY_TAB) {
            if (this.popup.isOpen()) {
                accept(this.popup.getSelected());
            }
            event.setCanceled(true);
            return;
        }

        if (!this.popup.isOpen()) {
            // Nieskonsumowany klawisz trafi do waniliowego GuiChat.keyTyped. Pisanie/kasowanie/
            // wklejanie i tak przejdzie przez writeText/deleteFromCursor -> FieldResponder, ale
            // sam ruch kursora (strzalki, Home/End, Ctrl+A) nie zglasza sie do respondera wcale -
            // wiec zaznaczamy dirty tutaj bezwarunkowo (nadmiarowe w pierwszym przypadku, ale tanie).
            markDirty();
            return;
        }
        if (key == Keyboard.KEY_UP) {
            this.popup.move(-1);
            event.setCanceled(true);
        } else if (key == Keyboard.KEY_DOWN) {
            this.popup.move(1);
            event.setCanceled(true);
        } else if (key == Keyboard.KEY_ESCAPE) {
            // pierwszy Escape zamyka TYLKO popup (anulujemy - wanilia nie zobaczy tego wcisniecia);
            // drugi Escape trafia juz na zamkniety popup, wiec nie wchodzimy tu wcale i wanilia
            // zamyka caly czat.
            this.popup.close();
            event.setCanceled(true);
        } else {
            // Nieobsluzony przez nas klawisz z otwartym popupem (dalsze pisanie, lewo/prawo,
            // Home/End, backspace, Enter...) - przejdzie do vanilla, ta sama logika co wyzej.
            // Enter CELOWO nie jest przechwytywany - patrz javadoc klasy/spec zadania 16.
            markDirty();
        }
    }

    @SubscribeEvent
    public void onMousePre(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!CommandSuggestConfig.enabled || event.getGui() != this.screen) {
            return;
        }
        if (!this.popup.isOpen()) {
            // Popup zamkniety, ale klik wciaz moze przesunac kursor w polu (GuiTextField.mouseClicked
            // przez GuiChat.mouseClicked) - to repozycjonowanie nie idzie przez respondera (patrz
            // javadoc klasy), wiec zaznaczamy dirty sami, bezwarunkowo dla kazdego zdarzenia myszy.
            markDirty();
            return;
        }
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            this.popup.scrollBy(dWheel > 0 ? -1 : 1);
            event.setCanceled(true);
            return;
        }
        if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
            GuiScreen gui = event.getGui();
            Minecraft mc = Minecraft.getMinecraft();
            int mx = Mouse.getEventX() * gui.width / mc.displayWidth;
            int my = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
            if (this.popup.click(mx, my)) {
                accept(this.popup.getSelected());
                event.setCanceled(true);
                return;
            }
        }
        // Zdarzenie nie zostalo skonsumowane (klik poza lista podpowiedzi, inny przycisk, zwolnienie
        // przycisku) - moze dotrzec do pola tekstowego (np. przesuniecie kursora klikiem obok listy),
        // wiec zaznaczamy dirty jak w galezi "popup zamkniety" powyzej.
        markDirty();
    }

    private void detach() {
        // setGuiResponder(null) jest wspierana wartoscia, nie hackiem -
        // GuiTextField.setResponderEntryValue null-checkuje pole przed uzyciem. Pole tekstowe i
        // tak zaraz przestaje istniec (GuiChat.initGui tworzy nowe przy resize/reopen), ale
        // odpinamy jawnie, zeby nie zostawiac respondera wskazujacego na martwy ekran.
        if (this.inputField != null) {
            this.inputField.setGuiResponder(null);
        }
        this.screen = null;
        this.inputField = null;
        this.spy = null;
        this.popup.close();
    }

    /**
     * Zaznacza, ze pole tekstowe zmienilo sie od ostatniego przeliczenia (tekst LUB kursor) -
     * jedyne miejsce, ktore dotyka {@link #recomputePending}/{@link #lastChangeAtMs}. Wywolywane
     * z {@link FieldResponder} (zmiana tekstu), z {@link #onKeyboardPre}/{@link #onMousePre} (ruch
     * kursora, ktorego responder nie zglasza) i z {@link #accept} ({@code setText} tez go nie
     * zglasza). Debounce sam w sobie zyje w {@link #onDrawScreenPost}.
     */
    private void markDirty() {
        this.recomputePending = true;
        this.lastChangeAtMs = System.currentTimeMillis();
    }

    private void recompute(String text, int cursor) {
        ParsedInput parsed = InputParser.parse(text, cursor);
        if (!parsed.isCommand()) {
            this.popup.close();
            this.pendingRequestReplaceStart = -1;
            this.pendingRequestArgType = null;
            return;
        }

        Suggestions sugg = SuggestionEngine.suggest(ClientTreeCache.get(), parsed);
        List<Suggestion> items = new ArrayList<Suggestion>(sugg.getItems());
        Set<String> seen = new HashSet<String>();
        for (Suggestion s : items) {
            if (s.getText() != null) {
                seen.add(s.getText().toLowerCase(Locale.ROOT));
            }
        }

        // Komendy klienckie Forge nie siedza w drzewie serwera - CommandHandler serwera ich nie
        // widzi - wiec przy edycji pierwszego tokenu doklejamy je z osobna.
        if (parsed.getEditIndex() == 0) {
            String prefix = sugg.getPrefix();
            for (String name : ClientCommandHandler.instance.getCommands().keySet()) {
                if (matchesPrefix(name, prefix) && seen.add(name.toLowerCase(Locale.ROOT))) {
                    items.add(new Suggestion(name, null));
                }
            }
        }

        ArgType type = sugg.getArgType();
        if (type != null && !type.isServerResolved()) {
            for (String v : LocalValueSource.resolve(type, sugg.getPrefix())) {
                if (seen.add(v.toLowerCase(Locale.ROOT))) {
                    items.add(new Suggestion(v, type));
                }
            }
        }

        String usage = sugg.getUsage();
        if (usage != null) {
            // TreeBuilder wysyla ICommand.getUsage() - to zwykle klucz tlumaczenia, a I18n.format
            // oddaje nieznany klucz bez zmian, wiec dziala i dla gotowej linii, i dla klucza.
            usage = I18n.format(usage);
        }
        this.popup.set(items, usage, sugg.getReplaceStart());

        if (sugg.needsServerQuery() && this.spy != null) {
            this.spy.request(text.substring(0, cursor));
            this.pendingRequestReplaceStart = sugg.getReplaceStart();
            this.pendingRequestArgType = type;
        } else {
            this.pendingRequestReplaceStart = -1;
            this.pendingRequestArgType = null;
        }
    }

    /**
     * Odpowiedz serwera przychodzi kilka klatek po tym, jak popup jest juz na ekranie - w
     * miedzyczasie gracz mogl przejsc na inny token (edit index sie przesunal) albo rozszerzyc
     * ten sam prefiks o kolejne znaki. Dwa zabezpieczenia przed pokazaniem nieaktualnych wartosci:
     * (1) {@code pendingRequestReplaceStart} musi wciaz zgadzac sie z {@code popup.getReplaceStart()}
     * - inaczej popup juz dotyczy zupelnie innego tokenu i cala odpowiedz jest odrzucana; (2)
     * kazda pozycja jest jeszcze raz filtrowana wzgledem BIEZACEGO prefiksu w polu tekstowym, nie
     * tego sprzed wyslania zapytania - bo prefiks mogl urosnac (np. "st" -> "ste") i czesc starych
     * trafien juz nie pasuje.
     */
    private void drainServerResponse(String text, int cursor) {
        List<String> drained = this.spy.drain();
        if (drained.isEmpty() || this.pendingRequestReplaceStart < 0) {
            return;
        }
        if (this.pendingRequestReplaceStart != this.popup.getReplaceStart()) {
            return;
        }
        ParsedInput parsed = InputParser.parse(text, cursor);
        String curPrefix = parsed.isCommand() ? parsed.getPrefix() : "";
        List<Suggestion> converted = new ArrayList<Suggestion>();
        for (String v : drained) {
            if (matchesPrefix(v, curPrefix)) {
                converted.add(new Suggestion(v, this.pendingRequestArgType));
            }
        }
        this.popup.append(converted);
    }

    private void accept(@Nullable Suggestion s) {
        if (s == null || s.getText() == null || this.inputField == null) {
            return;
        }
        String text = this.inputField.getText();
        int cursor = this.inputField.getCursorPosition();
        int start = clampToLength(this.popup.getReplaceStart(), text.length());
        int end = clampToLength(cursor, text.length());
        if (start > end) {
            start = end;
        }
        String replacement = s.getText();
        String newText = text.substring(0, start) + replacement + text.substring(end);
        this.inputField.setText(newText);
        this.inputField.setCursorPosition(start + replacement.length());
        this.popup.close();
        // setText() nie zglasza sie do respondera (patrz javadoc klasy) - wymuszamy przeliczenie
        // na nastepnej klatce sami.
        markDirty();
    }

    private String textBeforeToken(String text) {
        int rs = clampToLength(this.popup.getReplaceStart(), text.length());
        return text.substring(0, rs);
    }

    private static int clampToLength(int value, int length) {
        if (value < 0) {
            return 0;
        }
        return value > length ? length : value;
    }

    private static boolean matchesPrefix(String candidate, String prefix) {
        if (candidate == null) {
            return false;
        }
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        return candidate.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    @Nullable
    private static GuiTextField readInputField(GuiChat chat) {
        try {
            return (GuiTextField) INPUT_FIELD_REF.get(chat);
        } catch (ReflectiveOperationException e) {
            logReflectionFailureOnce(e);
            return null;
        }
    }

    private static boolean writeTabCompleter(GuiChat chat, SpyTabCompleter spy) {
        try {
            TAB_COMPLETER_REF.set(chat, spy);
            return true;
        } catch (ReflectiveOperationException e) {
            logReflectionFailureOnce(e);
            return false;
        }
    }

    private static void logReflectionFailureOnce(Exception e) {
        reflectionOk = false;
        if (!reflectionFailureLogged) {
            reflectionFailureLogged = true;
            CommandSuggest.LOGGER.error(
                    "Podmiana GuiChat.tabCompleter zawiodla w trakcie gry - "
                    + "podpowiedzi komend wylaczone na ta sesje gry.", e);
        }
    }

    /**
     * Instalowany na {@code inputField.setGuiResponder(...)} w {@link #onInitGuiPost}. Jedyna
     * przeciazona metoda, ktora {@code GuiTextField} kiedykolwiek faktycznie wola, to
     * {@link #setEntryValue(int, String)} - patrz {@code GuiTextField.setResponderEntryValue},
     * ktore samo jest wolane wylacznie z {@code writeText} i {@code deleteFromCursor}.
     */
    @SideOnly(Side.CLIENT)
    private static final class FieldResponder implements GuiPageButtonList.GuiResponder {

        private final ChatScreenHandler handler;

        FieldResponder(ChatScreenHandler handler) {
            this.handler = handler;
        }

        @Override
        public void setEntryValue(int id, String value) {
            this.handler.markDirty();
        }

        @Override
        public void setEntryValue(int id, boolean value) {
            // GuiTextField nigdy nie zglasza tej przeciazonej wersji - zglasza wylacznie String
            // (patrz setResponderEntryValue). Puste celowo, nie przeoczenie.
        }

        @Override
        public void setEntryValue(int id, float value) {
            // Jak wyzej - GuiTextField nie uzywa tej przeciazonej wersji.
        }
    }
}
