package team.creative.cmdcam.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

/**
 * 1.7.10 has no entity selectors, its @p, @a and @r only find players on the server. This adds the modern @e[...] and also works on the client. Supported are type, name, r (radius) and
 * c (count), as well as a plain uuid, a player name or #entityId.
 */
public final class EntityFinder {

    private EntityFinder() {}

    /** @return the best match or null */
    public static Entity find(ICommandSender sender, String selector) {
        World world = sender.getEntityWorld();
        if (world == null)
            return null;
        List<Entity> found = findAll(sender, world, selector);
        return found.isEmpty() ? null : found.get(0);
    }

    private static List<Entity> findAll(ICommandSender sender, World world, String selector) {
        List<Entity> result = new ArrayList<>();

        if (selector.startsWith("@")) {
            int bracket = selector.indexOf('[');
            String kind = bracket == -1 ? selector : selector.substring(0, bracket);
            Map<String, String> arguments = parseArguments(selector, bracket);

            final ChunkCoordinates origin = sender.getPlayerCoordinates();
            String type = arguments.get("type");
            String name = arguments.get("name");
            double radius = parseDouble(arguments.get("r"), -1);
            int count = (int) parseDouble(arguments.get("c"), 1);
            boolean players = kind.equals("@p") || kind.equals("@a") || kind.equals("@r");
            if (!players && !kind.equals("@e"))
                return result;

            for (Object object : world.loadedEntityList) {
                Entity entity = (Entity) object;
                if (players && !(entity instanceof EntityPlayer))
                    continue;
                if (type != null && !type.equalsIgnoreCase(typeName(entity)))
                    continue;
                if (name != null && !name.equalsIgnoreCase(entity.getCommandSenderName()))
                    continue;
                if (radius >= 0 && distanceSq(entity, origin) > radius * radius)
                    continue;
                result.add(entity);
            }

            if (kind.equals("@r"))
                Collections.shuffle(result);
            else
                Collections.sort(result, new Comparator<Entity>() {
                    @Override
                    public int compare(Entity first, Entity second) {
                        return Double.compare(distanceSq(first, origin), distanceSq(second, origin));
                    }
                });

            if (count > 0 && result.size() > count)
                return new ArrayList<>(result.subList(0, count));
            return result;
        }

        if (selector.startsWith("#")) {
            try {
                Entity entity = world.getEntityByID(Integer.parseInt(selector.substring(1)));
                if (entity != null)
                    result.add(entity);
            } catch (NumberFormatException e) {}
            return result;
        }

        UUID uuid = null;
        try {
            uuid = UUID.fromString(selector);
        } catch (IllegalArgumentException e) {}

        for (Object object : world.loadedEntityList) {
            Entity entity = (Entity) object;
            if (uuid != null ? uuid.equals(entity.getUniqueID()) : selector.equalsIgnoreCase(entity.getCommandSenderName())) {
                result.add(entity);
                return result;
            }
        }
        return result;
    }

    private static String typeName(Entity entity) {
        if (entity instanceof EntityPlayer)
            return "Player";
        return EntityList.getEntityString(entity);
    }

    private static double distanceSq(Entity entity, ChunkCoordinates origin) {
        double x = entity.posX - origin.posX;
        double y = entity.posY - origin.posY;
        double z = entity.posZ - origin.posZ;
        return x * x + y * y + z * z;
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Map<String, String> parseArguments(String selector, int bracket) {
        Map<String, String> arguments = new HashMap<>();
        if (bracket == -1 || !selector.endsWith("]"))
            return arguments;
        for (String pair : selector.substring(bracket + 1, selector.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0)
                arguments.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
        }
        return arguments;
    }

}
