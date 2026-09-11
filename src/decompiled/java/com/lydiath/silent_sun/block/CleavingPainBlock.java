package com.lydiath.silent_sun.block;

import com.lydiath.silent_sun.registry.ModBlockEntities;
import com.lydiath.silent_sun.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 裂解之痛：莱德厄斯召唤器基底。像炼药锅一样装岩浆/水：
 * <ul>
 *   <li>岩浆桶右键 → 装岩浆；</li>
 *   <li>水桶右键 → 装水；</li>
 *   <li>岩浆状态投入钻石 → 自动雷击、清除并掉落召唤器（见 {@link CleavingPainBlockEntity#tick}）；</li>
 *   <li>水状态手持「莱德厄斯召唤器」右键 → 清除水并召唤 Boss（见 {@link CleavingPainBlockEntity#trySummon}）。</li>
 * </ul>
 */
public class CleavingPainBlock extends BaseEntityBlock {

    /** 盛放液体状态（2026-09-08 用户裁决，参考炼药锅）：0=无 / 1=岩浆 / 2=水。 */
    public static final IntegerProperty FLUID = IntegerProperty.create("fluid", 0, 2);

    public CleavingPainBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FLUID, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FLUID);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(CleavingPainBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CleavingPainBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.CLEAVING_PAIN.get(), CleavingPainBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CleavingPainBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 手持莱德厄斯召唤器右键 → 倒水后召唤 Boss
        if (stack.is(ModItems.REDIOS_SIGIL.get())) {
            return be.trySummon(player, level, pos);
        }
        // 岩浆状态右键钻石 → 放置漂浮钻石，1 秒后雷击掉落召唤器
        if (stack.is(Items.DIAMOND)) {
            return be.placeDiamond(player, hand, level, pos);
        }
        // 空桶 → 取走已有液体（岩浆→岩浆桶，水→水桶）
        if (stack.is(Items.BUCKET)) {
            return be.tryTakeLiquid(player, hand, level, pos);
        }
        // 岩浆桶 → 装岩浆（已有液体则拒绝并提示）
        if (stack.is(Items.LAVA_BUCKET)) {
            return be.tryFill(player, hand, level, pos, CleavingPainBlockEntity.FLUID_LAVA);
        }
        // 水桶 → 装水（已有液体则拒绝并提示）
        if (stack.is(Items.WATER_BUCKET)) {
            return be.tryFill(player, hand, level, pos, CleavingPainBlockEntity.FLUID_WATER);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
