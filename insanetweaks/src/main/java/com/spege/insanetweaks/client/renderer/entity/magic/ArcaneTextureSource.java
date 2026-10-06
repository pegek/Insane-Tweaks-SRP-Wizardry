package com.spege.insanetweaks.client.renderer.entity.magic;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The skin SRP's renderer picked for this entity, which the glow layer re-binds. */
@SideOnly(Side.CLIENT)
public interface ArcaneTextureSource<T> {

    ResourceLocation arcaneTexture(T entity);
}
