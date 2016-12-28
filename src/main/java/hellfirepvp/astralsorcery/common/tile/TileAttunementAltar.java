package hellfirepvp.astralsorcery.common.tile;

import hellfirepvp.astralsorcery.AstralSorcery;
import hellfirepvp.astralsorcery.client.ClientScheduler;
import hellfirepvp.astralsorcery.client.effect.EffectHandler;
import hellfirepvp.astralsorcery.client.effect.EffectHelper;
import hellfirepvp.astralsorcery.client.effect.controller.OrbitalEffectController;
import hellfirepvp.astralsorcery.client.effect.controller.OrbitalPropertiesAttunement;
import hellfirepvp.astralsorcery.client.effect.fx.EntityFXFacingParticle;
import hellfirepvp.astralsorcery.client.effect.fx.EntityFXFacingSprite;
import hellfirepvp.astralsorcery.client.effect.light.EffectLightbeam;
import hellfirepvp.astralsorcery.client.util.camera.ClientCameraFlightHelper;
import hellfirepvp.astralsorcery.client.util.PositionedLoopSound;
import hellfirepvp.astralsorcery.client.util.SpriteLibrary;
import hellfirepvp.astralsorcery.common.constellation.ConstellationRegistry;
import hellfirepvp.astralsorcery.common.constellation.IConstellation;
import hellfirepvp.astralsorcery.common.constellation.IMajorConstellation;
import hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler;
import hellfirepvp.astralsorcery.common.constellation.star.StarConnection;
import hellfirepvp.astralsorcery.common.constellation.star.StarLocation;
import hellfirepvp.astralsorcery.common.data.research.PlayerProgress;
import hellfirepvp.astralsorcery.common.data.research.ResearchManager;
import hellfirepvp.astralsorcery.common.data.research.ResearchProgression;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.MultiBlockArrays;
import hellfirepvp.astralsorcery.common.lib.Sounds;
import hellfirepvp.astralsorcery.common.network.PacketChannel;
import hellfirepvp.astralsorcery.common.network.packet.client.PktAttuneConstellation;
import hellfirepvp.astralsorcery.common.network.packet.server.PktAttunementAltarState;
import hellfirepvp.astralsorcery.common.starlight.transmission.ITransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.base.SimpleTransmissionReceiver;
import hellfirepvp.astralsorcery.common.starlight.transmission.registry.TransmissionClassRegistry;
import hellfirepvp.astralsorcery.common.tile.base.TileReceiverBase;
import hellfirepvp.astralsorcery.common.util.MiscUtils;
import hellfirepvp.astralsorcery.common.util.SoundHelper;
import hellfirepvp.astralsorcery.common.util.data.Tuple;
import hellfirepvp.astralsorcery.common.util.data.Vector3;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: TileAttunementAltar
 * Created by HellFirePvP
 * Date: 28.11.2016 / 10:26
 */
public class TileAttunementAltar extends TileReceiverBase {

    private IMajorConstellation activeFound = null;
    private boolean doesSeeSky = false, hasMultiblock = false;

    //Attunement related
    private int mode = 0; //0 == idle, 1 == att_player, 2 == att_crystal
    private int entityIdActive = -1;
    private Entity activeEntity = null; //Unsynced

    private int playerAttunementWaitTick = -1;

    //Chunk load caching
    private Map<BlockPos, Boolean> unloadCache = new HashMap<>();

    //Sound & Visuals around the TE
    private Object activeSound = null;
    private List<Object> starSprites = new LinkedList<>();
    private IMajorConstellation highlight = null;
    private int highlightActive = 0;

    //TESR flags
    public static final int MAX_START_ANIMATION_TICK = 60;
    public static final int MAX_START_ANIMATION_SPIN = 100;
    public int activationTick = 0;
    public int prevActivationTick = 0;
    public boolean animate = false, tesrLocked = true;

    private boolean cameraFlightActive = false;
    private Object clientActiveCameraFlight = null;

