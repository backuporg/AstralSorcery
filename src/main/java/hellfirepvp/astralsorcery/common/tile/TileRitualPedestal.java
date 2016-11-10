package hellfirepvp.astralsorcery.common.tile;

import hellfirepvp.astralsorcery.AstralSorcery;
import hellfirepvp.astralsorcery.client.effect.EffectHandler;
import hellfirepvp.astralsorcery.client.effect.light.EffectLightbeam;
import hellfirepvp.astralsorcery.client.effect.texture.TexturePlane;
import hellfirepvp.astralsorcery.client.effect.texture.TextureSpritePlane;
import hellfirepvp.astralsorcery.client.util.RenderConstellation;
import hellfirepvp.astralsorcery.client.util.SpriteLibrary;
import hellfirepvp.astralsorcery.common.constellation.CelestialHandler;
import hellfirepvp.astralsorcery.common.constellation.Constellation;
import hellfirepvp.astralsorcery.common.constellation.effect.ConstellationEffect;
import hellfirepvp.astralsorcery.common.constellation.effect.ConstellationEffectRegistry;
import hellfirepvp.astralsorcery.common.item.crystal.CrystalProperties;
import hellfirepvp.astralsorcery.common.item.crystal.base.ItemTunedCrystalBase;
import hellfirepvp.astralsorcery.common.lib.MultiBlockArrays;
import hellfirepvp.astralsorcery.common.starlight.WorldNetworkHandler;
import hellfirepvp.astralsorcery.common.starlight.transmission.IPrismTransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.ITransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.NodeConnection;
import hellfirepvp.astralsorcery.common.starlight.transmission.base.SimpleTransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.registry.TransmissionClassRegistry;
import hellfirepvp.astralsorcery.common.tile.base.TileReceiverBaseInventory;
import hellfirepvp.astralsorcery.common.util.Axis;
import hellfirepvp.astralsorcery.common.util.CrystalCalculations;
import hellfirepvp.astralsorcery.common.util.MiscUtils;
import hellfirepvp.astralsorcery.common.util.RaytraceAssist;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import hellfirepvp.astralsorcery.common.util.nbt.NBTUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: TileRitualPedestal
 * Created by HellFirePvP
 * Date: 28.09.2016 / 13:47
 */
public class TileRitualPedestal extends TileReceiverBaseInventory {

    public static final int MAX_EFFECT_TICK = 63;

    private TransmissionReceiverRitualPedestal cachePedestal = null;

    private Object spritePlane = null;

    private List<BlockPos> offsetMirrorPositions = new LinkedList<>();

    private boolean dirty = false;
    private boolean doesSeeSky = false, hasMultiblock = false;

    private int effectWorkTick = 0; //up to 63
    private boolean working = false;
    private UUID ownerUUID = null;

    public TileRitualPedestal() {
        super(1);
    }

