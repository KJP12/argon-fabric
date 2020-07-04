package net.kjp12.argon.helpers;

import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Collection;

public interface IMinecraftServer {
    long getLastTimeReference();

    long getTimeReference();

    SubServer getSubServer(RegistryKey<World> key);

    Collection<SubServer> getSubServers();
}
