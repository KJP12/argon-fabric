package net.kjp12.argon.utils;

import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import javax.annotation.Nullable;

public final class DonutCare implements WorldGenerationProgressListener {
    public static final DonutCare INSTANCE = new DonutCare();

    private DonutCare() {
    }

    @Override
    public void start(ChunkPos spawnPos) {
    }

    @Override
    public void setChunkStatus(ChunkPos pos, @Nullable ChunkStatus status) {
    }

    @Override
    public void stop() {
    }
}
