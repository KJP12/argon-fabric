package net.kjp12.argon.mixins;

import net.kjp12.argon.helpers.SubServerWorld;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.OffThreadException;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NetworkThreadUtils.class)
public class MixinNetworkThreadUtils {
    @Shadow
    public native static <T extends PacketListener> void forceMainThread(Packet<T> packet, T listener, ThreadExecutor<?> executor) throws OffThreadException;

    /**
     * @reason It might as well be an overwrite; Use SubServer instead of Minecraft Server
     * @author kjp12
     */
    @Overwrite
    public static <T extends PacketListener> void forceMainThread(Packet<T> packet, T listener, ServerWorld world) throws OffThreadException {
        forceMainThread(packet, listener, ((SubServerWorld) world).getSubServer());
    }
}