    @Override
    public void update() {
        super.update();

        if(!worldObj.isRemote) {
            if((ticksExisted & 15) == 0) {
                updateSkyState(worldObj.canSeeSky(getPos()));
            }

            if((ticksExisted & 31) == 0) {
                updateMultiblockState();
            }

            if(dirty) {
                dirty = false;
                TransmissionReceiverRitualPedestal recNode = getUpdateCache();
                if(recNode != null) {
                    recNode.updateSkyState(doesSeeSky);
                    recNode.updateMultiblockState(hasMultiblock);

                    recNode.markDirty(worldObj);
                }
                markForUpdate();
            }
        }

        if(working) {
            if(effectWorkTick < 63) {
                effectWorkTick++;
            }
        } else {
            if(effectWorkTick > 0) {
                effectWorkTick--;
            }
        }

        if(worldObj.isRemote && working) {
            float alphaDaytime = (float) CelestialHandler.calcDaytimeDistribution(worldObj);
            alphaDaytime *= 0.8F;
            boolean isDay = alphaDaytime <= 1E-4;

            int tick = getEffectWorkTick();
            float percRunning = ((float) tick / (float) TileRitualPedestal.MAX_EFFECT_TICK);
            int chance = 15 + (int) ((1F - percRunning) * 50);
            if(EffectHandler.STATIC_EFFECT_RAND.nextInt(chance) == 0) {
                Vector3 from = new Vector3(this).add(0.5, 0.05, 0.5);
                MiscUtils.applyRandomOffset(from, EffectHandler.STATIC_EFFECT_RAND, 0.05F);
                EffectLightbeam lightbeam = EffectHandler.getInstance().lightbeam(from.clone().addY(7), from, 1.5F);
                lightbeam.setAlphaMultiplier(0.5F + (0.5F * alphaDaytime));
                lightbeam.setMaxAge(64);
            }
            if(shouldDoAdditionalEffects() && !isDay) {
                if(EffectHandler.STATIC_EFFECT_RAND.nextInt(chance * 2) == 0) {
                    Vector3 from = new Vector3(this).add(0.5, 0.1, 0.5);
                    MiscUtils.applyRandomOffset(from, EffectHandler.STATIC_EFFECT_RAND, 2F);
                    from.setY(getPos().getY() - 0.6 + 1 * EffectHandler.STATIC_EFFECT_RAND.nextFloat() * (EffectHandler.STATIC_EFFECT_RAND.nextBoolean() ? 1 : -1));
                    EffectLightbeam lightbeam = EffectHandler.getInstance().lightbeam(from.clone().addY(5 + EffectHandler.STATIC_EFFECT_RAND.nextInt(3)), from, 1.3F);
                    lightbeam.setAlphaMultiplier(alphaDaytime);
                    lightbeam.setMaxAge(64);
                }
            }
            ItemStack crystal = getStackInSlot(0);
            if(crystal != null && crystal.getItem() != null &&
                    crystal.getItem() instanceof ItemTunedCrystalBase) {
                Constellation ch = ItemTunedCrystalBase.getConstellation(crystal);
                if(ch != null) {
                    ConstellationEffect ce = ConstellationEffectRegistry.clientRenderInstance(ch);
                    if(ce != null) {
                        ce.playClientEffect(worldObj, getPos(), this, percRunning, shouldDoAdditionalEffects());
                    }
                }
            }
            for (BlockPos expMirror : offsetMirrorPositions) {
                if(ticksExisted % 32 == 0) {
                    Vector3 source = new Vector3(this).add(0.5, 0.75, 0.5);
                    Vector3 to = new Vector3(this).add(expMirror).add(0.5, 0.5, 0.5);
                    EffectHandler.getInstance().lightbeam(to, source, 0.8);
                }
            }
        }
    }

    private void updateMultiblockState() {
        boolean found = MultiBlockArrays.patternRitualPedestal.matches(worldObj, getPos());
        boolean update = hasMultiblock != found;
        this.hasMultiblock = found;
        if(update) {
            markForUpdate();
            flagDirty();
        }
    }