    @Override
    public void update() {
        super.update();

        if(world.isRemote) {
            renderEffects();
        } else {
            if(getTicksExisted() % 10 == 0) {
                if(activeFound == null) {
                    searchForConstellation();
                } else {
                    matchActiveConstellation();
                }
            }
            if((ticksExisted & 15) == 0) {
                updateSkyState();
            }

            if((ticksExisted & 31) == 0) {
                updateMultiblockState();
            }
            if(activeFound == null && getTicksExisted() % 10 == 0 && hasMultiblock) {
                searchForConstellation();
            }

            if(activeFound != null) {
                if(mode == 0) {
                    if(ConstellationSkyHandler.getInstance().isNight(world)) {
                        checkForAttunements();
                    }
                } else if(mode == 1) {
                    if(activeEntity == null || !(activeEntity instanceof EntityPlayer) || activeEntity.isDead) {
                        setAttunementState(0, null);
                    } else {
                        if(playerAttunementWaitTick > 0) {
                            playerAttunementWaitTick--;
                        }
                        if(playerAttunementWaitTick == 0) {
                            setAttunementState(0, null);
                            playerAttunementWaitTick = -1;
                        }
                    }
                } else if(mode == 2) {
                    //Uhh... item things. ?
                }
            }
        }
    }

    private void checkForAttunements() {
        if((ticksExisted & 31) != 0) return;
        List<EntityPlayerMP> players = world.getEntitiesWithinAABB(EntityPlayerMP.class, new AxisAlignedBB(0, 0, 0, 1, 1, 1).expandXyz(1).offset(getPos()));
        if(!players.isEmpty()) {
            EntityPlayerMP pl = players.get(0);
            if(MiscUtils.isPlayerFakeMP(pl)) return;
            PlayerProgress prog = ResearchManager.getProgress(pl, Side.SERVER);
            if(prog == null || prog.getAttunedConstellation() != null) return;
            if(!prog.getResearchProgression().contains(ResearchProgression.ATTUNEMENT)) return;
            if(!prog.getKnownConstellations().contains(activeFound.getUnlocalizedName())) return;

            PktAttunementAltarState state = new PktAttunementAltarState(pl.getEntityId(), world.provider.getDimension(), getPos());
            PacketChannel.CHANNEL.sendTo(state, pl);
            return;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return super.getRenderBoundingBox().expand(3.5, 2, 3.5);
    }

    protected void updateSkyState() {
        boolean seesSky = true;
        BlockPos at = getPos();
        lbl:
        for (int xx = -7; xx <= 7; xx++) {
            for (int zz = -7; zz <= 7; zz++) {
                BlockPos other = at.add(xx, 0, zz);
                if(MiscUtils.isChunkLoaded(world, new ChunkPos(other))) {
                    boolean see = itDown(other) <= other.getY() + 1;
                    unloadCache.put(other, see);
                    if(!see) {
                        seesSky = false;
                        break lbl;
                    }
                } else if(unloadCache.containsKey(other)) {
                    if(!unloadCache.get(other)) {
                        seesSky = false;
                        break lbl;
                    }
                } else {
                    boolean see = itDown(other) <= other.getY() + 1;
                    unloadCache.put(other, see);
                    if(!see) {
                        seesSky = false;
                        break lbl;
                    }
                }
            }
        }
        boolean update = doesSeeSky != seesSky;
        this.doesSeeSky = seesSky;
        if(update) {
            markForUpdate();
        }
    }

    private int itDown(BlockPos xzPos) {
        BlockPos.PooledMutableBlockPos mut = BlockPos.PooledMutableBlockPos.retain();
        mut.setPos(xzPos);
        for (int i = 255; i >= 0; i--) {
            mut.setY(i);
            if(!world.isAirBlock(mut)) {
                mut.release();
                return i;
            }
        }
        return -1;
    }

    private void updateMultiblockState() {
        boolean found = MultiBlockArrays.patternAttunementFrame.matches(world, getPos());
        boolean update = hasMultiblock != found;
        this.hasMultiblock = found;
        if(update) {
            markForUpdate();
        }
    }

    private void matchActiveConstellation() {
        List<BlockPos> positions = translateConstellationPositions(activeFound);
        boolean valid = true;
        for (BlockPos pos : positions) {
            if(pos.equals(getPos())) continue;
            IBlockState state = world.getBlockState(pos);
            if(!state.getBlock().equals(BlocksAS.attunementRelay)) {
                valid = false;
            }
        }
        if(!valid) {
            activeFound = null;
            markForUpdate();
        }
    }

    private void searchForConstellation() {
        IMajorConstellation match = null;
        for (IMajorConstellation attuneable : ConstellationRegistry.getMajorConstellations()) {
            List<BlockPos> positions = translateConstellationPositions(attuneable);
            boolean valid = true;
            for (BlockPos pos : positions) {
                if(pos.equals(getPos())) continue;
                IBlockState state = world.getBlockState(pos);
                if(!state.getBlock().equals(BlocksAS.attunementRelay)) {
                    valid = false;
                }
            }
            if(valid) {
                match = attuneable;
                break;
            }
        }
        if(match != null) {
            activeFound = match;
            markForUpdate();
        }
    }

    public void markPlayerStartCameraFlight(EntityPlayer pl) {
        setAttunementState(1, pl);
        this.playerAttunementWaitTick = 1000; //Depends on the camera flight... awkwardly enough.. client has a bit more time to answer too.
    }

    public void askForAttunement(EntityPlayerMP playerEntity, IMajorConstellation cst) {
        if(mode == 1 && playerAttunementWaitTick > 0 && activeEntity != null && playerEntity.equals(activeEntity)) {
            PlayerProgress prog = ResearchManager.getProgress(playerEntity, Side.SERVER);
            if(prog != null && prog.getAttunedConstellation() == null &&
                    prog.getResearchProgression().contains(ResearchProgression.ATTUNEMENT) &&
                    prog.getKnownConstellations().contains(cst.getUnlocalizedName())) {
                ResearchManager.setAttunedConstellation(playerEntity, cst);
            }
        }
        setAttunementState(0, null);
        playerAttunementWaitTick = -1;
    }

    private void setAttunementState(int mode, Entity trigger) {
        mode = MathHelper.clamp(mode, 0, 2);
        this.mode = mode;
        switch (mode) {
            case 0:
                this.entityIdActive = -1;
                this.activeEntity = null;
                this.playerAttunementWaitTick = -1;
                break;
            case 1:
            case 2:
                this.entityIdActive = trigger.getEntityId();
                this.activeEntity = trigger;
                break;
        }
        markForUpdate();
    }

    @SideOnly(Side.CLIENT)
    public boolean tryStartCameraFlight() {
        if(cameraFlightActive || !isClientCloseEnough()) {
            return false;
        }

        Vector3 offset = new Vector3(this).add(0, 6, 0);
        ClientCameraFlightHelper.CameraFlightBuilder builder = ClientCameraFlightHelper.builder(offset.clone().add(4, 0, 4), new Vector3(this).add(0.5, 0.5, 0.5));
        builder.addCircularPoints(offset, ClientCameraFlightHelper.DynamicRadiusGetter.dyanmicIncrease( 5,  0.025), 200, 2);
        builder.addCircularPoints(offset, ClientCameraFlightHelper.DynamicRadiusGetter.dyanmicIncrease(10, -0.01) , 200, 2);
        builder.setTickDelegate(createFloatDelegate(new Vector3(this).add(0.5F, 1.2F, 0.5F)));
        builder.setStopDelegate(createAttunementDelegate());

        OrbitalPropertiesAttunement att = new OrbitalPropertiesAttunement();
        OrbitalEffectController ctrl = EffectHandler.getInstance().orbital(att, att, null);
        ctrl.setOrbitAxis(Vector3.RotAxis.Y_AXIS).setOrbitRadius(3)
                .setTicksPerRotation(80).setOffset(new Vector3(this).add(0.5, 0.5, 0.5));

        ctrl = EffectHandler.getInstance().orbital(att, att, null);
        ctrl.setOrbitAxis(Vector3.RotAxis.Y_AXIS).setOrbitRadius(3)
                .setTicksPerRotation(80).setTickOffset(40).setOffset(new Vector3(this).add(0.5, 0.5, 0.5));

        this.clientActiveCameraFlight = builder.finishAndStart();
        this.cameraFlightActive = true;
        return true;
    }

    @SideOnly(Side.CLIENT)
    private boolean isClientCloseEnough() {
        List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(0, 0, 0, 1, 1, 1).expandXyz(1).offset(getPos()));
        return !players.isEmpty() && players.contains(Minecraft.getMinecraft().player);
    }

