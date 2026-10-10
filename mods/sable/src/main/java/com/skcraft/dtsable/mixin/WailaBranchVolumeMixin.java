package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.compat.waila.WailaBranchHandler;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.compat.HarvestHintPlugin;
import com.skcraft.dtsable.compat.StandingTreeVolume;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.api.Accessor;
import snownee.jade.impl.ObjectDataCenter;

/**
 * Dynamic Trees draws the species seed and the stick remainder on its branch tooltip.
 * Those stay, as a single icon: the real number is rolled when the block breaks.
 * Log stacks keep their counts.
 */
@Mixin(value = WailaBranchHandler.class, remap = false)
public abstract class WailaBranchVolumeMixin {
    @Redirect(method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/BlockAccessor;Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At(value = "INVOKE", target = "Lcom/dtteam/dynamictrees/tree/species/Species;getSeedStack(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dtsable$seedWithoutCount(Species species, int count) {
        ItemStack seed = species.getSeedStack(1);
        if (!seed.isEmpty()) seed.setCount(1);
        return seed;
    }

    @Redirect(method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/BlockAccessor;Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At(value = "INVOKE", target = "Lcom/dtteam/dynamictrees/tree/family/Family;getStick(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dtsable$stickWithoutCount(Family family, int count) {
        ItemStack stick = family.getStick(1);
        if (!stick.isEmpty()) stick.setCount(1);
        return stick;
    }

    @Inject(method = "getTreeVolume", at = @At("HEAD"), cancellable = true)
    private void dtsable$emptyVolumeWhenPreviewed(Level level, BlockPos pos, Species species,
                                                   CallbackInfoReturnable<NetVolumeNode.Volume> cir) {
        Accessor<?> accessor = ObjectDataCenter.get();
        if (accessor != null && accessor.getServerData().contains(HarvestHintPlugin.TAG, Tag.TAG_LIST)) {
            cir.setReturnValue(new NetVolumeNode.Volume());
            return;
        }
        NetVolumeNode.Volume standing = StandingTreeVolume.cascade(level, pos, species);
        if (standing != null) cir.setReturnValue(withStickHint(standing));
    }

    /**
     * Dynamic Trees draws a stick only when this slice's leftover volume rounds up to one.
     * A trunk can be exact logs at the base and a leftover a block or two higher. Sticks are a
     * per-branch chance, so any wood with volume keeps the icon.
     */
    private static NetVolumeNode.Volume withStickHint(NetVolumeNode.Volume source) {
        int rate;
        try {
            rate = DtSableConfig.fallenSticksPerLog();
        } catch (IllegalStateException | NullPointerException notLoaded) {
            rate = 4;
        }
        if (rate <= 0) return source;
        int[] raw = source.getRawVolumesArray();
        int perLog = NetVolumeNode.Volume.VOXELSPERLOG;
        boolean any = false;
        boolean already = false;
        for (int volume : raw) {
            if (volume <= 0) continue;
            any = true;
            if ((volume % perLog) * 8 / perLog > 0) already = true;
        }
        if (!any || already) return source;
        NetVolumeNode.Volume nudged = new NetVolumeNode.Volume();
        boolean added = false;
        for (int index = 0; index < raw.length; index++) {
            int volume = raw[index];
            if (volume > 0 && !added) {
                volume += perLog / 8;
                added = true;
            }
            if (volume > 0) nudged.addVolume(volume, index);
        }
        return nudged;
    }
}
