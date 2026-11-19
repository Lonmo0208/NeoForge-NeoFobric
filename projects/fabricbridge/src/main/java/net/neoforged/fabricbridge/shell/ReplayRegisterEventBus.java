package net.neoforged.fabricbridge.shell;

import java.util.function.Consumer;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.IModBusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The event bus of a {@link ShellModContainer}: a real mod bus plus a late-registration replay.
 *
 * <p>Event buses are built for the startup shape: a listener added while a mod is being constructed
 * hears every event posted to that bus afterwards. A platform library's registry manager instead adds
 * its listener when the mod first asks for its entries - during gameplay, long after the platform's
 * registration pass - and on a native mod that simply never happens because the same code ran at
 * construction time. A bridged mod can start its registration anywhere, so the listener is handed to
 * {@link LateRegistrationReplay} right after it was added: the platform then replays the registration
 * event for it, exactly once, unfreezing the target registry for the call.
 *
 * <p>The bus itself stays the authoritative one - listeners registered here receive every event the
 * platform posts to the shell, which is how the bridge's own re-posted pass serves the Architectury
 * style listeners that arrive during the Fabric entrypoints.
 */
public final class ReplayRegisterEventBus implements IEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger("NeoFobric");

    private final IEventBus delegate;
    private final LateRegistrationReplay replay;

    public ReplayRegisterEventBus(ModuleLayer gameLayer) {
        this.delegate = BusBuilder.builder()
                .markerType(IModBusEvent.class)
                .allowPerPhasePost()
                .build();
        this.replay = new LateRegistrationReplay(gameLayer);
    }

    @Override
    public void register(Object target) {
        delegate.register(target);
    }

    @Override
    public <T extends Event> void addListener(Consumer<T> listener) {
        delegate.addListener(listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(Class<T> eventType, Consumer<T> listener) {
        delegate.addListener(eventType, listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, Consumer<T> listener) {
        delegate.addListener(priority, listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> listener) {
        delegate.addListener(priority, eventType, listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Consumer<T> listener) {
        delegate.addListener(priority, receiveCancelled, listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType,
            Consumer<T> listener) {
        delegate.addListener(priority, receiveCancelled, eventType, listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(boolean receiveCancelled, Consumer<T> listener) {
        delegate.addListener(receiveCancelled, listener);
        replayAfterAdd(listener);
    }

    @Override
    public <T extends Event> void addListener(boolean receiveCancelled, Class<T> eventType, Consumer<T> listener) {
        delegate.addListener(receiveCancelled, eventType, listener);
        replayAfterAdd(listener);
    }

    @Override
    public void unregister(Object target) {
        delegate.unregister(target);
    }

    @Override
    public <T extends Event> T post(T event) {
        return delegate.post(event);
    }

    @Override
    public <T extends Event> T post(EventPriority phase, T event) {
        return delegate.post(phase, event);
    }

    @Override
    public void start() {
        delegate.start();
    }

    private void replayAfterAdd(Consumer<?> listener) {
        try {
            replay.afterListenerAdded(listener);
        } catch (Throwable t) {
            // A replay that cannot run must never break the mod's own registration call.
            LOGGER.debug("[NeoFobric] Could not replay late registrations for a listener", t);
        }
    }
}