    private void checkCameraFlightIntegrity() {
        if(clientActiveCameraFlight != null) {
            checkCameraClient();
        }
    }

    @SideOnly(Side.CLIENT)
    private void checkCameraClient() {
        if(mode != 1 || entityIdActive != Minecraft.getMinecraft().player.getEntityId()) {
            ((ClientCameraFlightHelper.CameraFlight) clientActiveCameraFlight).forceStop();
            clientActiveCameraFlight = null;
        }
    }

    @SideOnly(Side.CLIENT)
    private void renderEffects() {
        if(highlightActive > 0) {
            highlightActive--;
        }

        if(!hasMultiblock || !doesSeeSky) {
            starSprites.clear();
            activeSound = null;
            animate = false;

            prevActivationTick = activationTick;
            if(activationTick > 0) {
                activationTick--;
            }

            if(clientActiveCameraFlight != null) {
                ((ClientCameraFlightHelper.CameraFlight) clientActiveCameraFlight).forceStop();
                clientActiveCameraFlight = null;
            }

        } else if(activeFound == null) {
            starSprites.clear();
            activeSound = null;
            animate = false;

            prevActivationTick = activationTick;
            if(activationTick > 0) {
                activationTick--;
            }
            if(clientActiveCameraFlight != null) {
                ((ClientCameraFlightHelper.CameraFlight) clientActiveCameraFlight).forceStop();
                clientActiveCameraFlight = null;
            }

            spawnAmbientParticles();
            if(highlight != null && highlightActive > 0) {
                List<BlockPos> positions = translateConstellationPositions(highlight);
                for (BlockPos pos : positions) {
                    if(rand.nextBoolean()) continue;
                    EntityFXFacingParticle p = EffectHelper.genericFlareParticle(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).gravity(0.01);
                    p.offset(rand.nextFloat() * 0.7 * (rand.nextBoolean() ? 1 : -1), rand.nextFloat() * 0.7 * (rand.nextBoolean() ? 1 : -1), rand.nextFloat() * 0.7 * (rand.nextBoolean() ? 1 : -1));
                    p.scale(0.4F + rand.nextFloat() * 0.1F);
                    p.setAlphaMultiplier(0.5F);
                }
            }
        } else {
            if(Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.MASTER) > 0) {
                if(activeSound == null || ((PositionedLoopSound) activeSound).hasStoppedPlaying()) {
                    activeSound = SoundHelper.playSoundLoopClient(Sounds.attunement, new Vector3(this).add(0.5, 0.5, 0.5), 0.7F, 0.8F,
                            () -> isInvalid() ||
                                    activeFound == null ||
                                    Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.MASTER) <= 0);
                }
            }

