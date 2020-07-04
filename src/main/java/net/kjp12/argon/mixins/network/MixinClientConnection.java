package net.kjp12.argon.mixins.network;

import io.netty.channel.SimpleChannelInboundHandler;
import net.kjp12.argon.helpers.IClientConnection;
import net.kjp12.argon.helpers.SubServer;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(ClientConnection.class)
public abstract class MixinClientConnection extends SimpleChannelInboundHandler<Packet<?>> implements IClientConnection {
    private AtomicReference<SubServer> world = new AtomicReference<>();

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
