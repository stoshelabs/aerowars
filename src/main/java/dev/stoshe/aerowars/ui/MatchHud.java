package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Per-player match HUD: phase, big timer, players-alive counter and selected
 * kit. Data is pushed by {@link dev.stoshe.aerowars.manager.HudManager}.
 */
public class MatchHud extends CustomUIHud {
    private boolean visible = true;
    private String phase = "";
    private String timer = "";
    private String alive = "";
    private String kit = "";

    public MatchHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, "aerowars_hud");
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append("HUD/AeroWarsHud.ui");
        pushData(builder);
    }

    public void setData(boolean visible, String phase, String timer, String alive, String kit) {
        this.visible = visible;
        this.phase = phase == null ? "" : phase;
        this.timer = timer == null ? "" : timer;
        this.alive = alive == null ? "" : alive;
        this.kit = kit == null ? "" : kit;
    }

    public void requestUpdate() {
        if (getPlayerRef() == null || !getPlayerRef().isValid()) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        pushData(builder);
        super.update(false, builder);
    }

    private void pushData(@Nonnull UICommandBuilder builder) {
        builder.set("#AeroWarsRoot.Visible", visible);
        builder.set("#AwPhase.Text", phase);
        builder.set("#AwTimer.Text", timer);
        builder.set("#AwAlive.Text", alive);
        builder.set("#AwKit.Text", kit);
    }
}
