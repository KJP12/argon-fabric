package net.kjp12.argon.mixins.network;

import io.netty.channel.Channel;
import io.netty.channel.SimpleChannelInboundHandler;
import net.kjp12.argon.helpers.IClientConnection;
import net.kjp12.argon.helpers.SubServer;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(ClientConnection.class)
public abstract class MixinClientConnection extends SimpleChannelInboundHandler<Packet<?>> implements IClientConnection {
    @Shadow
    private PacketListener packetListener;
    @Shadow
    private Channel channel;
    @Shadow
    private int ticks;
    @Shadow
    private float avgPacketsSent;
    @Shadow
    private int packetsSentCounter;
    @Shadow
    private int packetsReceivedCounter;
    @Shadow
    private float avgPacketsReceived;

    @Shadow
    protected abstract void sendQueuedPackets();

    private AtomicReference<SubServer> world = new AtomicReference<>();

    /*/**
     * @reason Overwritten method is evil. (Concurrency with a slight change.)
     * @author KJP12
     * */
    /*@Overwrite
    public void tick() {
        sendQueuedPackets();

        if (packetListener instanceof ServerLoginNetworkHandler) {
            ((ServerLoginNetworkHandler)this.packetListener).tick();
        } else // This ELSE is important. When the packet listener switches from login to play, it immediately hangs
               // against the subserver on chunk fetching.
            if (this.packetListener instanceof ServerPlayNetworkHandler) {
            ((ServerPlayNetworkHandler)this.packetListener).tick();
        }

        if (channel != null) {
            channel.flush();
        }

        if (ticks++ % 20 == 0) {
            avgPacketsSent = avgPacketsSent * 0.75F + (float)packetsSentCounter * 0.25F;
            avgPacketsReceived = avgPacketsReceived * 0.75F + (float)packetsReceivedCounter * 0.25F;
            packetsSentCounter = 0;
            packetsReceivedCounter = 0;
        }

    }*/

    @Override
    public boolean isOwnedByWorld() {
        return world.getOpaque() != null;
    }

    @Override
    public SubServer getOwner() {
        return world.get();
    }

    @Override
    public void setOwner(SubServer world) {
        this.world.set(world);
    }
}
