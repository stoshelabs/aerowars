package dev.stoshe.aerowars.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.stoshe.aerowars.util.ChatUtil;

import javax.annotation.Nonnull;

/**
 * Per-player side scoreboard: a right-anchored panel listing the live match data.
 * Data is pushed by {@link dev.stoshe.aerowars.manager.HudManager}.
 */
public class ScoreboardHud extends CustomUIHud {
    private boolean visible = true;
    private String arena = "";
    private String mode = "";
    private String time = "";
    private String timeLabel = "";
    private String alive = "";
    private String kills = "";
    private String kit = "";
    private String eventName = "";
    private String eventTime = "";
    private String footer = "";

    public ScoreboardHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, "aerowars_scoreboard");
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append("HUD/AeroWarsScoreboard.ui");
        pushData(builder);
    }

    public void setData(boolean visible, String arena, String mode, String time, String timeLabel,
            String alive, String kills, String kit, String eventName, String eventTime, String footer) {
        this.visible = visible;
        this.arena = nz(arena);
        this.mode = nz(mode);
        this.time = nz(time);
        this.timeLabel = nz(timeLabel);
        this.alive = nz(alive);
        this.kills = nz(kills);
        this.kit = nz(kit);
        this.eventName = nz(eventName);
        this.eventTime = nz(eventTime);
        this.footer = nz(footer);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public void requestUpdate() {
        if (getPlayerRef() == null || !getPlayerRef().isValid()) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        pushData(builder);
        super.update(false, builder);
    }

    /** Pushes {@code Visible: false} so the panel disappears even if the detach reflection fails. */
    public void hide() {
        this.visible = false;
        if (getPlayerRef() == null || !getPlayerRef().isValid()) {
            return;
        }
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#AwSbRoot.Visible", false);
        super.update(false, builder);
    }

    private void pushData(@Nonnull UICommandBuilder builder) {
        builder.set("#AwSbRoot.Visible", visible);
        builder.set("#AwSbArena.Text", arena);
        builder.set("#AwSbMode.Text", mode);
        builder.set("#AwSbTime.Text", time);
        builder.set("#AwSbTimeLabel.Text", timeLabel);
        builder.set("#AwSbAlive.Text", alive);
        builder.set("#AwSbKills.Text", kills);
        builder.set("#AwSbKit.Text", kit);
        boolean hasEvent = !eventTime.isEmpty();
        builder.set("#AwSbEventSection.Visible", hasEvent);
        builder.set("#AwSbEventName.Text", eventName);
        builder.set("#AwSbEventTime.Text", eventTime);
        // Footer is configurable (server link/brand). MUST be a plain String — setting a Label's
        // .Text with a Message crashes the client ("No parameterless constructor for System.String").
        // Colour comes from the .ui Style; strip any {#hex}/&-codes the config may contain.
        boolean hasFooter = !footer.isBlank();
        builder.set("#AwSbFooter.Visible", hasFooter);
        if (hasFooter) {
            builder.set("#AwSbFooter.Text", ChatUtil.stripColor(footer));
        }
    }
}
