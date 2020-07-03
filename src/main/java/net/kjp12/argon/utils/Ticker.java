package net.kjp12.argon.utils;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class Ticker {
    private Queue<Entity> entityQueue = new ArrayDeque<>();
    private Queue<BlockEntity> blockEntityQueue = new ArrayDeque<>();
    private List<Entity>[] entities = new List[]{new ArrayList<Entity>()};
    private List<BlockEntity> blockEntities = new ArrayList<>();

}
