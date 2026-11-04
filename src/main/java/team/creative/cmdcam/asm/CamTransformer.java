package team.creative.cmdcam.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Patches EntityRenderer, which has no Forge events for what the camera needs:
 * <ul>
 * <li>getFOVModifier: zoom without touching the FOV in the options, which used to reset or save the wrong FOV
 * <li>getMouseOver: keeps aiming at what the player looks at while another entity is the camera in outside mode
 * </ul>
 */
public class CamTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOGGER = LogManager.getLogger("cmdcam");

    private static final String TARGET = "net.minecraft.client.renderer.EntityRenderer";
    private static final String HOOKS = "team/creative/cmdcam/client/CamHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName))
            return bytes;

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        boolean fov = false;
        boolean mouseOver = false;
        for (MethodNode method : node.methods) {
            if ((method.name.equals("getFOVModifier") || method.name.equals("func_78481_a")) && method.desc.equals("(FZ)F")) {
                patchFov(method);
                fov = true;
            } else if ((method.name.equals("getMouseOver") || method.name.equals("func_78473_a")) && method.desc.equals("(F)V")) {
                patchMouseOver(method);
                mouseOver = true;
            }
        }

        if (!fov || !mouseOver) // Something else changed the class. The game still runs, just without the feature
            LOGGER.error("Could not patch EntityRenderer completely (fov: {}, mouse over: {})", fov, mouseOver);
        else
            LOGGER.info("Patched EntityRenderer");

        // No branches are added, so the existing stack map frames stay valid
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void patchFov(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == FRETURN) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(ILOAD, 2));
                hook.add(new MethodInsnNode(INVOKESTATIC, HOOKS, "modifyFov", "(FZ)F", false));
                method.instructions.insertBefore(instruction, hook);
            }
        }
    }

    private static void patchMouseOver(MethodNode method) {
        method.instructions.insert(new MethodInsnNode(INVOKESTATIC, HOOKS, "mouseOverBefore", "()V", false));
        for (AbstractInsnNode instruction : method.instructions.toArray())
            if (instruction.getOpcode() == RETURN)
                method.instructions.insertBefore(instruction, new MethodInsnNode(INVOKESTATIC, HOOKS, "mouseOverAfter", "()V", false));
    }

}
