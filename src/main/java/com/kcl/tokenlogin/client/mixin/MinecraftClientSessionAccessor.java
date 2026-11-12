package com.kcl.tokenlogin.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientSessionAccessor {
    @Accessor("session")
    Session tokenlogin$getSession();

    @Mutable
    @Accessor("session")
    void tokenlogin$setSession(Session session);
}
