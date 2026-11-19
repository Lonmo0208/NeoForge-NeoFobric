package net.neoforged.fabricbridge.shell;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * The FML-side container of a mod the bridge loads through fabric-loader.
 *
 * <p>It carries no mod classes - the mod's code lives in the Fabric module, and FML must not
 * construct it - so everything the base class does for a real mod is either empty or irrelevant here.
 * What the container is for is the event bus: NeoForge-side code that a bridged mod reaches (a shared
 * library's platform implementation) asks FML for that mod's bus and registers on it. The bus handed
 * out here is {@link ReplayRegisterEventBus}, which additionally replays registrations that arrive
 * after the platform's startup registration pass closed - the normal case for a mod whose content is
 * created lazily during gameplay.
 */
public final class ShellModContainer extends ModContainer {
    private final ReplayRegisterEventBus eventBus;

    public ShellModContainer(IModInfo modInfo, ModuleLayer gameLayer) {
        super(modInfo);
        this.eventBus = new ReplayRegisterEventBus(gameLayer);
    }

    @Override
    public IEventBus getEventBus() {
        return eventBus;
    }
}
