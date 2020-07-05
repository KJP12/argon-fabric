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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkThreadUtils.class)
public class MixinNetworkThreadUtils {
    @Shadow
    public native static <T extends PacketListener> void forceMainThread(Packet<T> packet, T listener, ThreadExecutor<?> executor) throws OffThreadException;

    @Inject(method = "forceMainThread(Lnet/minecraft/network/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/network/OffThreadException;INSTANCE:Lnet/minecraft/network/OffThreadException;"))
    private static void argon$actuallyUsefulException(CallbackInfo cbi) {
        // throw new RuntimeException("Off Thread Task");
    }

    /**
     * @reason It might as well be an overwrite; Use SubServer instead of Minecraft Server
     * @author kjp12
     */
    @Overwrite
    public static <T extends PacketListener> void forceMainThread(Packet<T> packet, T listener, ServerWorld world) throws OffThreadException {
        forceMainThread(packet, listener, ((SubServerWorld) world).getSubServer());
    }
}
