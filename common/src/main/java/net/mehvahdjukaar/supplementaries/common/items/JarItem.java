package net.mehvahdjukaar.supplementaries.common.items;


import net.mehvahdjukaar.moonlight.fluids.SoftFluid;
import net.mehvahdjukaar.moonlight.fluids.SoftFluidRegistry;
import net.mehvahdjukaar.moonlight.fluids.SoftFluidTank;
import net.mehvahdjukaar.moonlight.util.PotionNBTHelper;
import net.mehvahdjukaar.supplementaries.client.renderers.items.JarItemRenderer;
import net.mehvahdjukaar.supplementaries.common.block.tiles.JarBlockTile;
import net.mehvahdjukaar.supplementaries.common.capabilities.mobholder.CapturedMobsHelper;
import net.mehvahdjukaar.supplementaries.common.items.tabs.JarTab;
import net.mehvahdjukaar.supplementaries.configs.ClientConfigs;
import net.mehvahdjukaar.supplementaries.configs.RegistryConfigs;
import net.mehvahdjukaar.supplementaries.configs.ServerConfigs;
import net.mehvahdjukaar.supplementaries.integration.CompatHandler;
import net.mehvahdjukaar.supplementaries.integration.botania.BotaniaCompatRegistry;
import net.mehvahdjukaar.supplementaries.setup.ClientRegistry;
import net.mehvahdjukaar.supplementaries.setup.ModRegistry;
import net.mehvahdjukaar.supplementaries.setup.ModSoftFluids;
import net.mehvahdjukaar.supplementaries.setup.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.IItemRenderProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class JarItem extends AbstractMobContainerItem {

    public JarItem(Block blockIn, Properties properties) {
        super(blockIn, properties, 0.625f, 0.875f, true);
    }

    @Override
    public boolean canItemCatch(Entity e) {
        EntityType<?> type = e.getType();
        if (e instanceof Monster) return false;
        if (ServerConfigs.cached.JAR_AUTO_DETECT && this.canFitEntity(e)) return true;
        return type.is(ModTags.JAR_CATCHABLE) || this.isBoat(e) || CapturedMobsHelper.is2DFish(type);
    }

    @Override
    public void playReleaseSound(Level world, Vec3 v) {
        world.playSound(null, v.x(), v.y(), v.z(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1, 0.05f);
    }

    @Override
    public void playCatchSound(Player player) {
        player.level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.BLOCKS, 1, 1);
    }

    @Override
    public ItemStack saveEntityInItem(Entity entity, ItemStack currentStack, ItemStack bucket) {
        if (this.isBoat(entity)) {
            return new ItemStack(ModRegistry.JAR_BOAT_ITEM.get());
        } else {
            return super.saveEntityInItem(entity, currentStack, bucket);
        }
    }

    public boolean isBoat(Entity e) {
        return e instanceof Boat;
    }

    @Override
    public InteractionResult doInteract(ItemStack stack, Player player, Entity entity, InteractionHand hand) {
        //capture mob
        if (!ServerConfigs.cached.JAR_CAPTURE) return InteractionResult.PASS;
        return super.doInteract(stack, player, entity, hand);
    }

    @Override
    public boolean blocksPlacement() {
        return ServerConfigs.cached.JAR_CAPTURE;
    }

    @Override
    public void addPlacementTooltip(List<Component> tooltip) {
        if (ServerConfigs.cached.JAR_CAPTURE)
            super.addPlacementTooltip(tooltip);
    }

    //full jar stuff
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
        super.appendHoverText(stack, worldIn, tooltip, flagIn);
        CompoundTag compoundTag = stack.getTagElement("BlockEntityTag");
        if (compoundTag == null) {
            if (!ClientConfigs.cached.TOOLTIP_HINTS || !flagIn.isAdvanced()) return;
            tooltip.add(Component.translatable("message.supplementaries.jar").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        } else {
            if (compoundTag.contains("LootTable", 8)) {
                tooltip.add(Component.literal("???????").withStyle(ChatFormatting.GRAY));
            }

            if (compoundTag.contains("FluidHolder")) {
                CompoundTag com = compoundTag.getCompound("FluidHolder");
                SoftFluid s = SoftFluidRegistry.get(com.getString("Fluid"));
                int count = com.getInt("Count");
                if (!s.isEmpty() && count > 0) {

                    CompoundTag nbt = null;
                    String add = "";
                    if (com.contains("NBT")) {
                        nbt = com.getCompound("NBT");
                        if (nbt.contains("Bottle")) {
                            String bottle = nbt.getString("Bottle").toLowerCase(Locale.ROOT);
                            if (!bottle.equals("regular")) add = "_" + bottle;
                        }
                    }

                    tooltip.add(Component.translatable("message.supplementaries.fluid_tooltip",
                            Component.translatable(s.getTranslationKey() + add), count).withStyle(ChatFormatting.GRAY));
                    if (nbt != null) {
                        PotionNBTHelper.addPotionTooltip(nbt, tooltip, 1);
                        return;
                    }
                }
            }

            if (compoundTag.contains("Items", 9)) {
                NonNullList<ItemStack> nonnulllist = NonNullList.withSize(27, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(compoundTag, nonnulllist);
                int i = 0;
                int j = 0;

                for (ItemStack itemstack : nonnulllist) {
                    if (!itemstack.isEmpty()) {
                        ++j;
                        if (i <= 4) {
                            ++i;
                            MutableComponent iformattabletextcomponent = itemstack.getHoverName().copy();

                            String s = iformattabletextcomponent.getString();
                            s = s.replace(" Bucket", "");
                            s = s.replace(" Bottle", "");
                            s = s.replace("Bucket of ", "");
                            MutableComponent str = Component.literal(s);

                            str.append(" x").append(String.valueOf(itemstack.getCount()));
                            tooltip.add(str.withStyle(ChatFormatting.GRAY));
                        }
                    }
                }
                if (j - i > 0) {
                    tooltip.add((Component.translatable("container.shulkerBox.more", j - i)).withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    @Override
    public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items) {
        if (RegistryConfigs.Reg.JAR_TAB.get()) {
            if (group == ModRegistry.JAR_TAB) {
                JarTab.populateTab(items);
            }
        } else super.fillItemCategory(group, items);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null) {
            if (tag.contains("FluidHolder")) {
                CompoundTag com = tag.getCompound("FluidHolder");
                SoftFluid s = SoftFluidRegistry.get(com.getString("Fluid"));
                if (s == ModSoftFluids.DIRT.get()) return Rarity.RARE;
            }
        }
        return super.getRarity(stack);
    }

    //nonsense jar drinking here

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity entity) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && entity instanceof Player) {
            JarBlockTile temp = new JarBlockTile(entity.getOnPos(), ModRegistry.JAR.get().defaultBlockState());
            temp.load(tag);
            SoftFluidTank fh = temp.getSoftFluidTank();
            if (fh.containsFood()) {
                if (fh.tryDrinkUpFluid((Player) entity, world)) {
                    CompoundTag newTag = new CompoundTag();
                    temp.saveAdditional(newTag);
                    stack.addTagElement("BlockEntityTag", newTag);
                    return stack;
                }
            }
        }
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player playerEntity, InteractionHand hand) {
        if (this.getUseDuration(playerEntity.getItemInHand(hand)) != 0) {
            return ItemUtils.startUsingInstantly(world, playerEntity, hand);
        }
        return super.use(world, playerEntity, hand);
    }

    private JarBlockTile DUMMY_TILE = null;

    @Override
    public int getUseDuration(ItemStack stack) {
        if (ServerConfigs.cached.JAR_ITEM_DRINK) {
            CompoundTag tag = stack.getTagElement("BlockEntityTag");
            if (tag != null) {
                if (DUMMY_TILE == null)
                    DUMMY_TILE = new JarBlockTile(BlockPos.ZERO, ModRegistry.JAR.get().defaultBlockState());
                DUMMY_TILE.load(tag);
                SoftFluidTank fh = DUMMY_TILE.getSoftFluidTank();
                var provider = fh.getFluid().getFoodProvider();
                Item food = provider.getFood();
                return food.getUseDuration(food.getDefaultInstance()) / provider.getDivider();
            }
        }
        return 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        if (ServerConfigs.cached.JAR_ITEM_DRINK) {
            return UseAnim.DRINK;
        }
        return UseAnim.NONE;
    }

    @Override
    public void initializeClient(Consumer<IItemRenderProperties> consumer) {
        ClientRegistry.registerISTER(consumer, JarItemRenderer::new);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (CompatHandler.botania && this == ModRegistry.JAR_ITEM.get()) {
            InteractionResult r = BotaniaCompatRegistry.tryCaptureTater(this, context);
            if (r.consumesAction()) return r;
        }
        return super.useOn(context);
    }


}