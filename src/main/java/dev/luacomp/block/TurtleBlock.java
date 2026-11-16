package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Turtle: clique abre o terminal; Shift+clique com item na mão coloca no inventário;
 * Shift+clique com a mão vazia devolve o primeiro item.
 */
public class TurtleBlock extends ComputerBlock {
    public TurtleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // a frente do turtle aponta para onde o jogador olha
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurtleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.TURTLE_BE.get(), ComputerBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
            if (!level.isClientSide) {
                ItemStack held = player.getItemInHand(hand);
                if (!held.isEmpty()) {
                    ItemStack rest = ItemHandlerHelper.insertItem(turtle.getInventory(), held.copy(), false);
                    if (!player.getAbilities().instabuild) player.setItemInHand(hand, rest);
                } else {
                    for (int i = 0; i < TurtleBlockEntity.SLOTS; i++) {
                        ItemStack s = turtle.getInventory().extractItem(i, 64, false);
                        if (!s.isEmpty()) {
                            if (!player.getInventory().add(s)) player.drop(s, false);
                            break;
                        }
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TurtleBlockEntity turtle) {
            turtle.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
