package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Modem (com ou sem fio): uma placa colada em outro bloco. FACING aponta para o bloco onde está preso. */
public class ModemBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape DOWN = Block.box(0, 0, 0, 16, 6, 16);
    private static final VoxelShape UP = Block.box(0, 10, 0, 16, 16, 16);
    private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, 6);
    private static final VoxelShape SOUTH = Block.box(0, 0, 10, 16, 16, 16);
    private static final VoxelShape WEST = Block.box(0, 0, 0, 6, 16, 16);
    private static final VoxelShape EAST = Block.box(10, 0, 0, 16, 16, 16);

    private final boolean wired;

    public ModemBlock(Properties properties, boolean wired) {
        super(properties);
        this.wired = wired;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    public boolean isWired() { return wired; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ModemBlockEntity(pos, state);
    }
}
