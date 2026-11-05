package team.creative.cmdcam.common.packet;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.server.CMDCamServer;

/** All packets of the mod. New actions go to the end of the list, versions tell them apart by number. */
public class CamPacket implements IMessage {

    public enum Action {
        CONNECT,
        GET,
        SET,
        START,
        STOP,
        PAUSE,
        RESUME,
        TELEPORT,
        /** Client asks for all saved paths. */
        LIST,
        LIST_ENTRY,
        /** Nothing follows, tells the client if it can change paths on the server. */
        LIST_END,
        RENAME,
        DELETE,
        /** The client loaded this path from a list it already has. */
        LOADED,
        /** The answer to a change. The id is the text to show. */
        RESULT,
        /** The server does not have the requested path. */
        LOAD_FAIL;

        /** @return true if a client may ask the server for this, everything else is ignored */
        public boolean fromClient() {
            return this == GET || this == SET || this == LIST || this == RENAME || this == DELETE || this == LOADED;
        }
    }

    private static final Action[] ACTIONS = Action.values();
    private static final int STRING_TAG = 8;
    private static final int NO_NBT = -1;
    private static final int LARGE_NBT = -2;
    /** Much more than the largest scene allowed takes in memory, by how the size of NBT is estimated. */
    private static final long MAX_NBT_SIZE = 16 * 1024 * 1024;

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

    public static NBTTagCompound resultData(boolean success, String... args) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setBoolean("success", success);
        NBTTagList list = new NBTTagList();
        for (String arg : args)
            list.appendTag(new NBTTagString(arg));
        nbt.setTag("args", list);
        return nbt;
    }

    public static Object[] resultArgs(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("args", STRING_TAG);
        Object[] args = new Object[list.tagCount()];
        for (int i = 0; i < args.length; i++)
            args[i] = list.getStringTagAt(i);
        return args;
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
        nbt = readNBT(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action.ordinal());
        ByteBufUtils.writeUTF8String(buf, id);
        writeNBT(buf, nbt);
    }

    /**
     * Minecraft's own format stores the compressed size in a short, a large scene would break the packet. This one reads the same up to that size, so nothing changes for other
     * versions of the mod. Larger data is marked with a size they take for no data at all.
     */
    private static void writeNBT(ByteBuf buf, NBTTagCompound nbt) {
        if (nbt == null) {
            buf.writeShort(NO_NBT);
            return;
        }
        try {
            byte[] data = CompressedStreamTools.compress(nbt);
            if (data.length <= Short.MAX_VALUE)
                buf.writeShort(data.length);
            else {
                buf.writeShort(LARGE_NBT);
                buf.writeInt(data.length);
            }
            buf.writeBytes(data);
        } catch (IOException e) {
            throw new EncoderException(e);
        }
    }

    private static NBTTagCompound readNBT(ByteBuf buf) {
        int size = buf.readShort();
        if (size == LARGE_NBT)
            size = buf.readInt();
        else if (size < 0)
            return null;
        if (size < 0 || size > buf.readableBytes())
            throw new DecoderException("Invalid size of NBT data: " + size);
        byte[] data = new byte[size];
        buf.readBytes(data);
        try {
            return CompressedStreamTools.func_152457_a(data, new NBTSizeTracker(MAX_NBT_SIZE));
        } catch (IOException e) {
            throw new DecoderException(e);
        }
    }

    public static class Handler implements IMessageHandler<CamPacket, IMessage> {

        @Override
        public IMessage onMessage(final CamPacket packet, MessageContext context) {
            if (packet.action == null)
                return null;
            if (context.side == Side.CLIENT)
                CMDCam.proxy.handleClientPacket(packet);
            else if (packet.action.fromClient()) {
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
