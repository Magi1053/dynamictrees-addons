package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.dt.FellContext;
import com.skcraft.dtsable.tree.TreeAssembler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = FallingTreeEntity.class, remap = false)
public abstract class FallingTreeDropMixin {
    @Inject(method = "dropTree", at = @At("HEAD"), cancellable = true)
    private static void dtsable$convert(Level level, BranchDestructionData data, List<ItemStack> drops,
                                        FallingTreeEntity.DestroyType type,
                                        CallbackInfoReturnable<FallingTreeEntity> cir) {
        if (!(level instanceof ServerLevel server) || level.isClientSide || !DtSableConfig.isEnabled()
                || DtIntegration.isMutatingBody() || !DtIntegration.supported(type)) return;
        if (TreeAssembler.tryAssemble(server, FellContext.player(), data, drops) != null) {
            FellContext.markConverted();
            cir.setReturnValue(null);
        }
    }
}
