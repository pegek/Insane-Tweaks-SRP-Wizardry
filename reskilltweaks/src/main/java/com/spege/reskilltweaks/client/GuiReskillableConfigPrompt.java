package com.spege.reskilltweaks.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.spege.reskilltweaks.ReskillTweaks;
import com.spege.reskilltweaks.config.ReskillableConfigSwapper;
import com.spege.reskilltweaks.config.TunedConfigMode;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Asks whether this mod may replace reskillable.cfg with its tuned version. Shown on the main menu
 * of a launch where the choice is still {@code ASK} and the tuned config has never been installed.
 *
 * <p>The answer cannot take effect in this session: Reskillable read reskillable.cfg during startup
 * and builds its unlockable costs from it only once. So the screen only records the choice
 * ({@link ReskillableConfigSwapper#choose}), and after {@code APPLY} it says plainly that the new
 * config arrives with the next launch and offers to quit now. {@code KEEP} needs no restart, so it
 * goes straight back to the menu.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}; only {@link ReskillableConfigPromptHandler},
 * itself client-only, may name it.
 */
@SideOnly(Side.CLIENT)
public class GuiReskillableConfigPrompt extends GuiScreen {

    private static final int BUTTON_APPLY = 0;
    private static final int BUTTON_KEEP = 1;
    private static final int BUTTON_LATER = 2;
    private static final int BUTTON_QUIT = 3;
    private static final int BUTTON_CONTINUE = 4;

    private static final int TEXT_WIDTH = 400;

    private final GuiScreen parent;
    private final List<String> body = new ArrayList<String>();

    /** Set once APPLY is chosen: the screen switches to the "restart to finish" state. */
    private boolean applied = false;

    public GuiReskillableConfigPrompt(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.body.clear();
        this.buttonList.clear();

        int wrap = Math.min(TEXT_WIDTH, this.width - 40);
        int buttonY = this.height - 66;

        if (this.applied) {
            wrap(TextFormatting.WHITE + "Done. The tuned reskillable.cfg will be installed the next "
                    + "time the game starts.", wrap);
            this.body.add("");
            wrap(TextFormatting.GOLD + "Until then Reskillable keeps running on the config it "
                    + "loaded at startup, so costs and locks in this session are still the old ones.",
                    wrap);

            this.buttonList.add(new GuiButton(BUTTON_QUIT, this.width / 2 - 155, buttonY + 24, 152, 20,
                    "Quit game now"));
            this.buttonList.add(new GuiButton(BUTTON_CONTINUE, this.width / 2 + 3, buttonY + 24, 152, 20,
                    "Continue to the menu"));
            return;
        }

        wrap(TextFormatting.WHITE + "Reskill Tweaks comes with a tuned reskillable.cfg: its own skill "
                + "costs, level requirements and item locks, balanced around the traits it adds.", wrap);
        this.body.add("");
        wrap(TextFormatting.WHITE + "Use it in place of your current reskillable.cfg?", wrap);
        this.body.add("");
        wrap(TextFormatting.GRAY + "Your current file is backed up first, and any Skill Locks you added "
                + "yourself are carried over into the new one.", wrap);
        this.body.add("");
        wrap(TextFormatting.GOLD + "Reskillable reads its config once, at startup - a change made here "
                + "takes effect on the next launch.", wrap);
        this.body.add("");
        wrap(TextFormatting.DARK_GRAY + "You can change your mind later: modules > Tuned Reskillable "
                + "Config in config/reskilltweaks.cfg.", wrap);

        this.buttonList.add(new GuiButton(BUTTON_APPLY, this.width / 2 - 155, buttonY, 152, 20,
                "Use the tuned config"));
        this.buttonList.add(new GuiButton(BUTTON_KEEP, this.width / 2 + 3, buttonY, 152, 20,
                "Keep my reskillable.cfg"));
        this.buttonList.add(new GuiButton(BUTTON_LATER, this.width / 2 - 155, buttonY + 24, 310, 20,
                "Ask me next time"));
    }

    private void wrap(String text, int wrapWidth) {
        this.body.addAll(this.fontRenderer.listFormattedStringToWidth(text, wrapWidth));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(this.fontRenderer,
                TextFormatting.GOLD + "" + TextFormatting.BOLD + ReskillTweaks.NAME + " " + ReskillTweaks.VERSION,
                this.width / 2, 24, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer,
                TextFormatting.YELLOW + "Replace reskillable.cfg with the tuned version?",
                this.width / 2, 38, 0xFFFFFF);

        int left = this.width / 2 - Math.min(TEXT_WIDTH, this.width - 40) / 2;
        int y = 62;
        for (String line : this.body) {
            this.fontRenderer.drawStringWithShadow(line, left, y, 0xFFFFFF);
            y += 10;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BUTTON_APPLY:
                ReskillableConfigSwapper.choose(TunedConfigMode.APPLY);
                this.applied = true;
                initGui();
                break;
            case BUTTON_KEEP:
                ReskillableConfigSwapper.choose(TunedConfigMode.KEEP);
                back();
                break;
            case BUTTON_QUIT:
                this.mc.shutdown();
                break;
            case BUTTON_LATER:
            case BUTTON_CONTINUE:
            default:
                back();
                break;
        }
    }

    /** Escape counts as "ask me next time" - it records nothing. */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Escape
            back();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void back() {
        this.mc.displayGuiScreen(this.parent);
    }
}
