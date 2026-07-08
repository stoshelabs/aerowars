package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

/**
 * Centred top status banner: the headline match state (countdown, time remaining,
 * winner) with a secondary line. Driven by {@link dev.stoshe.aerowars.manager.HudManager}.
 */
public class StatusHud extends CustomUIHud {
    private boolean visible = true;
    private String main = "";
    private String sub = "";

    public StatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, "aerowars_status");
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append("HUD/AeroWarsStatus.ui");
        pushData(builder);
    }

    public void setData(boolean visible, String main, String sub) {
        this.visible = visible;
        this.main = main == null ? "" : main;
        this.sub = sub == null ? "" : sub;
    }

    public void requestUpdate() {
        if (getPlayerRef() == null || !getPlayerRef().isValid()) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        pushData(builder);
        super.update(false, builder);
    }

    /** Pushes {@code Visible: false} so the banner disappears even if the detach reflection fails. */
    public void hide() {
        this.visible = false;
        if (getPlayerRef() == null || !getPlayerRef().isValid()) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#AwStatusRoot.Visible", false);
        super.update(false, builder);
    }

    private void pushData(@Nonnull UICommandBuilder builder) {
        builder.set("#AwStatusRoot.Visible", visible);
        builder.set("#AwStatusMain.Text", main);
        builder.set("#AwStatusSub.Text", sub);
    }
}
