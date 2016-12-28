package hellfirepvp.astralsorcery.common.tile;

import hellfirepvp.astralsorcery.common.tile.base.TileEntityTick;
import hellfirepvp.astralsorcery.common.util.MiscUtils;
import hellfirepvp.astralsorcery.common.util.nbt.NBTUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: TileFakeTree
 * Created by HellFirePvP
 * Date: 11.11.2016 / 20:34
 */
public class TileFakeTree extends TileEntityTick {

    //public static final IMajorConstellation SUSTAIN_CONSTELLATION = Constellations.fertilitas;

    private BlockPos reference;
    private IBlockState fakedState;
    private SustainFunction sFunc;

    @Override
    public void update() {
        super.update();

        if(world.isRemote) {

        } else {
            if(ticksExisted > 5 && ticksExisted % 4 == 0) {
                if(reference == null) {
                    cleanUp();
                    return;
                }
                if(fakedState == null || fakedState.getBlock().equals(Blocks.AIR)) {
                    cleanUp();
                    return;
                }
                if(MiscUtils.isChunkLoaded(world, new ChunkPos(reference))) {
                    if(!sFunc.canSustainFakeTile(world, pos)) {
                        cleanUp();
                    }
                    cleanUp();
                }
            }
        }
    }

    private void cleanUp() {
        if(fakedState != null) {
            world.setBlockState(getPos(), fakedState);
        } else {
            world.setBlockToAir(getPos());
        }
    }

    @Override
    protected void onFirstTick() {}

    public void setupTile(BlockPos reference, IBlockState fakedState, SustainFunction func) {
        this.reference = reference;
        this.fakedState = fakedState;
        this.sFunc = func;
        markForUpdate();
    }

    public IBlockState getFakedState() {
        return fakedState;
    }

    public BlockPos getReference() {
        return reference;
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);

        reference = NBTUtils.readBlockPosFromNBT(compound);

        if(compound.hasKey("Block") && compound.hasKey("Data")) {
            int data = compound.getInteger("Data");
            Block b = Block.getBlockFromName(compound.getString("Block"));
            if(b != null) {
                fakedState = b.getStateFromMeta(data);
            }
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);

        if(reference != null) {
            NBTUtils.writeBlockPosToNBT(reference, compound);
        }
        if(fakedState != null) {
            compound.setString("Block", Block.REGISTRY.getNameForObject(fakedState.getBlock()).toString());
            compound.setInteger("Data", fakedState.getBlock().getMetaFromState(fakedState));
        }
    }

    public static interface SustainFunction {

        public boolean canSustainFakeTile(World world, BlockPos pos);

    }

}
