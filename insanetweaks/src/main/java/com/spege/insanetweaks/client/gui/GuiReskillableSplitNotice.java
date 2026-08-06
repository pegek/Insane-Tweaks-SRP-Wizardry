package com.spege.insanetweaks.client.gui;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ReskillableSplitNotice;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The screen that stands between the player and the main menu when the Reskillable trait module has
 * moved out from under them. Shown once per launch, before any world can be loaded — see
 * {@link ReskillableSplitNotice} for why that timing is the whole point here, more so than for the
 * Tombstone split: this loss comes with no Forge screen of its own.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}. Forge's SideTransformer makes merely
 * instantiating this on a dedicated server throw, so nothing outside a client-only class may
 * mention it — it is reached from {@code ReskillableSplitNoticeHandler}, which is itself client-only
 * and registered from {@code ClientProxy}.
 */
@SideOnly(Side.CLIENT)
public class GuiReskillableSplitNotice extends GuiScreen {

    private static final int BUTTON_DOWNLOAD = 0;
    private static final int BUTTON_CONTINUE = 1;
    private static final int BUTTON_NEVER = 2;

    private static final int TEXT_WIDTH = 400;

    private final GuiScreen parent;
    private final List<String> body = new ArrayList<String>();

    public GuiReskillableSplitNotice(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.body.clear();

        int wrap = Math.min(TEXT_WIDTH, this.width - 40);

        wrap(TextFormatting.WHITE + "The Reskillable trait integration is no longer part of "
                + "Insane Tweaks. As of " + InsaneTweaksMod.VERSION
                + " it is a separate mod, Reskill Tweaks.", wrap);
        this.body.add("");
        wrap(TextFormatting.RED + "Do not load your world until you have installed it.", wrap);
        this.body.add("");
        wrap(TextFormatting.GRAY + "The twenty traits this mod used to add are still registered "
                + "under their original names, but only by Reskill Tweaks. Without it, every trait "
                + "your players bought is dropped from their character the moment it loads - and the "
                + "skill points they paid are not returned.", wrap);
        this.body.add("");
        wrap(TextFormatting.GOLD + "There is no confirmation screen for this. Reskillable discards "
                + "an unlock it cannot resolve without saying anything, so the first sign is a "
                + "player asking where their traits went.", wrap);
        this.body.add("");
        wrap(TextFormatting.GRAY + "Everything else in Insane Tweaks is unaffected. If you never "
                + "used the trait module, you can dismiss this for good below.", wrap);

        String extracted = ReskillableSplitNotice.getExtractedPath();
        if (extracted != null) {
            this.body.add("");
            wrap(TextFormatting.DARK_GRAY + "Your old settings were copied to config/"
                    + new java.io.File(extracted).getName(), wrap);
        }

        int buttonY = this.height - 66;
        this.buttonList.add(new GuiButton(BUTTON_DOWNLOAD, this.width / 2 - 155, buttonY, 310, 20,
                "Open the Reskill Tweaks download page"));
        this.buttonList.add(new GuiButton(BUTTON_CONTINUE, this.width / 2 - 155, buttonY + 24, 152, 20,
                "Continue"));
        this.buttonList.add(new GuiButton(BUTTON_NEVER, this.width / 2 + 3, buttonY + 24, 152, 20,
                "Don't show this again"));
    }

    private void wrap(String text, int wrapWidth) {
        this.body.addAll(this.fontRenderer.listFormattedStringToWidth(text, wrapWidth));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        this.drawCenteredString(this.fontRenderer,
                TextFormatting.GOLD + "" + TextFormatting.BOLD + "Insane Tweaks "
                        + InsaneTweaksMod.VERSION,
                this.width / 2, 24, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer,
                TextFormatting.YELLOW + "The Reskillable traits have moved to their own mod",
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
            case BUTTON_DOWNLOAD:
                openDownloadPage();
                break;
            case BUTTON_NEVER:
                ReskillableSplitNotice.acknowledge();
                back();
                break;
            case BUTTON_CONTINUE:
            default:
                back();
                break;
        }
    }

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

    /**
     * Opens the download page in the system browser. The URL also goes on the clipboard
     * unconditionally: some launchers start the game headless enough that {@code java.awt.Desktop}
     * silently does nothing, and a player staring at a dead button has no way to find the link.
     */
    private void openDownloadPage() {
        String url = ReskillableSplitNotice.DOWNLOAD_URL;
        try {
            setClipboardString(url);
        } catch (Throwable ignored) {
            // Clipboard access is a courtesy, never a requirement.
        }
        try {
            // Reflection rather than a direct call: java.awt is not something to link against from
            // a class the game loads on every startup, and it is absent or headless on some setups.
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Object desktop = desktopClass.getMethod("getDesktop").invoke((Object) null);
            desktopClass.getMethod("browse", URI.class).invoke(desktop, new URI(url));
        } catch (Throwable t) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Could not open {} in a browser ({}). "
                    + "The address has been copied to the clipboard instead.", url, t.toString());
        }
    }
}