    //Affects only client, i'll keep the method here for misc reasons tho.
    public int getEffectWorkTick() {
        return effectWorkTick;
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    public Constellation getDisplayConstellation() {
        if(offsetMirrorPositions.size() != TransmissionReceiverRitualPedestal.MAX_MIRROR_COUNT)
            return null;
        ItemStack crystal = getStackInSlot(0);
        if(crystal != null && crystal.getItem() != null &&
                crystal.getItem() instanceof ItemTunedCrystalBase) {
            return ItemTunedCrystalBase.getConstellation(crystal);
        }
        return null;
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldDoAdditionalEffects() {
        return working && offsetMirrorPositions.size() > 0;
    }

    @Nullable
    public TransmissionReceiverRitualPedestal getUpdateCache() {
        if(cachePedestal == null) {
            cachePedestal = tryGetNode();
        }
        if(cachePedestal != null) {
            if(!cachePedestal.getPos().equals(getPos())) {
                cachePedestal = null;
            }
        }
        return cachePedestal;
    }

    protected void updateSkyState(boolean seesSky) {
        boolean update = doesSeeSky != seesSky;
        this.doesSeeSky = seesSky;
        if(update) {
            markForUpdate();
            flagDirty();
        }
    }

    @Override
    public void onLoad() {
        if(!worldObj.isRemote) {
            TransmissionReceiverRitualPedestal ped = getUpdateCache();
            if(ped != null) {
                offsetMirrorPositions.clear();
                offsetMirrorPositions.addAll(ped.offsetMirrors.keySet());
                flagDirty();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public TextureSpritePlane getHaloEffectSprite() {
        TextureSpritePlane spr = (TextureSpritePlane) spritePlane;
        if(spr == null || spr.canRemove() || spr.isRemoved()) { //Refresh.
            spr = EffectHandler.getInstance().textureSpritePlane(SpriteLibrary.spriteHalo, Axis.Y_AXIS);
            spr.setPosition(new Vector3(this).add(0.5, 0.15, 0.5));
            spr.setAlphaOverDistance(true);
            spr.setNoRotation(45);
            spr.setRefreshFunc(() -> !isInvalid() && working);
            spr.setScale(6.5F);
            spritePlane = spr;
        }
        return spr;
    }

    @Override
    protected void onInventoryChanged() {
        if(!worldObj.isRemote) {
            ItemStack in = getStackInSlot(0);
            if(in != null && in.getItem() != null &&
                    in.getItem() instanceof ItemTunedCrystalBase) {
                CrystalProperties properties = CrystalProperties.getCrystalProperties(in);
                Constellation tuned = ItemTunedCrystalBase.getConstellation(in);
                Constellation trait = ItemTunedCrystalBase.getTrait(in);
                TransmissionReceiverRitualPedestal recNode = getUpdateCache();
                if(recNode != null) {
                    recNode.updateCrystalProperties(worldObj, properties, tuned, trait);
                } else {
                    AstralSorcery.log.warn("[AstralSorcery] Updated inventory and tried to update pedestal state.");
                    AstralSorcery.log.warn("[AstralSorcery] Tried to find receiver node at dimId=" + worldObj.provider.getDimension() + " pos=" + getPos() + " - couldn't find it.");
                }
            } else {
                TransmissionReceiverRitualPedestal recNode = getUpdateCache();
                if(recNode != null) {
                    recNode.updateCrystalProperties(worldObj, null, null, null);
                } else {
                    AstralSorcery.log.warn("[AstralSorcery] Updated inventory and tried to update pedestal state.");
                    AstralSorcery.log.warn("[AstralSorcery] Tried to find receiver node at dimId=" + worldObj.provider.getDimension() + " pos=" + getPos() + " - couldn't find it.");
                }
            }
            markForUpdate();
        }
    }

    private void updatePositions(Collection<BlockPos> offsetMirrors) {
        offsetMirrorPositions.clear();
        offsetMirrorPositions.addAll(offsetMirrors);
        markForUpdate();
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);

        this.working = compound.getBoolean("working");
        this.ownerUUID = compound.getUniqueId("owner");
        this.doesSeeSky = compound.getBoolean("seesSky");
        this.hasMultiblock = compound.getBoolean("hasMultiblock");

        offsetMirrorPositions.clear();
        NBTTagList listPos = compound.getTagList("positions", 10);
        for (int i = 0; i < listPos.tagCount(); i++) {
            offsetMirrorPositions.add(NBTUtils.readBlockPosFromNBT(listPos.getCompoundTagAt(i)));
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);

        compound.setBoolean("working", working);
        compound.setUniqueId("owner", ownerUUID);
        compound.setBoolean("hasMultiblock", hasMultiblock);
        compound.setBoolean("seesSky", doesSeeSky);

        NBTTagList listPositions = new NBTTagList();
        for (BlockPos pos : offsetMirrorPositions) {
            NBTTagCompound cmp = new NBTTagCompound();
            NBTUtils.writeBlockPosToNBT(pos, cmp);
            listPositions.appendTag(cmp);
        }
        compound.setTag("positions", listPositions);
    }

    public void flagDirty() {
        this.dirty = true;
    }

    @Override
    public String getInventoryName() {
        return getUnLocalizedDisplayName();
    }

    @Nullable
    @Override
    public String getUnLocalizedDisplayName() {
        return "tile.BlockRitualPedestal.name";
    }

    @Override
    public ITransmissionReceiver provideEndpoint(BlockPos at) {
        return new TransmissionReceiverRitualPedestal(at, doesSeeSky);
    }

    public void setOwner(UUID uniqueID) {
        this.ownerUUID = uniqueID;
        markForUpdate();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nullable
    public EntityPlayer getOwningPlayerInWorld(World world) {
        UUID uuid = getOwnerUUID();
        return uuid == null ? null : world.getPlayerEntityByUUID(uuid);
    }

    public static class TransmissionReceiverRitualPedestal extends SimpleTransmissionReceiver {

        private static final int MAX_MIRROR_COUNT = 5;

        //TODO change to higher numbers for release.
        //Steps between trials: 10 minutes, 25 minutes, 50 minutes, 2 hours, 5 hours
        //private static final int[] secToNext =    new int[] { 12_000, 30_000, 60_000, 144_000, 360_000 };
        private static final int[] secToNext =    new int[] { 10, 10, 6, 10, 10 };
        //private static final int[] chanceToNext = new int[] { 50,     200,    500,    1000,    2000 };
        private static final int[] chanceToNext = new int[] { 2,     2,    2,    2,    2 };

        private static final BlockPos[] possibleOffsets = new BlockPos[] {
                new BlockPos( 4, 2,  0),
                new BlockPos( 4, 2,  1),
                new BlockPos( 3, 2,  2),
                new BlockPos( 2, 2,  3),
                new BlockPos( 1, 2,  4),
                new BlockPos( 0, 2,  4),
                new BlockPos(-1, 2,  4),
                new BlockPos(-2, 2,  3),
                new BlockPos(-3, 2,  2),
                new BlockPos(-4, 2,  1),
                new BlockPos(-4, 2,  0),
                new BlockPos(-4, 2, -1),
                new BlockPos(-3, 2, -2),
                new BlockPos(-2, 2, -3),
                new BlockPos(-1, 2, -4),
                new BlockPos( 0, 2, -4),
                new BlockPos( 1, 2, -4),
                new BlockPos( 2, 2, -3),
                new BlockPos( 3, 2, -2),
                new BlockPos( 4, 2, -1)
        };

        private int ticksTicking = 0;

        private boolean doesSeeSky, hasMultiblock;
        private Constellation channeling, trait;
        private CrystalProperties properties;
        private int channeled = 0;

        private ConstellationEffect ce;
        private Map<BlockPos, Boolean> offsetMirrors = new HashMap<>();

        private double collectionChannelBuffer = 0D, collectionTraitBuffer = 0D;
        private boolean doesWorkBuffer = false;

        public TransmissionReceiverRitualPedestal(@Nonnull BlockPos thisPos, boolean doesSeeSky) {
            super(thisPos);
            this.doesSeeSky = doesSeeSky;
        }

        @Override
        public void update(World world) {
            ticksTicking++;

            if(channeling != null && properties != null && hasMultiblock) {
                if(ce == null) {
                    ce = ConstellationEffectRegistry.getEffect(channeling);
                }
                if(ticksTicking % 20 == 0) {
                    WorldNetworkHandler handle = WorldNetworkHandler.getNetworkHandler(world);
                    List<BlockPos> toNodes = getSources();
                    for (BlockPos pos : new LinkedList<>(offsetMirrors.keySet())) {
                        BlockPos act = pos.add(getPos());
                        if(!toNodes.contains(act)) {
                            offsetMirrors.put(pos, false);
                            continue;
                        }

                        IPrismTransmissionNode node = handle.getTransmissionNode(act);
                        if(node == null) continue;

                        boolean found = false;
                        for (NodeConnection<IPrismTransmissionNode> n : node.queryNext(handle)) {
                            if(n.getTo().equals(getPos())) {
                                offsetMirrors.put(pos, n.canConnect());
                                found = true;
                            }
                        }
                        if(!found) {
                            offsetMirrors.put(pos, false);
                        }
                    }
                }

                if(ticksTicking % 60 == 0) {
                    TileRitualPedestal pedestal = getTileAtPos(world, TileRitualPedestal.class);
                    if(pedestal != null) {
                        if(pedestal.offsetMirrorPositions.size() != offsetMirrors.size()) {
                            updateMirrorPositions(world);
                        }
                    }
                }

                if(doesSeeSky) {
                    double perc = 0.2D + (0.8D * CelestialHandler.calcDaytimeDistribution(world));
                    double collect = perc * CrystalCalculations.getCollectionAmt(properties, CelestialHandler.getCurrentDistribution(channeling, (in) -> 0.2F + (0.8F * in)));
                    collectionChannelBuffer += collect / 2D;
                    /*if(collectionChannelBuffer <= 0) {
                        AstralSorcery.log.info("Ended up with < 0 starlight back from gathering it from sky.");
                        AstralSorcery.log.info("perc: " + perc + ", distr: " + CelestialHandler.calcDaytimeDistribution(world));
                        AstralSorcery.log.info("distribution: " + CelestialHandler.getCurrentDistribution(channeling, (in) -> in));
                        AstralSorcery.log.info("collection from properties from distr: " + CrystalCalculations.getCollectionAmt(properties, CelestialHandler.getCurrentDistribution(channeling, (in) -> 0.2F + (0.8F * in))));
                    }*/
                }
                if(collectionChannelBuffer > 0) {
                    doMainEffect(world, ce, trait, trait != null && collectionTraitBuffer > 0);
                    //if(collectionChannelBuffer <= 0) AstralSorcery.log.info("Ended up with < 0 starlight back from main effect.");

                    if(tryIncrementChannelingTimer())
                        channeled++;

                    flagAsWorking(world);

                    if(trait != null && collectionTraitBuffer > 0) {
                        doTraitEffect(world, ce);
                    }
                } else {
                    flagAsInactive(world);
                    ce = null;
                }
            } else {
                flagAsInactive(world);
                ce = null;
            }
        }

        private void doTraitEffect(World world, ConstellationEffect ce) {
            double maxDrain = 20D;
            maxDrain /= CrystalCalculations.getMaxRitualReduction(properties);
            maxDrain /= Math.max(1, getCollectedBackmirrors() - 1);
            int executeTimes = MathHelper.floor_double(collectionChannelBuffer / maxDrain);
            boolean consumeCompletely = executeTimes == 0;

            if(ce != null && !consumeCompletely && ce.mayExecuteMultipleTrait()) {
                collectionTraitBuffer = Math.max(0, collectionTraitBuffer - (executeTimes * maxDrain));
                if(ce.playTraitEffectMultiple(world, getPos(), trait, executeTimes)) markDirty(world);
            } else {
                for (int i = 0; i <= executeTimes; i++) {
                    float perc;
                    if(collectionTraitBuffer >= maxDrain) {
                        collectionTraitBuffer -= maxDrain;
                        perc = 1F;
                    } else if(consumeCompletely) {
                        collectionTraitBuffer = 0;
                        perc = (float) ((collectionTraitBuffer) / maxDrain);
                    } else {
                        continue;
                    }

                    if(ce != null) {
                        if(ce.playTraitEffect(world, getPos(), trait, perc)) markDirty(world);
                    }
                }
            }
        }

        //TODO occasionally returns with <0?
        private void doMainEffect(World world, ConstellationEffect ce, @Nullable Constellation trait, boolean mayDoTrait) {
            double maxDrain = 20D;
            maxDrain /= CrystalCalculations.getMaxRitualReduction(properties);
            maxDrain /= Math.max(1, getCollectedBackmirrors() - 1);
            int executeTimes = MathHelper.floor_double(collectionChannelBuffer / maxDrain);
            boolean consumeCompletely = executeTimes == 0;

            if(ce != null && !consumeCompletely && ce.mayExecuteMultipleMain()) {
                collectionChannelBuffer = Math.max(0, collectionChannelBuffer - (executeTimes * maxDrain));
                if(ce.playMainEffectMultiple(world, getPos(), executeTimes, mayDoTrait, trait)) markDirty(world);
            } else {
                for (int i = 0; i <= executeTimes; i++) {
                    float perc;
                    if(collectionChannelBuffer >= maxDrain) {
                        collectionChannelBuffer -= maxDrain;
                        perc = 1F;
                    } else if(consumeCompletely) {
                        collectionChannelBuffer = 0;
                        perc = (float) ((collectionChannelBuffer) / maxDrain);
                    } else {
                        continue;
                    }

                    if(ce != null) {
                        if(ce.playMainEffect(world, getPos(), perc, mayDoTrait, trait)) markDirty(world);
                    }
                }
            }
        }

        private int getCollectedBackmirrors() {
            int amt = 1;
            for (boolean f : offsetMirrors.values()) if(f) amt++;
            return amt;
        }

        private void flagAsInactive(World world) {
            if(doesWorkBuffer) {
                doesWorkBuffer = false;
                channeled = 0;
                TileRitualPedestal ped = getTileAtPos(world, TileRitualPedestal.class);
                if(ped != null) {
                    ped.working = false;
                    ped.markForUpdate();
                }

                clearAllMirrorPositions(world);
            }
        }

        private void flagAsWorking(World world) {
            if(!doesWorkBuffer) {
                doesWorkBuffer = true;
                TileRitualPedestal ped = getTileAtPos(world, TileRitualPedestal.class);
                if(ped != null) {
                    ped.working = true;
                    ped.markForUpdate();
                }
            }
        }

        @Override
        public void onStarlightReceive(World world, boolean isChunkLoaded, Constellation type, double amount) {
            if(channeling != null && hasMultiblock) {
                if(channeling == type) {
                    collectionChannelBuffer += amount;
                    /*if(collectionChannelBuffer <= 0) {
                        AstralSorcery.log.info("Ended up with < 0 starlight back receive: amount: " + amount);
                    }*/
                    tryGainMirrorPos(world);
                    return;
                }
                if(trait != null && trait == type) {
                    collectionTraitBuffer += amount;
                }
            }
        }

        private boolean tryIncrementChannelingTimer() {
            if(offsetMirrors.size() < 0 || offsetMirrors.size() >= 5) return false;
            if((getCollectedBackmirrors() - 1) < offsetMirrors.size()) return false;
            int step = secToNext[offsetMirrors.size()];
            return channeled <= step;
        }

        private void tryGainMirrorPos(World world) {
            //AstralSorcery.log.info("size: " + offsetMirrors.size());
            //AstralSorcery.log.info("collected: " + (getCollectedBackmirrors() - 1));
            if(offsetMirrors.size() < 0 || offsetMirrors.size() >= 5) return;
            int mirrors = offsetMirrors.size();
            if((getCollectedBackmirrors() - 1) < mirrors) return;
            int step = secToNext[mirrors];
            //AstralSorcery.log.info("step: " + step + ", channeling: " + channeled);
            if(channeled > step) {
                //AstralSorcery.log.info("try find new.");
                if(world.rand.nextInt(chanceToNext[mirrors]) == 0) {
                    findPossibleMirror(world);
                }
            }
        }

        private void findPossibleMirror(World world) {
            BlockPos offset = possibleOffsets[world.rand.nextInt(possibleOffsets.length)];
            RaytraceAssist ray = new RaytraceAssist(getPos(), getPos().add(offset));
            Vector3 from = new Vector3(0.5, 0.7, 0.5);
            Vector3 newDir = new Vector3(offset).add(0.5, 0.5, 0.5).subtract(from);
            for (BlockPos p : offsetMirrors.keySet()) {
                Vector3 toDir = new Vector3(p).add(0.5, 0.5, 0.5).subtract(from);
                if(Math.toDegrees(toDir.angle(newDir)) <= 30) return;
                if(offset.distanceSq(p) <= 3) return;
            }
            if(ray.isClear(world)) {
                addMirrorPosition(world, offset);
            }
        }

        public void addMirrorPosition(World world, BlockPos offset) {
            this.offsetMirrors.put(offset, false);
            updateMirrorPositions(world);

            markDirty(world);
        }

        public void clearAllMirrorPositions(World world) {
            this.offsetMirrors.clear();
            updateMirrorPositions(world);

            markDirty(world);
        }

        @Override
        public boolean needsUpdate() {
            return true;
        }

        /*@Override
        public void postLoad(World world) {
            updateMirrorPositions(world);
        }*/

        public void updateMirrorPositions(World world) {
            TileRitualPedestal ped = getTileAtPos(world, TileRitualPedestal.class);
            if(ped != null) {
                ped.updatePositions(offsetMirrors.keySet());
            }
        }

        @Override
        public void readFromNBT(NBTTagCompound compound) {
            super.readFromNBT(compound);

            doesSeeSky = compound.getBoolean("doesSeeSky");
            hasMultiblock = compound.getBoolean("hasMultiblock");
            channeled = compound.getInteger("channeled");
            properties = CrystalProperties.readFromNBT(compound);
            channeling = Constellation.readFromNBT(compound, Constellation.getDefaultSaveKey() + "Normal");
            trait = Constellation.readFromNBT(compound, Constellation.getDefaultSaveKey() + "Trait");

            offsetMirrors.clear();
            NBTTagList listPos = compound.getTagList("positions", 10);
            for (int i = 0; i < listPos.tagCount(); i++) {
                offsetMirrors.put(NBTUtils.readBlockPosFromNBT(listPos.getCompoundTagAt(i)), false);
            }

            if(channeling != null) {
                ce = ConstellationEffectRegistry.getEffect(channeling);
                if(compound.hasKey("effect")) {
                    NBTTagCompound cmp = compound.getCompoundTag("effect");
                    ce.readFromNBT(cmp);
                }
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound compound) {
            super.writeToNBT(compound);

            compound.setBoolean("doesSeeSky", doesSeeSky);
            compound.setBoolean("hasMultiblock", hasMultiblock);
            compound.setInteger("channeled", channeled);

            NBTTagList listPositions = new NBTTagList();
            for (BlockPos pos : offsetMirrors.keySet()) {
                NBTTagCompound cmp = new NBTTagCompound();
                NBTUtils.writeBlockPosToNBT(pos, cmp);
                listPositions.appendTag(cmp);
            }
            compound.setTag("positions", listPositions);

            if(properties != null) {
                properties.writeToNBT(compound);
            }
            if(channeling != null) {
                channeling.writeToNBT(compound, Constellation.getDefaultSaveKey() + "Normal");
            }
            if(trait != null) {
                trait.writeToNBT(compound, Constellation.getDefaultSaveKey() + "Trait");
            }
            if(ce != null) {
                NBTTagCompound tag = new NBTTagCompound();
                ce.writeToNBT(tag);
                compound.setTag("effect", tag);
            }
        }

        @Override
        public TransmissionClassRegistry.TransmissionProvider getProvider() {
            return new PedestalReceiverProvider();
        }

        /*public void update(boolean doesSeeSky, Constellation bufferChanneling, Constellation trait) {
            this.doesSeeSky = doesSeeSky;
            this.channeling = bufferChanneling;
            this.trait = trait;
        }*/

        public void updateSkyState(boolean doesSeeSky) {
            this.doesSeeSky = doesSeeSky;
        }

        public void updateMultiblockState(boolean hasMultiblock) {
            this.hasMultiblock = hasMultiblock;
        }

        public void updateCrystalProperties(World world, CrystalProperties properties, Constellation channeling, Constellation trait) {
            this.properties = properties;
            this.channeling = channeling;
            this.trait = trait;
            this.clearAllMirrorPositions(world);

            markDirty(world);
        }

    }

    public static class PedestalReceiverProvider implements TransmissionClassRegistry.TransmissionProvider {

        @Override
        public TransmissionReceiverRitualPedestal provideEmptyNode() {
            return new TransmissionReceiverRitualPedestal(null, false);
        }

        @Override
        public String getIdentifier() {
            return AstralSorcery.MODID + ":TransmissionReceiverRitualPedestal";
        }

    }

}
