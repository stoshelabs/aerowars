package dev.stoshe.aerowars.util;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * Plugin console output. Normal messages go through the server's native {@link Logger} (named "AeroWars"),
 * which already colors them by level (info/warning/severe) — no manual ANSI needed. Only the one-off boot
 * banner is written raw (bypassing the logger) so its rainbow art isn't broken up by the log prefix.
 */
public final class Console {
    private static final Logger LOGGER = Logger.getLogger("AeroWars");
    /** Direct handle to the real terminal (fd 1), used only for the rainbow banner. */
    private static final PrintStream OUT = new PrintStream(new FileOutputStream(FileDescriptor.out), true);
    private static final String ESC = ((char) 27) + "[";
    private static final String RESET = ESC + "0m";
    /** 256-color rainbow ramp (red→orange→yellow→green→cyan→blue→purple) for the banner. */
    private static final int[] RAINBOW = {196, 202, 208, 214, 220, 226, 190, 118, 46, 48, 51, 45, 39, 33, 63, 99, 135, 171, 201};

    private Console() {
    }

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void success(String msg) {
        LOGGER.info(msg);
    }

    public static void warning(String msg) {
        LOGGER.warning(msg);
    }

    public static void error(String msg) {
        LOGGER.severe(msg);
    }

    /** Raw line straight to the terminal (no log prefix) — banner spacing. */
    public static void log(String msg) {
        OUT.println(msg);
    }

    /** Prints a line with each non-space glyph stepped through the rainbow ramp — for the boot banner. */
    public static void rainbow(String msg) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (int c = 0; c < msg.length(); c++) {
            char ch = msg.charAt(c);
            if (ch == ' ') {
                sb.append(' ');
                continue;
            }
            sb.append(ESC).append("38;5;").append(RAINBOW[i % RAINBOW.length]).append('m').append(ch);
            i++;
        }
        sb.append(RESET);
        OUT.println(sb);
    }
}
