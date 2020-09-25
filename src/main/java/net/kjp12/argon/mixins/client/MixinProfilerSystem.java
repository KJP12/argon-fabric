package net.kjp12.argon.mixins.client;

import net.minecraft.util.profiler.ProfileLocationInfo;
import net.minecraft.util.profiler.ProfilerSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ProfilerSystem.class)
public abstract class MixinProfilerSystem {
    /**
     * coerce-type - {@link net.minecraft.util.profiler.ProfilerSystem.LocatedInfo} is not accessible outside of ProfilerSystem, resorting to applicable interface.
     * */
    @Shadow @Final @Mutable
    private Map<String, ProfileLocationInfo> locationInfos;

    /**
     * @reason The hashmap before it breaks due to the server writing to the hashmap at the same time the client's iterating over it.
     * @author KJP12
     * */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void argon$init(CallbackInfo cbi) {
        locationInfos = new ConcurrentHashMap<>();
    }
}
