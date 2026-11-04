package team.creative.cmdcam.common.packet;

import java.util.ArrayDeque;
import java.util.Queue;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.server.CMDCamServer;

/** All packets of the mod. The server only ever acts on GET and SET, so a client can do nothing else. */
public class CamPacket implements IMessage {

    public enum Action {
        CONNECT,
        GET,
        SET,
        START,
        STOP,
        PAUSE,
        RESUME,
        TELEPORT
    }

    private static final Action[] ACTIONS = Action.values();

    /** Runs server packets on the server thread, since packet handlers are called on a network thread in 1.7.10. */
    private static final Queue<Runnable> SERVER_TASKS = new ArrayDeque<>();

    public static void runServerTasks() {
        while (true) {
            Runnable task;
            synchronized (SERVER_TASKS) {
                task = SERVER_TASKS.poll();
            }
            if (task == null)
                return;
            task.run();
        }
    }

    public Action action;
    public String id;
    public NBTTagCompound nbt;

    public CamPacket() {}

    public CamPacket(Action action) {
        this(action, "", null);
    }

    public CamPacket(Action action, String id, NBTTagCompound nbt) {
        this.action = action;
        this.id = id;
        this.nbt = nbt;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        action = ordinal >= 0 && ordinal < ACTIONS.length ? ACTIONS[ordinal] : null;
        id = ByteBufUtils.readUTF8String(buf);
        nbt = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        ByteBufUtils.writeUTF8String(buf, id);
        ByteBufUtils.writeTag(buf, nbt);
    }

    public static class Handler implements IMessageHandler<CamPacket, IMessage> {

        @Override
        public IMessage onMessage(final CamPacket packet, MessageContext context) {
            if (packet.action == null)
                return null;
            if (context.side == Side.CLIENT)
                CMDCam.proxy.handleClientPacket(packet);
            else if (packet.action == Action.GET || packet.action == Action.SET) {
                final EntityPlayerMP player = context.getServerHandler().playerEntity;
                synchronized (SERVER_TASKS) {
                    SERVER_TASKS.add(new Runnable() {
                        @Override
                        public void run() {
                            CMDCamServer.handlePacket(packet, player);
                        }
                    });
                }
            }
            return null;
        }
    }

}
