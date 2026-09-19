package ez.minar.system.features.misc;

import ez.minar.system.api.Category;
import ez.minar.system.api.Function;
import ez.minar.system.api.NewFunction;
import ez.minar.system.events.EventHandler;
import ez.minar.system.events.impl.UpdateEvent;
import ez.minar.system.settings.impl.BooleanSetting;
import ez.minar.system.settings.impl.TextSetting;
import net.minecraft.client.gui.screen.DeathScreen;

@NewFunction(name = "AutoRespawn", desc = "Автоматически возрождается после смерти", category = Category.MISC)
public class AutoRespawn extends Function {

    private final BooleanSetting useCommand = new BooleanSetting("Использовать команду", false);
    private final TextSetting command = new TextSetting("Команда", "home");

    private boolean respawned = false;

    public AutoRespawn() {
        addSettings(useCommand, command);
    }

    @Override
    public void onEnable() {
        respawned = false;
        super.onEnable();
    }

    @EventHandler
    public void onTick(UpdateEvent event) {
        if (mc.player == null) return;

        if (mc.currentScreen instanceof DeathScreen) {
            if (!respawned) {
                mc.player.requestRespawn();
                mc.setScreen(null);
                
                if (useCommand.isEnabled()) {
                    String cmd = command.getValue();
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    if (mc.player.networkHandler != null && !cmd.isEmpty()) {
                        mc.player.networkHandler.sendChatCommand(cmd);
                    }
                }
                respawned = true;
            }
        } else {
            respawned = false;
        }
    }
}
