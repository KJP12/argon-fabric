package net.kjp12.argon.mixins.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kjp12.argon.helpers.IMinecraftServer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

import static net.kjp12.argon.Argon.*;

@Mixin(net.minecraft.client.Keyboard.class)
@Environment(EnvType.CLIENT)
public class ArgonKeyboard {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "processF3(I)Z", at = @At(value = "TAIL"), cancellable = true)
    private void argon$processProfiler(int key, CallbackInfoReturnable<Boolean> cbir) {
        if(key >= 48 && key <= 57) {
            var server = client.getServer();
            if(server != null) {
                if(key == 48) {
                    profileResult = null;
                    profileSection = "root";
                    if(serverToProfile != null) {
                        serverToProfile.disableProfiler();
                        serverToProfile = null;
                    } else if(debugInternalServer ^= true) {
                        server.enableProfiler();
                    } else {
                        server.stopDebug();
                    }
                } else {
                    var old = serverToProfile;
                    if(old != null) old.disableProfiler();
                    // TODO: Is there a better way to do this?
                    var arr = new ArrayList<>(((IMinecraftServer) server).getSubServers());
                    var ind = key - 49;
                    if(ind >= arr.size()) return;
                    serverToProfile = arr.get(ind);
                    if(old != serverToProfile) {
                        serverToProfile.enableProfiler();
                    } else {
                        serverToProfile = null;
                    }
                    profileResult = null;
                    profileSection = "root";
                }
                cbir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "onKey(JIIII)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;debugProfilerEnabled:Z", ordinal = 1, shift = At.Shift.BEFORE))
    private void argon$onKey$ProfilerHandler(long id, int key, int scancode, int i, int j, CallbackInfo cbi) {
        if(serverToProfile != null && key >= 48 && key <= 57) {
            handleProfilerKeyPress(key - 48);
        }
    }
}
