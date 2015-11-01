package makeo.gadomancy.client.util;

import baubles.api.BaublesApi;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import makeo.gadomancy.common.network.packets.PacketFamiliar;
import makeo.gadomancy.common.registration.RegisteredItems;
import makeo.gadomancy.common.utils.FakeWorld;
import makeo.gadomancy.common.utils.NBTHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.renderers.entity.RenderPrimalOrb;
import thaumcraft.client.renderers.entity.RenderWisp;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.entities.monster.EntityWisp;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is part of the Gadomancy Mod
 * Gadomancy is Open Source and distributed under the
 * GNU LESSER GENERAL PUBLIC LICENSE
 * for more read the LICENSE file
 *
 * Created by HellFirePvP @ 31.10.2015 12:13
 */
public class FamiliarHandlerClient {
    private static final EntityWisp ENTITY_WISP = new EntityWisp(new FakeWorld());

    private static RenderWisp fallbackRenderer;

    private static Map<String, PartialEntityFamiliar> clientFamiliars = new HashMap<String, PartialEntityFamiliar>();
    private static List<String> familiarPlayers = new ArrayList<String>();

    @SideOnly(Side.CLIENT)
    public static void processPacket(PacketFamiliar packet) {
        if(packet instanceof PacketFamiliar.PacketFamiliarBolt) {
            PacketFamiliar.PacketFamiliarBolt pkt = (PacketFamiliar.PacketFamiliarBolt) packet;
            EntityPlayer p = Minecraft.getMinecraft().theWorld.getPlayerEntityByName(pkt.owner);
            if(p == null || p.getDistanceSqToEntity(Minecraft.getMinecraft().thePlayer) > 1024) return; //32^2
            PartialEntityFamiliar fam = clientFamiliars.get(p.getCommandSenderName());
            if(fam == null) return;
            Thaumcraft.proxy.nodeBolt(Minecraft.getMinecraft().theWorld, (float) fam.posX, (float) fam.posY, (float) fam.posZ, pkt.targetX, pkt.targetY, pkt.targetZ);
            //TODO zap given coordinates from current familiar render coordinates
        } else if(packet instanceof PacketFamiliar.PacketFamiliarSyncCompletely) {
            PacketFamiliar.PacketFamiliarSyncCompletely sync = (PacketFamiliar.PacketFamiliarSyncCompletely) packet;
            familiarPlayers = sync.playerNamesWithFamiliars;
        } else if(packet instanceof PacketFamiliar.PacketFamiliarSyncSingle) {
            PacketFamiliar.PacketFamiliarSyncSingle sync = (PacketFamiliar.PacketFamiliarSyncSingle) packet;
            String value = sync.name;
            if(sync.status) {
                if(!familiarPlayers.contains(value)) {
                    familiarPlayers.add(value);
                    clientFamiliars.put(value, new PartialEntityFamiliar(Minecraft.getMinecraft().theWorld.getPlayerEntityByName(value), value));
                }
            } else {
                clientFamiliars.remove(value);
                familiarPlayers.remove(value);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public static void playerRenderEvent(EntityPlayer player, float partialTicks) { //RenderPlayerEvent.Post
        EntityLivingBase renderView = Minecraft.getMinecraft().renderViewEntity;
        for(String ownerName : clientFamiliars.keySet()) {
            PartialEntityFamiliar fam = clientFamiliars.get(ownerName);

            if(!ownerName.equals(player.getCommandSenderName())) continue;
            if(renderView.worldObj.provider.dimensionId != player.worldObj.provider.dimensionId) continue;
            if(renderView.getDistanceSqToEntity(player) > 1024) continue;

            //Info: "Thanks" to RenderPlayerEvent, GL11 offset is moved to the player already.
            //GL11 coordinates: Player.posX, Player.posY + Player.eyeHeight (1.62 client side..), Player.posZ, rotation: 0

            ItemStack stack = BaublesApi.getBaubles(player).getStackInSlot(0);
            if(stack != null && stack.getItem() == RegisteredItems.itemFamiliar
                    && stack.hasTagCompound() && stack.getTagCompound().hasKey("aspect")) {
                //ENTITY_WISP.setType(stack.getTagCompound().getString("aspect"));
                //ENTITY_WISP.setType(Aspect.AIR.getName());
                ENTITY_WISP.getDataWatcher().getWatchedObject(22).setObject(Aspect.AIR.getName());
                ENTITY_WISP.ticksExisted = fam.dummyEntity.ticksExisted;
                fallbackRenderer.renderEntityAt(ENTITY_WISP, fam.renderX, fam.renderY, fam.renderZ, 0, partialTicks);
            }
            //Render at abs positions: ram.posX, fam.posY, fam.posZ
        }
    }

    @SideOnly(Side.CLIENT)
    public static void playerTickEvent() {
        if(Minecraft.getMinecraft().theWorld == null) return;
        for(PartialEntityFamiliar fam : clientFamiliars.values()) fam.tick();
        PartialEntityFamiliar.DUMMY_FAMILIAR.tick();
    }

    static {
        fallbackRenderer = new RenderWisp();
        fallbackRenderer.setRenderManager(RenderManager.instance);
    }

    public static class DummyEntityFamiliar extends Entity {

        public DummyEntityFamiliar(World p_i1582_1_) {
            super(p_i1582_1_);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(NBTTagCompound p_70037_1_) {}

        @Override
        protected void writeEntityToNBT(NBTTagCompound p_70014_1_) {}
    }

    public static class PartialEntityFamiliar {

        public static final PartialEntityFamiliar DUMMY_FAMILIAR = new PartialEntityFamiliar(null, "§Invalid§");

        private static final double RAD_CAP = 2 * Math.PI;
        private static final int CRICLE_TIME_TICKS = 160;
        private static final double RADIUS = 1;

        public DummyEntityFamiliar dummyEntity = new DummyEntityFamiliar(null);
        public WeakReference<EntityPlayer> owner;
        public String potentialOwnerName;
        public double posX, posY, posZ;
        public double renderX, renderY, renderZ;
        public double prevPosX, prevPosY, prevPosZ;
        public int ticksExisted = 0;

        public PartialEntityFamiliar(EntityPlayer owner, String ownerNameToSearch) {
            this.owner = new WeakReference<EntityPlayer>(owner);
            this.potentialOwnerName = ownerNameToSearch;
        }

        public void tick() {
            ticksExisted++;
            dummyEntity.ticksExisted = ticksExisted;

            if(owner.get() == null) {
                if(potentialOwnerName != null) {
                    EntityPlayer player = Minecraft.getMinecraft().theWorld.getPlayerEntityByName(potentialOwnerName);
                    if(player != null) {
                        owner = new WeakReference<EntityPlayer>(player);
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else {
                if(owner.get().worldObj.provider.dimensionId != Minecraft.getMinecraft().renderViewEntity.worldObj.provider.dimensionId) {
                    owner = new WeakReference<EntityPlayer>(null);
                    return;
                }
            }

            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;

            double part = ((double) ticksExisted) / ((double) CRICLE_TIME_TICKS);
            double theta = RAD_CAP * part;
            this.renderX = RADIUS * Math.cos(theta);
            this.renderZ = RADIUS * Math.sin(theta);
            this.renderY = 0.7 + Math.sin(theta * 2) / 4D;

            this.posX = owner.get().posX + renderX;
            this.posZ = owner.get().posZ + renderZ;
            this.posY = owner.get().posY + renderY;
        }
    }

}