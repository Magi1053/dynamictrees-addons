package com.skcraft.dtsable.compat;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeBody;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.impl.Tooltip;
import snownee.jade.impl.ui.ItemStackElement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * On a managed fallen body, Jade shows the items a manual chop of the looked-at block would pay.
 * Standing trees are left to Dynamic Trees.
 */
@WailaPlugin
public class HarvestHintPlugin implements IWailaPlugin {
    public static final String TAG = "dtsable_harvest";
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("dtsable", "harvest_hint");

    private final Provider provider = new Provider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(provider, BranchBlock.class);
        registration.registerBlockDataProvider(provider, TrunkShellBlock.class);
        registration.registerBlockDataProvider(provider, DynamicLeavesBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(provider, BranchBlock.class);
        registration.registerBlockComponent(provider, TrunkShellBlock.class);
        registration.registerBlockComponent(provider, DynamicLeavesBlock.class);
        registration.addTooltipCollectedCallback(HarvestHintPlugin::logsFirst);
    }

    /**
     * Dynamic Trees adds each drop as its own line, seed first. Collapse those item rows
     * into one line and put logs ahead of seeds, sticks, and other drops.
     */
    private static void logsFirst(IBoxElement box, Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor block)) return;
        BlockState state = block.getBlockState();
        boolean tree = state.getBlock() instanceof BranchBlock
                || state.getBlock() instanceof TrunkShellBlock
                || state.getBlock() instanceof DynamicLeavesBlock
                || block.getServerData().contains(TAG);
        if (!tree || !(box.getTooltip() instanceof Tooltip tooltip)) return;
        List<Integer> itemRows = new ArrayList<>();
        for (int i = 0; i < tooltip.lines.size(); i++) {
            Tooltip.Line line = tooltip.lines.get(i);
            if (!isItemRow(line)) continue;
            sortLogsWithin(line);
            itemRows.add(i);
        }
        if (itemRows.size() < 2) return;
        int insertAt = itemRows.getFirst();
        List<Tooltip.Line> rows = new ArrayList<>();
        for (int index : itemRows) rows.add(tooltip.lines.get(index));
        rows.sort((left, right) -> Boolean.compare(!rowHasLog(left), !rowHasLog(right)));
        List<IElement> combined = new ArrayList<>();
        for (Tooltip.Line row : rows) {
            for (IElement element : row.alignedElements(IElement.Align.LEFT)) {
                if (element instanceof ItemStackElement) combined.add(element);
            }
        }
        for (int i = itemRows.size() - 1; i >= 0; i--) tooltip.lines.remove((int) itemRows.get(i));
        if (!combined.isEmpty()) tooltip.add(insertAt, combined);
    }

    private static boolean isItemRow(Tooltip.Line line) {
        boolean item = false;
        for (IElement element : line.alignedElements(IElement.Align.LEFT)) {
            if (element instanceof ItemStackElement) item = true;
            else if (element.getMessage() != null && !element.getMessage().isBlank()) return false;
        }
        return item;
    }

    private static void sortLogsWithin(Tooltip.Line line) {
        List<IElement> elements = line.alignedElements(IElement.Align.LEFT);
        boolean log = false;
        boolean other = false;
        for (IElement element : elements) {
            if (!(element instanceof ItemStackElement)) continue;
            if (isLog(element)) log = true;
            else other = true;
        }
        if (!log || !other) return;
        elements.sort((left, right) -> Boolean.compare(!isLog(left), !isLog(right)));
        line.markDirty();
    }

    private static boolean rowHasLog(Tooltip.Line line) {
        for (IElement element : line.alignedElements(IElement.Align.LEFT)) {
            if (isLog(element)) return true;
        }
        return false;
    }

    private static boolean isLog(IElement element) {
        return element instanceof ItemStackElement item && item.getItem().is(ItemTags.LOGS);
    }

    private static final class Provider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
        private ResourceKey<Level> cachedDimension;
        private int cachedTool;
        private int cachedStamp;
        private UUID cachedBody;
        private ListTag cachedStacks;

        @Override
        public ResourceLocation getUid() { return UID; }

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!DtSableConfig.isEnabled() || !(accessor.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = accessor.getPosition();
            if (FallenTreeRegistry.find(level, pos).isEmpty()) return;
            tag.put(TAG, stacksFor(level, pos, accessor.getPlayer()).copy());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains(TAG, Tag.TAG_LIST)) return;
            ListTag list = data.getList(TAG, Tag.TAG_COMPOUND);
            if (list.isEmpty()) return;
            IElementHelper helper = IElementHelper.get();
            List<IElement> elements = new ArrayList<>();
            for (Tag entry : list) {
                if (!(entry instanceof CompoundTag stack)) continue;
                ResourceLocation id = ResourceLocation.tryParse(stack.getString("id"));
                if (id == null) continue;
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == null || item == Items.AIR) continue;
                elements.add(helper.item(new ItemStack(item, Math.max(1, stack.getInt("count")))));
            }
            if (!elements.isEmpty()) tooltip.add(elements);
        }

        /**
         * One hint per body segment and budget. The hint is only guaranteed logs, so looking at
         * another block of the same SubLevel shows the same stacks. A split or harvest changes the stamp.
         */
        private synchronized ListTag stacksFor(ServerLevel level, BlockPos pos, Player player) {
            var body = FallenTreeRegistry.find(level, pos).orElse(null);
            if (body == null) return new ListTag();
            ItemStack tool = player == null ? ItemStack.EMPTY : player.getMainHandItem();
            int toolHash = ItemStack.hashItemAndComponents(tool);
            int stamp = bodyStamp(body);
            if (cachedStacks != null && level.dimension().equals(cachedDimension)
                    && toolHash == cachedTool && stamp == cachedStamp && body.id().equals(cachedBody)) {
                return cachedStacks;
            }
            cachedDimension = level.dimension();
            cachedTool = toolHash;
            cachedStamp = stamp;
            cachedBody = body.id();
            cachedStacks = encode(TreeHarvest.previewDrops(level, pos, player));
            return cachedStacks;
        }

        private static int bodyStamp(FallenTreeBody body) {
            int stamp = body.wood().size() * 31 + body.leaves().size();
            for (int volume : body.unpaidVolume().values()) stamp = stamp * 31 + volume;
            return stamp;
        }

        private static ListTag encode(List<ItemStack> drops) {
            ListTag list = new ListTag();
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                CompoundTag entry = new CompoundTag();
                entry.putString("id", BuiltInRegistries.ITEM.getKey(drop.getItem()).toString());
                entry.putInt("count", drop.getCount());
                list.add(entry);
            }
            return list;
        }
    }
}
