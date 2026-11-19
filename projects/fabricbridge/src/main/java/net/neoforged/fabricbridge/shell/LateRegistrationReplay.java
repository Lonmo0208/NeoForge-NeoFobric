package net.neoforged.fabricbridge.shell;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands a listener added to a shell bus to the platform's late-registration replay.
 *
 * <p>The replay lives in the platform ({@code net.neoforged.neoforge.registries.BridgeLateRegistration})
 * because only it knows how to unfreeze a registry for the call; the bridge runs on the bootstrap
 * classpath and cannot see the game layer at compile time. The game layer's {@link ModuleLayer} - the
 * one FML passes to every language loader - is used to resolve the class at the first late listener
 * and the method is cached.
 *
 * <p>Nothing here fails loudly: a platform without the helper (an older NeoForge, a bridge build used
 * on its own) simply leaves the listener as it is.
 */
final class LateRegistrationReplay {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric");
    private static final String HELPER_CLASS = "net.neoforged.neoforge.registries.BridgeLateRegistration";

    private final ModuleLayer gameLayer;
    private Method replayMethod;
    private boolean resolved;

    LateRegistrationReplay(ModuleLayer gameLayer) {
        this.gameLayer = gameLayer;
    }

    void afterListenerAdded(Consumer<?> listener) {
        var method = resolve();
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, listener);
        } catch (Throwable t) {
            LOGGER.debug("[NeoFobric] Late registration replay failed", t);
        }
    }

    private synchronized Method resolve() {
        if (resolved) {
            return replayMethod;
        }
        resolved = true;
        if (gameLayer == null) {
            return null;
        }
        // The platform module is the named "neoforge" module of the game layer, but the layer also
        // carries the mods; ask every module's loader instead of trusting one module name.
        for (var module : gameLayer.modules()) {
            var loader = module.getClassLoader();
            if (loader == null) {
                continue;
            }
            try {
                var helper = Class.forName(HELPER_CLASS, false, loader);
                replayMethod = helper.getMethod("replayTo", Consumer.class);
                LOGGER.info("[NeoFobric] Late registration replay resolved through module {}", module.getName());
                return replayMethod;
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                // Not this module; try the next one.
            }
        }
        LOGGER.debug("[NeoFobric] The platform has no late registration replay; listeners added after the "
                + "registration window stay silent");
        return null;
    }
}
