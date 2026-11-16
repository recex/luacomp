package dev.luacomp.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Impressora: clique com papel ou corante abastece; mão vazia mostra os níveis;
 * Shift + mão vazia recolhe as páginas impressas.
 */
public class PrinterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public PrinterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrinterBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof PrinterBlockEntity printer)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            ItemStack rest = ItemHandlerHelper.insertItem(printer.getInventory(), held.copy(), false);
            if (rest.getCount() != held.getCount()) {
                if (!player.getAbilities().instabuild) player.setItemInHand(hand, rest);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            boolean any = false;
            for (int slot = PrinterBlockEntity.OUT_FIRST; slot <= PrinterBlockEntity.OUT_LAST; slot++) {
                ItemStack out = printer.getInventory().getStackInSlot(slot);
                if (!out.isEmpty()) {
                    printer.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                    if (!player.getInventory().add(out)) player.drop(out, false);
                    any = true;
                }
            }
            if (!any) player.displayClientMessage(Component.literal("Nenhuma página pronta."), true);
        } else {
            player.displayClientMessage(Component.literal(
                    "Papel: " + printer.getPaperCount() + "  Tinta: " + printer.getInkTotal() + " páginas"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PrinterBlockEntity printer) {
            printer.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
