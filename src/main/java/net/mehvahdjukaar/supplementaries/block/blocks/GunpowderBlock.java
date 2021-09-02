package net.mehvahdjukaar.supplementaries.block.blocks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.mehvahdjukaar.supplementaries.block.BlockProperties;
import net.mehvahdjukaar.supplementaries.block.util.ILightable;
import net.mehvahdjukaar.supplementaries.compat.CompatHandler;
import net.mehvahdjukaar.supplementaries.compat.decorativeblocks.DecoBlocksCompatRegistry;
import net.mehvahdjukaar.supplementaries.setup.ModRegistry;
import net.mehvahdjukaar.supplementaries.world.explosion.GunpowderExplosion;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.RedstoneSide;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.*;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Random;

/**
 * The main gunpowder block. Very similar to the {@link net.minecraft.block.RedstoneWireBlock}
 * block.
 *
 * @author Tmtravlr (Rebeca Rey), updated by MehVahdJukaar
 * @Date December 2015, 2021
 */
public class GunpowderBlock extends LightUpBlock {


    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
    public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
    public static final IntegerProperty BURNING = BlockProperties.BURNING;


    public static final Map<Direction, EnumProperty<RedstoneSide>> PROPERTY_BY_DIRECTION = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST));
    private static final VoxelShape SHAPE_DOT = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 1.0D, 13.0D);
    private static final Map<Direction, VoxelShape> SHAPES_FLOOR = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, Block.box(3.0D, 0.0D, 0.0D, 13.0D, 1.0D, 13.0D), Direction.SOUTH, Block.box(3.0D, 0.0D, 3.0D, 13.0D, 1.0D, 16.0D), Direction.EAST, Block.box(3.0D, 0.0D, 3.0D, 16.0D, 1.0D, 13.0D), Direction.WEST, Block.box(0.0D, 0.0D, 3.0D, 13.0D, 1.0D, 13.0D)));
    private static final Map<Direction, VoxelShape> SHAPES_UP = Maps.newEnumMap(ImmutableMap.of(Direction.NORTH, VoxelShapes.or(SHAPES_FLOOR.get(Direction.NORTH), Block.box(3.0D, 0.0D, 0.0D, 13.0D, 16.0D, 1.0D)), Direction.SOUTH, VoxelShapes.or(SHAPES_FLOOR.get(Direction.SOUTH), Block.box(3.0D, 0.0D, 15.0D, 13.0D, 16.0D, 16.0D)), Direction.EAST, VoxelShapes.or(SHAPES_FLOOR.get(Direction.EAST), Block.box(15.0D, 0.0D, 3.0D, 16.0D, 16.0D, 13.0D)), Direction.WEST, VoxelShapes.or(SHAPES_FLOOR.get(Direction.WEST), Block.box(0.0D, 0.0D, 3.0D, 1.0D, 16.0D, 13.0D))));

    private final Map<BlockState, VoxelShape> SHAPES_CACHE = Maps.newHashMap();
    private final BlockState crossState;

    public static final int DELAY = 2;

    public static final int SPREAD_AGE = 2;

    public GunpowderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, RedstoneSide.NONE)
                .setValue(EAST, RedstoneSide.NONE).setValue(SOUTH, RedstoneSide.NONE)
                .setValue(WEST, RedstoneSide.NONE).setValue(BURNING, 0));
        this.crossState = this.defaultBlockState().setValue(NORTH, RedstoneSide.SIDE)
                .setValue(EAST, RedstoneSide.SIDE).setValue(SOUTH, RedstoneSide.SIDE)
                .setValue(WEST, RedstoneSide.SIDE).setValue(BURNING, 0);

        for (BlockState blockstate : this.getStateDefinition().getPossibleStates()) {
            if (blockstate.getValue(BURNING) == 0) {
                this.SHAPES_CACHE.put(blockstate, this.calculateVoxelShape(blockstate));
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, BURNING);
    }

    private VoxelShape calculateVoxelShape(BlockState state) {
        VoxelShape voxelshape = SHAPE_DOT;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            RedstoneSide redstoneside = state.getValue(PROPERTY_BY_DIRECTION.get(direction));
            if (redstoneside == RedstoneSide.SIDE) {
                voxelshape = VoxelShapes.or(voxelshape, SHAPES_FLOOR.get(direction));
            } else if (redstoneside == RedstoneSide.UP) {
                voxelshape = VoxelShapes.or(voxelshape, SHAPES_UP.get(direction));
            }
        }
        return voxelshape;
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return this.SHAPES_CACHE.get(state.setValue(BURNING, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getConnectionState(context.getLevel(), this.crossState, context.getClickedPos());
    }

    @Deprecated
    public boolean canBeReplaced(BlockState p_225541_1_, Fluid p_225541_2_) {
        return this.material.isReplaceable() || !this.material.isSolid();
    }

    //-----connection logic------

    private BlockState getConnectionState(IBlockReader world, BlockState state, BlockPos pos) {
        boolean flag = isDot(state);
        state = this.getMissingConnections(world, this.defaultBlockState().setValue(BURNING, state.getValue(BURNING)), pos);
        if (!flag || !isDot(state)) {
            boolean flag1 = state.getValue(NORTH).isConnected();
            boolean flag2 = state.getValue(SOUTH).isConnected();
            boolean flag3 = state.getValue(EAST).isConnected();
            boolean flag4 = state.getValue(WEST).isConnected();
            boolean flag5 = !flag1 && !flag2;
            boolean flag6 = !flag3 && !flag4;
            if (!flag4 && flag5) {
                state = state.setValue(WEST, RedstoneSide.SIDE);
            }

            if (!flag3 && flag5) {
                state = state.setValue(EAST, RedstoneSide.SIDE);
            }

            if (!flag1 && flag6) {
                state = state.setValue(NORTH, RedstoneSide.SIDE);
            }

            if (!flag2 && flag6) {
                state = state.setValue(SOUTH, RedstoneSide.SIDE);
            }
        }
        return state;
    }

    private BlockState getMissingConnections(IBlockReader world, BlockState state, BlockPos pos) {
        boolean flag = !world.getBlockState(pos.above()).isRedstoneConductor(world, pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected()) {
                RedstoneSide redstoneside = this.getConnectingSide(world, pos, direction, flag);
                state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), redstoneside);
            }
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState otherState, IWorld world, BlockPos pos, BlockPos otherPos) {
        //should be server only
        BlockState newState;
        if (direction == Direction.DOWN) {
            newState = this.canSurvive(state, world, pos) ? state : Blocks.AIR.defaultBlockState();
        } else if (direction == Direction.UP) {
            newState = this.getConnectionState(world, state, pos);
        } else {
            RedstoneSide redstoneside = this.getConnectingSide(world, pos, direction);
            newState = redstoneside.isConnected() == state.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected() && !isCross(state) ?
                    state.setValue(PROPERTY_BY_DIRECTION.get(direction), redstoneside) :
                    this.getConnectionState(world, this.crossState.setValue(BURNING, state.getValue(BURNING)).setValue(PROPERTY_BY_DIRECTION.get(direction), redstoneside), pos);
        }
        return newState;
    }

    private static boolean isCross(BlockState state) {
        return state.getValue(NORTH).isConnected() && state.getValue(SOUTH).isConnected() && state.getValue(EAST).isConnected() && state.getValue(WEST).isConnected();
    }

    private static boolean isDot(BlockState state) {
        return !state.getValue(NORTH).isConnected() && !state.getValue(SOUTH).isConnected() && !state.getValue(EAST).isConnected() && !state.getValue(WEST).isConnected();
    }

    //used to connect diagonally
    @Override
    public void updateIndirectNeighbourShapes(BlockState state, IWorld world, BlockPos pos, int var1, int var2) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            RedstoneSide redstoneside = state.getValue(PROPERTY_BY_DIRECTION.get(direction));
            if (redstoneside != RedstoneSide.NONE && !world.getBlockState(mutable.setWithOffset(pos, direction)).is(this)) {
                mutable.move(Direction.DOWN);
                BlockState blockstate = world.getBlockState(mutable);
                if (!blockstate.is(Blocks.OBSERVER)) {
                    BlockPos blockpos = mutable.relative(direction.getOpposite());
                    BlockState blockstate1 = blockstate.updateShape(direction.getOpposite(), world.getBlockState(blockpos), world, mutable, blockpos);
                    updateOrDestroy(blockstate, blockstate1, world, mutable, var1, var2);
                }

                mutable.setWithOffset(pos, direction).move(Direction.UP);
                BlockState blockstate3 = world.getBlockState(mutable);
                if (!blockstate3.is(Blocks.OBSERVER)) {
                    BlockPos blockpos1 = mutable.relative(direction.getOpposite());
                    BlockState blockstate2 = blockstate3.updateShape(direction.getOpposite(), world.getBlockState(blockpos1), world, mutable, blockpos1);
                    updateOrDestroy(blockstate3, blockstate2, world, mutable, var1, var2);
                }
            }
        }

    }

    //gets connection to blocks diagonally above
    private RedstoneSide getConnectingSide(IBlockReader world, BlockPos pos, Direction dir) {
        return this.getConnectingSide(world, pos, dir, !world.getBlockState(pos.above()).isRedstoneConductor(world, pos));
    }

    private RedstoneSide getConnectingSide(IBlockReader world, BlockPos pos, Direction dir, boolean canClimbUp) {
        BlockPos blockpos = pos.relative(dir);
        BlockState blockstate = world.getBlockState(blockpos);
        if (canClimbUp) {
            boolean flag = this.canSurviveOn(world, blockpos, blockstate);
            if (flag && canConnectTo(world.getBlockState(blockpos.above()), world, blockpos.above(), null)) {
                if (blockstate.isFaceSturdy(world, blockpos, dir.getOpposite())) {
                    return RedstoneSide.UP;
                }
                return RedstoneSide.SIDE;
            }
        }
        return !canConnectTo(blockstate, world, blockpos, dir) && (blockstate.isRedstoneConductor(world, blockpos) || !canConnectTo(world.getBlockState(blockpos.below()), world, blockpos.below(), null)) ? RedstoneSide.NONE : RedstoneSide.SIDE;
    }

    @Override
    public boolean canSurvive(BlockState state, IWorldReader world, BlockPos pos) {
        BlockPos blockpos = pos.below();
        BlockState blockstate = world.getBlockState(blockpos);
        return this.canSurviveOn(world, blockpos, blockstate);
    }

    private boolean canSurviveOn(IBlockReader world, BlockPos pos, BlockState state) {
        return state.isFaceSturdy(world, pos, Direction.UP) || state.is(Blocks.HOPPER);
    }

    //TODO: add more cases
    protected boolean canConnectTo(BlockState state, IBlockReader world, BlockPos pos, @Nullable Direction dir) {
        Block b = state.getBlock();
        return b instanceof ILightable || b instanceof TNTBlock || b instanceof CampfireBlock ||
                (CompatHandler.deco_blocks && DecoBlocksCompatRegistry.isBrazier(b));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST)).setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH)).setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH));
            default:
                return state;
        }
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK:
                return state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default:
                return super.mirror(state, mirror);
        }
    }


    //-----redstone------

    @Override
    public void onPlace(BlockState state, World world, BlockPos pos, BlockState oldState, boolean moving) {
        if (!oldState.is(state.getBlock()) && !world.isClientSide) {
            //doesn't ignite immediately
            world.getBlockTicks().scheduleTick(pos, this, DELAY);

            for (Direction direction : Direction.Plane.VERTICAL) {
                world.updateNeighborsAt(pos.relative(direction), this);
            }

            this.updateNeighborsOfNeighboringWires(world, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            super.onRemove(state, world, pos, newState, isMoving);
            if (!world.isClientSide) {
                for (Direction direction : Direction.values()) {
                    world.updateNeighborsAt(pos.relative(direction), this);
                }
                this.updateNeighborsOfNeighboringWires(world, pos);
            }
        }
    }

    /**
     * Lets the block know when one of its neighbor changes. Doesn't know which
     * neighbor changed (coordinates passed are their own) Args: x, y, z,
     * neighbor Block
     */
    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moving) {
        super.neighborChanged(state, world, pos, neighborBlock, neighborPos, moving);
        if (!world.isClientSide) {
            world.getBlockTicks().scheduleTick(pos, this, DELAY);
        }
    }

    private void updateNeighborsOfNeighboringWires(World world, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            this.checkCornerChangeAt(world, pos.relative(direction));
        }

        for (Direction direction1 : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.relative(direction1);
            if (world.getBlockState(blockpos).isRedstoneConductor(world, blockpos)) {
                this.checkCornerChangeAt(world, blockpos.above());
            } else {
                this.checkCornerChangeAt(world, blockpos.below());
            }
        }
    }

    private void checkCornerChangeAt(World world, BlockPos pos) {
        if (world.getBlockState(pos).is(this)) {
            world.updateNeighborsAt(pos, this);

            for (Direction direction : Direction.values()) {
                world.updateNeighborsAt(pos.relative(direction), this);
            }
        }
    }

    @Override
    public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        ActionResultType lightUp = super.use(state, world, pos, player, hand, hit);
        if (lightUp.consumesAction()) return lightUp;
        if (player.abilities.mayBuild) {
            if (isCross(state) || isDot(state)) {
                BlockState blockstate = isCross(state) ? this.defaultBlockState() : this.crossState;
                blockstate = blockstate.setValue(BURNING, state.getValue(BURNING));
                blockstate = this.getConnectionState(world, blockstate, pos);
                if (blockstate != state) {
                    world.setBlock(pos, blockstate, 3);
                    this.updatesOnShapeChange(world, pos, state, blockstate);
                    return ActionResultType.SUCCESS;
                }
            }
        }
        return ActionResultType.PASS;
    }

    private void updatesOnShapeChange(World world, BlockPos pos, BlockState state, BlockState newState) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.relative(direction);
            if (state.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected() != newState.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected() && world.getBlockState(blockpos).isRedstoneConductor(world, blockpos)) {
                world.updateNeighborsAtExceptFromFacing(blockpos, newState.getBlock(), direction.getOpposite());
            }
        }

    }


    //-----explosion-stuff------

    @Override
    public void tick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int burning = state.getValue(BURNING);

        if (!world.isClientSide) {

            if (burning == 8) {
                world.removeBlock(pos, false);
                explode(world, pos);
            } else if (burning > 0) {
                if (burning >= SPREAD_AGE) {
                    this.lightUpNeighbouringWires(pos, state, world);
                }
                world.setBlockAndUpdate(pos, state.setValue(BURNING, burning + 1));
                world.getBlockTicks().scheduleTick(pos, this, DELAY);
            }
            //not burning. check if it should
            else {
                for (Direction dir : Direction.values()) {
                    BlockPos p = pos.relative(dir);
                    if (this.isFireSource(world, p)) {
                        this.lightUp(state, pos, world, FireSound.FLAMING_ARROW);
                        world.getBlockTicks().scheduleTick(pos, this, DELAY);
                        break;
                    }
                }
            }
        }
    }


    public void explode(World world, BlockPos pos) {
        GunpowderExplosion explosion = new GunpowderExplosion(world, null, pos.getX(), pos.getY(), pos.getZ(), 0.5f);
        if (net.minecraftforge.event.ForgeEventFactory.onExplosionStart(world, explosion)) return;
        explosion.explode();
        explosion.finalizeExplosion(false);
    }

    @Override
    public boolean lightUp(BlockState state, BlockPos pos, IWorld world, FireSound sound) {
        boolean ret = super.lightUp(state, pos, world, sound);
        if (ret) {
            //spawn particles when first lit
            if (!world.isClientSide()) {
                ((World) world).blockEvent(pos, this, 0, 0);
            }
            world.getBlockTicks().scheduleTick(pos, this, DELAY);
        }
        return ret;
    }

    //for gunpowder -> gunpowder
    private void lightUpByWire(BlockState state, BlockPos pos, IWorld world) {
        if (!isLit(state)) {
            //spawn particles when first lit
            if (!world.isClientSide()) {
                ((World) world).blockEvent(pos, this, 0, 0);
            }
            world.setBlock(pos, toggleLitState(state, true), 11);
            world.playSound(null, pos, ModRegistry.GUNPOWDER_IGNITE.get(), SoundCategory.BLOCKS, 2.0f,
                    1.9f + world.getRandom().nextFloat() * 0.1f);
        }
    }

    protected void lightUpNeighbouringWires(BlockPos pos, BlockState state, World world) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            RedstoneSide side = state.getValue(PROPERTY_BY_DIRECTION.get(dir));
            BlockState neighbourState;
            BlockPos p;
            if (side == RedstoneSide.UP) {
                p = pos.relative(dir).above();
                neighbourState = world.getBlockState(p);

            } else if (side == RedstoneSide.SIDE) {
                p = pos.relative(dir);
                neighbourState = world.getBlockState(p);
                if (!neighbourState.is(this) && !neighbourState.isRedstoneConductor(world, pos)) {
                    p = p.below();
                    neighbourState = world.getBlockState(p);
                }
            } else continue;
            if (neighbourState.is(this)) {
                world.getBlockTicks().scheduleTick(p, this, Math.max(DELAY - 1, 1));
                this.lightUpByWire(neighbourState, p, world);
            }
        }
    }


    //TODO: drop nothing if burning
    private boolean isFireSource(IWorld world, BlockPos pos) {
        //wires handled separately
        BlockState state = world.getBlockState(pos);
        Block b = state.getBlock();
        //TODO: add tag
        if (b instanceof FireBlock || b instanceof MagmaBlock || b instanceof TorchBlock ||
                b == ModRegistry.BLAZE_ROD_BLOCK.get() || b == ModRegistry.MAGMA_CREAM_BLOCK.get())
            return true;
        if (b instanceof CampfireBlock || (CompatHandler.deco_blocks && DecoBlocksCompatRegistry.isBrazier(b))) {
            return state.getValue(CampfireBlock.LIT);
        }
        return world.getFluidState(pos).getType() == Fluids.LAVA;
    }

    /**
     * Called upon the block being destroyed by an explosion
     */
    @Override
    public void onBlockExploded(BlockState state, World world, BlockPos pos, Explosion explosion) {
        if (!world.isClientSide) {
            this.lightUp(state, pos, world, FireSound.FLAMING_ARROW);
        }
    }

    //----- light up block ------


    @Override
    public int getFireSpreadSpeed(BlockState state, IBlockReader world, BlockPos pos, Direction face) {
        return 60;
    }

    @Override
    public int getFlammability(BlockState state, IBlockReader world, BlockPos pos, Direction face) {
        return 300;
    }

    @Override
    public void catchFire(BlockState state, World world, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {

    }

    @Override
    public boolean isLit(BlockState state) {
        return state.getValue(BURNING) != 0;
    }

    @Override
    public BlockState toggleLitState(BlockState state, boolean lit) {
        return state.setValue(BURNING, lit ? 1 : 0);
    }

    /**
     * Gets an item for the block being called on. Args: world, x, y, z
     */
    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult target, IBlockReader world, BlockPos pos, PlayerEntity player) {
        return new ItemStack(Items.GUNPOWDER);
    }


    //client

    //called when first lit
    @Override
    public boolean triggerEvent(BlockState state, World world, BlockPos pos, int eventID, int eventParam) {
        if (eventID == 0) {
            this.animateTick(state.setValue(BURNING, 1), world, pos, world.random);
            return true;
        }
        return super.triggerEvent(state, world, pos, eventID, eventParam);
    }

    /**
     * A randomly called display update to be able to add particles or other
     * items for display
     */
    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random random) {
        int i = state.getValue(BURNING);
        if (i != 0) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                RedstoneSide redstoneside = state.getValue(PROPERTY_BY_DIRECTION.get(direction));
                switch (redstoneside) {
                    case UP:
                        this.spawnParticlesAlongLine(world, random, pos, i, direction, Direction.UP, -0.5F, 0.5F);
                    case SIDE:
                        this.spawnParticlesAlongLine(world, random, pos, i, Direction.DOWN, direction, 0.0F, 0.5F);
                        break;
                    case NONE:
                    default:
                        this.spawnParticlesAlongLine(world, random, pos, i, Direction.DOWN, direction, 0.0F, 0.3F);
                }
            }
        }
    }

    private void spawnParticlesAlongLine(World world, Random rand, BlockPos pos, int burning, Direction dir1, Direction dir2, float from, float to) {
        float f = to - from;
        float in = (7.5f - (burning - 1)) / 7.5f;
        if ((rand.nextFloat() < 1 * f * in)) {
            float f2 = from + f * rand.nextFloat();
            double x = pos.getX() + 0.5D + (double) (0.4375F * (float) dir1.getStepX()) + (double) (f2 * (float) dir2.getStepX());
            double y = pos.getY() + 0.5D + (double) (0.4375F * (float) dir1.getStepY()) + (double) (f2 * (float) dir2.getStepY());
            double z = pos.getZ() + 0.5D + (double) (0.4375F * (float) dir1.getStepZ()) + (double) (f2 * (float) dir2.getStepZ());

            float velY = (burning / 15.0F) * 0.03F;
            float velX = rand.nextFloat() * 0.02f - 0.01f;
            float velZ = rand.nextFloat() * 0.02f - 0.01f;

            world.addParticle(ParticleTypes.FLAME, x, y, z, velX, velY, velZ);
            world.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, velX, velY, velZ);
        }
    }


}