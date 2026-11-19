package net.neoforged.fabricbridge.shell;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

/**
 * The language loader of the bridge's shell mods.
 *
 * <p>A shell is a one-file jar ({@code META-INF/neoforge.mods.toml}) the bridge writes for a mod it
 * loads through fabric-loader, so FML's mod list can answer lookups by that mod's id. NeoForge-side
 * code reaches for a mod's {@code ModContainer} whenever it wants its event bus - Architectury's
 * {@code EventBusesHooks}, puzzleslib's {@code NeoForgeModContainerHelper} - and a bridged mod has no
 * FML entry otherwise: those lookups throw {@code NoSuchElementException}/{@code NullPointerException}
 * and the calling registration never happens.
 *
 * <p>The stock {@code javafml} loader would give the shell FML's own container, whose bus is a plain
 * {@link net.neoforged.bus.api.IEventBus}: a listener added after the platform's registration window
 * closed would simply never fire. {@link ShellModContainer} hands out a bus that replays such
 * listeners instead, which is what a mod that registers lazily out of gameplay needs.
 */
public final class BridgeShellLanguageLoader implements IModLanguageLoader {
    /** The {@code modLoader} value of the shell jars the bridge writes. */
    public static final String NAME = "neofobric_shell";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public ModContainer loadMod(IModInfo modInfo, ModFileScanData scanData, ModuleLayer gameLayer) {
        return new ShellModContainer(modInfo, gameLayer);
    }
}
