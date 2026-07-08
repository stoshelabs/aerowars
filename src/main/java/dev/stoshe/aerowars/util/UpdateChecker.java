package dev.stoshe.aerowars.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;

/** Checks for a newer AeroWars release on GitHub (same scheme as the Plots plugin). */
public final class UpdateChecker {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/stoshelabs/aerowars/releases/latest";
    /** Human-facing download page shown to admins / in the panel. */
    public static final String RELEASES_URL = "https://github.com/stoshelabs/aerowars/releases";
    private static final int TIMEOUT_MS = 5000;

    private UpdateChecker() {
    }

    /** Fetches the latest release tag asynchronously; resolves to null when the check fails. */
    public static CompletableFuture<String> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var url = new java.net.URI(GITHUB_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (connection.getResponseCode() != 200) {
                    return null;
                }
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                String latest = json.get("tag_name").getAsString();
                return latest.startsWith("v") ? latest.substring(1) : latest;
            } catch (Exception e) {
                // Offline / private repo / rate-limited — silently give up (no update info).
                return null;
            }
        });
    }

    /** True if {@code newVersion} is strictly greater than {@code currentVersion} (dotted numeric compare). */
    public static boolean isNewerVersion(@Nullable String currentVersion, @Nullable String newVersion) {
        if (currentVersion == null || newVersion == null) {
            return false;
        }
        try {
            String[] cur = currentVersion.split("\\.");
            String[] neu = newVersion.split("\\.");
            int length = Math.max(cur.length, neu.length);
            for (int i = 0; i < length; i++) {
                int c = i < cur.length ? Integer.parseInt(cur[i].replaceAll("\\D", "")) : 0;
                int n = i < neu.length ? Integer.parseInt(neu[i].replaceAll("\\D", "")) : 0;
                if (n > c) {
                    return true;
                }
                if (n < c) {
                    return false;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
