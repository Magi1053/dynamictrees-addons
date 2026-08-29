package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BasicBranchBlock;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.physics.FallenTreeCollision;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.WoodThickness;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BasicBranchBlock.class)
public abstract class FallenBranchMixin extends BranchBlock implements BlockWithSubLevelCollisionCallback {
    protected FallenBranchMixin(ResourceLocation name, BlockBehaviour.Properties properties) { super(name, properties); }

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() { return FallenTreeCollision.INSTANCE; }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos) {
        if (!DtSableConfig.isEnabled() || !(getter instanceof Level level)
                || !FallenTreeRegistry.hasFallenMiningContext(level, pos)) return super.getDestroyProgress(state, player, getter, pos);
        BlockState primitive = DtIntegration.primitiveLog(this).defaultBlockState();
        float scale = Math.max(1.0f, WoodThickness.radius(state) / 8.0f);
        float progress = primitive.getDestroyProgress(player, getter, pos)
                * DtSableConfig.fallenWoodMiningSpeed() / scale;
        return progress > 0.0f ? progress : super.getDestroyProgress(state, player, getter, pos);
    }
}
