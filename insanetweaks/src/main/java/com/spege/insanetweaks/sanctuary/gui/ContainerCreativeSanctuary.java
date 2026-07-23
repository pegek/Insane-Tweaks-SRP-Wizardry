package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;

public class ContainerCreativeSanctuary extends Container {

    private final TileEntitySanctuaryCore te;
    private int lastRadius = -1;
    private int clientRadius = 0;

    public ContainerCreativeSanctuary(TileEntitySanctuaryCore te) {
        this.te = te;
    }

    public TileEntitySanctuaryCore getTe() { return te; }
    public int getClientRadius() { return clientRadius; }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendWindowProperty(this, 0, clampShort(te.getCreativeRadius()));
        lastRadius = te.getCreativeRadius();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int r = te.getCreativeRadius();
        if (r != lastRadius) {
            for (IContainerListener l : this.listeners) {
                l.sendWindowProperty(this, 0, clampShort(r));
            }
            lastRadius = r;
        }
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        if (id == 0) { clientRadius = data; }
    }

    private static int clampShort(int v) { return v < 0 ? 0 : (v > 32767 ? 32767 : v); }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.getWorld().getTileEntity(te.getPos()) == te
                && player.getDistanceSq(te.getPos()) <= 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }
}