            /*if(!sheduledCameraFlight && clientActiveCameraFlight == null) {
                List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(0, 0, 0, 1, 1, 1).expandXyz(1).offset(getPos()));
                if(!players.isEmpty() && players.contains(Minecraft.getMinecraft().player)) {
                    sheduledCameraFlight = true;
                    Vector3 offset = new Vector3(this).add(0, 6, 0);
                    ClientCameraFlightHelper.CameraFlightBuilder builder = ClientCameraFlightHelper.builder(offset.clone().add(4, 0, 4), new Vector3(this).add(0.5, 0.5, 0.5));
                    builder.addCircularPoints(offset, ClientCameraFlightHelper.DynamicRadiusGetter.dyanmicIncrease( 5,  0.025), 200, 2);
                    builder.addCircularPoints(offset, ClientCameraFlightHelper.DynamicRadiusGetter.dyanmicIncrease(10, -0.01) , 200, 2);
                    builder.setTickDelegate(createFloatDelegate(new Vector3(this).add(0.5F, 1.2F, 0.5F)));
                    builder.setStopDelegate(createAttunementDelegate());

                    OrbitalPropertiesAttunement att = new OrbitalPropertiesAttunement();
                    OrbitalEffectController ctrl = EffectHandler.getInstance().orbital(att, att, null);
                    ctrl.setOrbitAxis(Vector3.RotAxis.Y_AXIS).setOrbitRadius(3)
                            .setTicksPerRotation(80).setOffset(new Vector3(this).add(0.5, 0.5, 0.5));

                    ctrl = EffectHandler.getInstance().orbital(att, att, null);
                    ctrl.setOrbitAxis(Vector3.RotAxis.Y_AXIS).setOrbitRadius(3)
                            .setTicksPerRotation(80).setTickOffset(40).setOffset(new Vector3(this).add(0.5, 0.5, 0.5));

                    this.clientActiveCameraFlight = builder.finishAndStart();
                }
            }*/

