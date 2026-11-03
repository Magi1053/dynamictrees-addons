package com.skcraft.dtsable.compat.bluemap3d;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class BlueMap3DInjectionTargetsTest {
    private ClassNode read(String name) throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("dtsable.bluemap3dJar")).toFile());
             var stream = jar.getInputStream(jar.getEntry(name + ".class"))) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    @Test void mesherHasExactlyOneVolumeArgumentEntryPoint() throws Exception {
        var type = read("dev/duzo/bluemap3d/bake/VolumeMesher");
        assertEquals(1, type.methods.stream().filter(m -> m.name.equals("mesh")
                && m.desc.equals("(Ldev/duzo/bluemap3d/api/BlockVolume;)Ldev/duzo/bluemap3d/bake/BakedMesh;")).count());
    }

    @Test void reviewedRefreshLambdaContainsExactlyOneRegionSchedulingCall() throws Exception {
        var type = read("dev/duzo/bluemap3d/publish/TileRefreshQueue");
        var method = type.methods.stream().filter(m -> m.name.equals("lambda$tick$3")).findFirst().orElseThrow();
        int targets = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("de/bluecolored/bluemap/api/RenderManager")
                    && call.name.equals("scheduleMapUpdateTask")
                    && call.desc.equals("(Lde/bluecolored/bluemap/api/BlueMapMap;Ljava/util/Collection;Z)Z")) targets++;
        }
        assertEquals(1, targets);
    }
}
