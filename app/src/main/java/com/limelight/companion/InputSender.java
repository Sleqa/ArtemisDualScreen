package com.limelight.companion;

import com.limelight.Game;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/** Sends companion input to the host through the active stream, if any. */
public final class InputSender {

    private InputSender() {
    }

    private static NvConnection conn() {
        Game g = Game.instance;
        if (g == null || !g.connected) {
            return null;
        }
        return g.conn;
    }

    public static boolean isReady() {
        return conn() != null;
    }

    public static byte modifierFor(int vk) {
        return KeyboardTranslator.getModifier((short) vk);
    }

    public static void keyDown(int vk, byte modifiers) {
        NvConnection c = conn();
        if (c != null) c.sendKeyboardInput((short) vk, KeyboardPacket.KEY_DOWN, modifiers, (byte) 0);
    }

    public static void keyUp(int vk, byte modifiers) {
        NvConnection c = conn();
        if (c != null) c.sendKeyboardInput((short) vk, KeyboardPacket.KEY_UP, modifiers, (byte) 0);
    }

    /** Presses keys in order (modifiers accumulate) and releases them in reverse. */
    public static void pressAll(int[] keys) {
        byte mods = 0;
        for (int k : keys) {
            keyDown(k, mods);
            mods |= modifierFor(k);
        }
    }

    public static void releaseAll(int[] keys) {
        byte mods = 0;
        for (int k : keys) mods |= modifierFor(k);
        for (int i = keys.length - 1; i >= 0; i--) {
            mods &= (byte) ~modifierFor(keys[i]);
            keyUp(keys[i], mods);
        }
    }

    /** Taps a combo: press everything, release shortly after (same timing as the game menu). */
    public static void tapCombo(int[] keys) {
        Game g = Game.instance;
        if (g == null || conn() == null) return;
        short[] s = new short[keys.length];
        for (int i = 0; i < keys.length; i++) s[i] = (short) keys[i];
        g.sendKeys(s);
    }

    public static void mouseMove(int dx, int dy) {
        NvConnection c = conn();
        if (c != null && (dx != 0 || dy != 0)) c.sendMouseMove((short) dx, (short) dy);
    }

    public static void mousePosition(int x, int y, int refW, int refH) {
        NvConnection c = conn();
        if (c != null) c.sendMousePosition((short) x, (short) y, (short) refW, (short) refH);
    }

    public static void mouseDown(byte button) {
        NvConnection c = conn();
        if (c != null) c.sendMouseButtonDown(button);
    }

    public static void mouseUp(byte button) {
        NvConnection c = conn();
        if (c != null) c.sendMouseButtonUp(button);
    }

    /** Positive scrolls up. 120 = one wheel notch. */
    public static void scroll(int amount) {
        NvConnection c = conn();
        if (c != null && amount != 0) {
            c.sendMouseHighResScroll((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amount)));
        }
    }

    public static void sendText(String text) {
        NvConnection c = conn();
        if (c != null && text != null && !text.isEmpty()) c.sendUtf8Text(text);
    }
}
