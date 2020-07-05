package net.kjp12.argon.mixins.world;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ChunkTicketManager.class)
public class MixinChunkTicketManager {
    @Inject(method = "handleChunkLeave", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectSet;remove(Ljava/lang/Object;)Z"), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    private void argon$removeAsshatNPE(ChunkSectionPos pos, ServerPlayerEntity player, CallbackInfo cbi, long l, ObjectSet<?> idc) {
        if (idc == null) cbi.cancel();
    }
}
