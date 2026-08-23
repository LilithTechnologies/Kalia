package re.lilith.kalia.mixins.netty;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.Buffer;

@Mixin(targets = "io.netty.util.internal.PlatformDependent0")
public class MixinPlatformDependent0 {
    @Final
    @Mutable
    @Shadow(remap = false)
    private static Unsafe UNSAFE;

    @Final
    @Mutable
    @Shadow(remap = false)
    private static boolean UNALIGNED;

    @Final
    @Mutable
    @Shadow(remap = false)
    private static long ADDRESS_FIELD_OFFSET;

    /**
     * Fix various lookup problems for newer JDKs.
     */
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void fixUnsafeLookup(CallbackInfo ci) {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);

            if (UNSAFE == null) {
                return;
            }

            Field address = Buffer.class.getDeclaredField("address");
            ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(address);

            UNALIGNED = detectUnalignedAccess();
        } catch (Throwable ignored) {}
    }

    @Unique
    private static boolean detectUnalignedAccess() {
        String arch = System.getProperty("os.arch", "");

        return arch.matches(
                "^(i[3-6]86|x86(_64)?|x64|amd64|aarch64|ppc64(le)?|s390x)$"
        );
    }
}