package necek.development.autolever2.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class MainClient implements ClientModInitializer {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<BlockPos, Block> previousBlockStates = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player != null && client.world != null) {
            PlayerEntity player = client.player;
            BlockPos playerPos = player.getBlockPos();

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos checkPos = playerPos.add(x, y, z);
                        BlockState currentState = client.world.getBlockState(checkPos);
                        Block currentBlock = currentState.getBlock();

                        Block previousBlock = previousBlockStates.get(checkPos);

                        if (currentBlock == Blocks.WATER &&
                                currentState.getFluidState().isStill() &&
                                previousBlock != null &&
                                previousBlock != Blocks.WATER) {

                            if (player.getOffHandStack().getItem() == Items.LEVER) {
                                placeLever(checkPos);
                            }
                        }

                        previousBlockStates.put(checkPos.toImmutable(), currentBlock);
                    }
                }
            }

            previousBlockStates.keySet().removeIf(pos ->
                    Math.abs(pos.getX() - playerPos.getX()) > 2 ||
                            Math.abs(pos.getY() - playerPos.getY()) > 2 ||
                            Math.abs(pos.getZ() - playerPos.getZ()) > 2
            );
        }
    }

    private void placeLever(BlockPos pos) {
        if (canPlaceBlock(pos, true)) {
            Vec3d hitPos = Vec3d.ofCenter(pos);
            BlockPos neighbour;
            Direction side = getPlaceSide(pos);

            if (side == null) {
                side = Direction.UP;
                neighbour = pos;
            } else {
                neighbour = pos.offset(side);
                hitPos = hitPos.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            }

            BlockHitResult blockHitResult = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
            interact(blockHitResult, Hand.OFF_HAND, true);
        }
    }

    private boolean canPlaceBlock(BlockPos blockPos, boolean checkEntities) {
        if (blockPos == null) return false;
        if (!World.isValid(blockPos)) return false;
        if (!mc.world.getBlockState(blockPos).isOf(Blocks.WATER)) return false;
        return !checkEntities || mc.world.canPlace(Blocks.LEVER.getDefaultState(), blockPos, ShapeContext.absent());
    }

    private void interact(BlockHitResult blockHitResult, Hand hand, boolean swing) {
        boolean wasSneaking = mc.player.isSneaking();
        mc.player.setSneaking(false);

        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, blockHitResult);

        if (result.isAccepted()) {
            if (swing) mc.player.swingHand(hand);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        }

        mc.player.setSneaking(wasSneaking);
    }

    private Direction getPlaceSide(BlockPos blockPos) {
        Vec3d lookVec = blockPos.toCenterPos().subtract(mc.player.getEyePos());
        double bestRelevancy = -Double.MAX_VALUE;
        Direction bestSide = null;

        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.offset(side);
            BlockState state = mc.world.getBlockState(neighbor);

            if (state.isAir() || isClickable(state.getBlock())) continue;
            if (!state.getFluidState().isEmpty()) continue;

            double relevancy = side.getAxis().choose(lookVec.getX(), lookVec.getY(), lookVec.getZ()) * side.getDirection().offset();
            if (relevancy > bestRelevancy) {
                bestRelevancy = relevancy;
                bestSide = side;
            }
        }

        return bestSide;
    }

    private boolean isClickable(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof ButtonBlock
                || block instanceof BlockWithEntity
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof TrapdoorBlock
                || block instanceof FenceGateBlock;
    }
}