            animate = true;
            prevActivationTick = activationTick;
            if(activationTick < MAX_START_ANIMATION_TICK) {
                activationTick++;
            }

            for(Object o : starSprites) {
                EntityFXFacingSprite p = (EntityFXFacingSprite) o;
                if(p.isRemoved()) {
                    EffectHandler.getInstance().registerFX(p);
                }
            }
            if(starSprites.isEmpty()) {
                addStarSprites();
            }
            if(getTicksExisted() % 53 == 0) {
                addConnectionBeams();
            }
            spawnAmbientParticles();
            spawnAmbientActiveParticles();
        }

    }

    @SideOnly(Side.CLIENT)
    private ClientCameraFlightHelper.StopDelegate createAttunementDelegate() {
        return () -> {
            if(clientActiveCameraFlight != null && ((ClientCameraFlightHelper.CameraFlight) clientActiveCameraFlight).isExpired()
                    && !((ClientCameraFlightHelper.CameraFlight) clientActiveCameraFlight).wasForciblyStopped()) {
                if(activeFound != null) {
                    PacketChannel.CHANNEL.sendToServer(new PktAttuneConstellation(activeFound, world.provider.getDimension(), getPos()));
                    SoundHelper.playSoundClientWorld(Sounds.craftFinish, pos, 1F, 1.4F);
                }
            }
            this.cameraFlightActive = false;
            this.clientActiveCameraFlight = null;
        };
    }

    @SideOnly(Side.CLIENT)
    private ClientCameraFlightHelper.TickDelegate createFloatDelegate(Vector3 offsetPos) {
        return (renderView, focusedEntity) -> {
            if(focusedEntity == null) return;

            float floatTick = (ClientScheduler.getClientTick() % 40) / 40F;
            float sin = MathHelper.sin((float) (floatTick * 2 * Math.PI)) / 2F + 0.5F;
            focusedEntity.setAlwaysRenderNameTag(false);
            focusedEntity.setPositionAndRotation(offsetPos.getX(), offsetPos.getY() + sin * 0.2D, offsetPos.getZ(), 0F, 0F);
            focusedEntity.setPositionAndRotation(offsetPos.getX(), offsetPos.getY() + sin * 0.2D, offsetPos.getZ(), 0F, 0F);
            focusedEntity.rotationYawHead = 0;
            focusedEntity.prevRotationYawHead = 0;
            focusedEntity.setVelocity(0, 0, 0);

            playAttenuationEffects(renderView.ticksExisted);
        };
    }

    @SideOnly(Side.CLIENT)
    private void playAttenuationEffects(int cameraFlightTick) {
        if(activeFound == null) return;

        if(cameraFlightTick >= 0 && cameraFlightTick <= 800) {
            if(cameraFlightTick % 30 == 0) {
                List<BlockPos> offsets = translateConstellationPositions(activeFound);
                Color ov = new Color(0x2100FD);
                float cR = ov.getRed() / 255F;
                float cG = ov.getGreen() / 255F;
                float cB = ov.getBlue() / 255F;
                for (BlockPos effectPos : offsets) {
                    Vector3 from = new Vector3(effectPos).add(0.5, -0.1, 0.5);
                    MiscUtils.applyRandomOffset(from, rand, 0.1F);
                    EffectLightbeam lightbeam = EffectHandler.getInstance().lightbeam(from.clone().addY(6), from, 1.5F);
                    lightbeam.setAlphaMultiplier(0.8F);
                    lightbeam.setColorOverlay(cR, cG, cB, 0.2F);
                    lightbeam.setMaxAge(64);
                }
            }
        }
        if(cameraFlightTick >= 200) {
            for (int i = 0; i < 2; i++) {
                List<BlockPos> offsets = translateConstellationPositions(activeFound);
                BlockPos pos = offsets.get(rand.nextInt(offsets.size()));
                Vector3 offset = new Vector3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
                Vector3 dir = new Vector3(this).add(0.5, 3, 0.5).subtract(offset);
                EntityFXFacingParticle p = EffectHelper.genericFlareParticle(offset.getX(), offset.getY(), offset.getZ());
                p.setColor(Color.WHITE).scale(0.3F + rand.nextFloat() * 0.1F).gravity(0.004).motion(dir.getX() / 40D, dir.getY() / 40D, dir.getZ() / 40D);
            }
        }
        if(cameraFlightTick >= 350) {
            for (int i = 0; i < 3; i++) {
                Vector3 from = new Vector3(this).add(0.5, 0.5, 0.5);
                from.addX(rand.nextFloat() * 6F * (rand.nextBoolean() ? 1 : -1));
                from.addZ(rand.nextFloat() * 6F * (rand.nextBoolean() ? 1 : -1));
                Vector3 dir = new Vector3(this).add(0.5, 3, 0.5).subtract(from);
                EntityFXFacingParticle p = EffectHelper.genericFlareParticle(from.getX(), from.getY(), from.getZ());
                p.setColor(Color.WHITE).scale(0.3F + rand.nextFloat() * 0.1F).gravity(0.004).motion(dir.getX() / 40D, dir.getY() / 40D, dir.getZ() / 40D);
            }
        }
        if(cameraFlightTick >= 500) {
            for (int i = 0; i < 4; i++) {
                Vector3 at = new Vector3(this).add(0.5, 0.1, 0.5);
                at.addX(rand.nextFloat() * 7F * (rand.nextBoolean() ? 1 : -1));
                at.addZ(rand.nextFloat() * 7F * (rand.nextBoolean() ? 1 : -1));
                EntityFXFacingParticle p = EffectHelper.genericFlareParticle(at.getX(), at.getY(), at.getZ());
                p.setAlphaMultiplier(0.7F);
                if(rand.nextBoolean()) p.setColor(Color.WHITE);
                p.setMaxAge((int) (30 + rand.nextFloat() * 50));
                p.gravity(0.05).scale(0.3F + rand.nextFloat() * 0.1F);
            }
        }
        if(cameraFlightTick >= 600) {
            if(cameraFlightTick % 5 == 0) {
                Vector3 from = new Vector3(this).add(0.5, -0.1, 0.5);
                MiscUtils.applyRandomOffset(from, rand, 0.3F);
                EffectLightbeam lightbeam = EffectHandler.getInstance().lightbeam(from.clone().addY(8), from, 2.4F);
                lightbeam.setAlphaMultiplier(0.8F);
                lightbeam.setMaxAge(64);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void spawnAmbientActiveParticles() {
        if(rand.nextInt(3) == 0) {
            Vector3 at = new Vector3(this).add(0, 0.1, 0);
            at.add(rand.nextFloat() * 3 - 1, 0, rand.nextFloat() * 3 - 1);
            EntityFXFacingParticle p = EffectHelper.genericFlareParticle(at.getX(), at.getY(), at.getZ());
            p.setAlphaMultiplier(0.7F);
            p.setMaxAge((int) (30 + rand.nextFloat() * 50));
            p.gravity(0.05).scale(0.3F + rand.nextFloat() * 0.1F);
        }
    }

    @SideOnly(Side.CLIENT)
    private void spawnAmbientParticles() {
        if(rand.nextBoolean()) {
            Vector3 at = new Vector3(this).add(0, 0.01, 0);
            at.add(rand.nextFloat() * 15 - 7, 0, rand.nextFloat() * 15 - 7);
            EntityFXFacingParticle p = EffectHelper.genericFlareParticle(at.getX(), at.getY(), at.getZ());
            p.setAlphaMultiplier(0.7F);
            p.setColor(Color.WHITE);
            p.gravity(0.004).scale(0.3F + rand.nextFloat() * 0.1F);
        }
    }

    @SideOnly(Side.CLIENT)
    private void addConnectionBeams() {
        List<Tuple<BlockPos, BlockPos>> connectionTuples = translateConnectionPositions(activeFound);
        Color ov = new Color(0x2100FD);
        float cR = ov.getRed() / 255F;
        float cG = ov.getGreen() / 255F;
        float cB = ov.getBlue() / 255F;
        float alpha = 0.2F;
        for (Tuple<BlockPos, BlockPos> connection : connectionTuples) {
            Vector3 from = new Vector3(connection.key)  .add(0.5, 0.5, 0.5);
            Vector3 to   = new Vector3(connection.value).add(0.5, 0.5, 0.5);
            EffectHandler.getInstance().lightbeam(from, to, 1.1).setColorOverlay(cR, cG, cB, alpha);
            EffectHandler.getInstance().lightbeam(to, from, 1.1).setColorOverlay(cR, cG, cB, alpha);
        }
    }

    @SideOnly(Side.CLIENT)
    private void addStarSprites() {
        List<BlockPos> positions = translateConstellationPositions(activeFound);
        for (BlockPos pos : positions) {
            EntityFXFacingSprite sprite = EntityFXFacingSprite.fromSpriteSheet(SpriteLibrary.spriteStar1, pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5, 1.5F, 2);
            EffectHandler.getInstance().registerFX(sprite);
            starSprites.add(sprite);
            sprite.setRefreshFunc(() -> starSprites.contains(sprite) && !isInvalid());
        }
    }

    @Nullable
    @Override
    public String getUnLocalizedDisplayName() {
        return "tile.BlockAttunementAltar.name";
    }

    private void receiveStarlight(IMajorConstellation type, double amount) {}

    @Override
    public ITransmissionReceiver provideEndpoint(BlockPos at) {
        return new TransmissionReceiverAttunementAltar(at);
    }

    @SideOnly(Side.CLIENT)
    public void highlightConstellation(IMajorConstellation highlight) {
        this.highlight = highlight;
        this.highlightActive = 60;
    }

    private List<Tuple<BlockPos, BlockPos>> translateConnectionPositions(IConstellation cst) {
        List<Tuple<BlockPos, BlockPos>> offsetPositions = new LinkedList<>();
        for (StarConnection c : cst.getStarConnections()) {
            StarLocation from = c.from;
            StarLocation to = c.to;
            offsetPositions.add(new Tuple<>(new BlockPos(from.x / 2 - 7, 0, from.y / 2 - 7).add(getPos()), new BlockPos(to.x / 2 - 7, 0, to.y / 2 - 7).add(getPos())));
        }
        return offsetPositions;
    }

    private List<BlockPos> translateConstellationPositions(IConstellation cst) {
        List<BlockPos> offsetPositions = new LinkedList<>();
        for (StarLocation sl : cst.getStars()) {
            int x = sl.x / 2;
            int z = sl.y / 2;
            offsetPositions.add(new BlockPos(x - 7, 0, z - 7).add(getPos()));
        }
        return offsetPositions;
    }

    @Override
    public void readCustomNBT(NBTTagCompound compound) {
        super.readCustomNBT(compound);

        this.hasMultiblock = compound.getBoolean("mbState");
        this.doesSeeSky = compound.getBoolean("skState");

        this.mode = compound.getInteger("modeId");
        this.entityIdActive = compound.getInteger("entityId");

        checkCameraFlightIntegrity();

        IConstellation found = IConstellation.readFromNBT(compound);
        if(found == null || !(found instanceof IMajorConstellation)) {
            activeFound = null;
        } else {
            activeFound = (IMajorConstellation) found;
        }
    }

    @Override
    public void writeCustomNBT(NBTTagCompound compound) {
        super.writeCustomNBT(compound);

        compound.setBoolean("mbState", hasMultiblock);
        compound.setBoolean("skState", doesSeeSky);

        compound.setInteger("modeId", mode);
        compound.setInteger("entityId", entityIdActive);

        if (activeFound != null) {
            activeFound.writeToNBT(compound);
        }
    }

    public static class TransmissionReceiverAttunementAltar extends SimpleTransmissionReceiver {

        public TransmissionReceiverAttunementAltar(@Nonnull BlockPos thisPos) {
            super(thisPos);
        }

        @Override
        public void onStarlightReceive(World world, boolean isChunkLoaded, IMajorConstellation type, double amount) {
            if(isChunkLoaded) {
                TileAttunementAltar ta = MiscUtils.getTileAt(world, getPos(), TileAttunementAltar.class, false);
                if(ta != null) {
                    ta.receiveStarlight(type, amount);
                }
            }
        }

        @Override
        public TransmissionClassRegistry.TransmissionProvider getProvider() {
            return new AttunementAltarReceiverProvider();
        }

    }

    public static class AttunementAltarReceiverProvider implements TransmissionClassRegistry.TransmissionProvider {

        @Override
        public TransmissionReceiverAttunementAltar provideEmptyNode() {
            return new TransmissionReceiverAttunementAltar(null);
        }

        @Override
        public String getIdentifier() {
            return AstralSorcery.MODID + ":TransmissionReceiverAttunementAltar";
        }

    }

}
