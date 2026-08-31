package net.fayber.faybergui;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fayber.faybergui.dev.WidgetTestScreen;
import net.minecraft.client.Minecraft;

/**
 * {@code /faybergui showcase}: opens the full widget catalog screen in-game, no startup flag
 * needed. This is the same screen the {@code -Dfaybergui.preview=true} dev hook auto-opens on
 * the title screen ({@link WidgetTestScreen}), just on demand.
 */
public final class ShowcaseCommand {
    private ShowcaseCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("faybergui")
                .then(ClientCommands.literal("showcase").executes(context -> {
                    Minecraft client = context.getSource().getClient();
                    // Route through the client thread: setScreen is only safe there.
                    client.execute(() -> client.setScreen(new WidgetTestScreen()));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
