package team.creative.cmdcam.common.target;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.common.math.vec.Vec3d;
import team.creative.cmdcam.common.util.EntityUtil;

/** Something the camera can look at or follow. */
public abstract class CamTarget {

    /** @return the target or null if the data is not valid */
    public static CamTarget load(NBTTagCompound nbt) {
        try {
            String id = nbt.getString("id");
            if ("pos".equals(id)) {
                int[] data = nbt.getIntArray("data");
                return data.length == 3 ? new BlockTarget(data[0], data[1], data[2]) : null;
            }
            if ("entity".equals(id))
                return new EntityTarget(UUID.fromString(nbt.getString("uuid")));
            if ("player".equals(id))
                return new PlayerTarget(UUID.fromString(nbt.getString("uuid")));
            if ("self".equals(id))
                return new SelfTarget();
        } catch (IllegalArgumentException e) {
            CMDCam.LOGGER.warn("Skipped invalid camera target {}", nbt);
        }
        return null;
    }

    /** @return the eye position (or block center) of the target, or null if it cannot be found right now */
    public abstract Vec3d position(World world, float partialTicks);

    public abstract NBTTagCompound save();

    public abstract IChatComponent print(World world);

    private static Entity findEntity(World world, UUID uuid) {
        for (Object object : world.loadedEntityList) {
            Entity entity = (Entity) object;
            if (uuid.equals(entity.getUniqueID()))
                return entity;
        }
        return null;
    }

    private static EntityPlayer findPlayer(World world, UUID uuid) {
        for (Object object : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (uuid.equals(player.getUniqueID()))
                return player;
        }
        return null;
    }

    public static class BlockTarget extends CamTarget {

        public final int x;
        public final int y;
        public final int z;

        public BlockTarget(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Vec3d position(World world, float partialTicks) {
            return new Vec3d(x + 0.5, y + 0.5, z + 0.5);
        }

        @Override
        public NBTTagCompound save() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("id", "pos");
            nbt.setIntArray("data", new int[] { x, y, z });
            return nbt;
        }

        @Override
        public IChatComponent print(World world) {
            return new ChatComponentTranslation("scene.output.pos", x, y, z);
        }
    }

    public static class EntityTarget extends CamTarget {

        public final UUID uuid;
        private Entity cached;

        public EntityTarget(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public Vec3d position(World world, float partialTicks) {
            // Also finds the entity again after it left and reentered the loaded area
            if (cached == null || cached.isDead || cached.worldObj != world)
                cached = findEntity(world, uuid);
            if (cached == null || !cached.isEntityAlive())
                return null;
            return EntityUtil.eyePosition(cached, partialTicks);
        }

        @Override
        public NBTTagCompound save() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("id", "entity");
            nbt.setString("uuid", uuid.toString());
            return nbt;
        }

        @Override
        public IChatComponent print(World world) {
            Entity entity = findEntity(world, uuid);
            if (entity != null)
                return new ChatComponentTranslation("scene.output.entity_found", entity.getCommandSenderName(), uuid.toString());
            return new ChatComponentTranslation("scene.output.entity", uuid.toString());
        }
    }

    public static class SelfTarget extends CamTarget {

        @Override
        public Vec3d position(World world, float partialTicks) {
            return CMDCam.proxy.localPlayerEyes(world, partialTicks);
        }

        @Override
        public NBTTagCompound save() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("id", "self");
            return nbt;
        }

        @Override
        public IChatComponent print(World world) {
            return new ChatComponentTranslation("scene.output.self");
        }
    }

    public static class PlayerTarget extends CamTarget {

        public final UUID uuid;

        public PlayerTarget(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public Vec3d position(World world, float partialTicks) {
            EntityPlayer player = findPlayer(world, uuid);
            if (player == null || !player.isEntityAlive())
                return null;
            return EntityUtil.eyePosition(player, partialTicks);
        }

        @Override
        public NBTTagCompound save() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("id", "player");
            nbt.setString("uuid", uuid.toString());
            return nbt;
        }

        @Override
        public IChatComponent print(World world) {
            EntityPlayer player = findPlayer(world, uuid);
            if (player != null)
                return new ChatComponentTranslation("scene.output.player_found", player.getCommandSenderName());
            return new ChatComponentTranslation("scene.output.player", uuid.toString());
        }
    }

}
