package dev.luacomp.terminal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

/** Buffer de texto com 16 cores, como o terminal do ComputerCraft. Thread-safe e redimensionável. */
public class Terminal {
    /** Tamanho padrão do computador. */
    public static final int WIDTH = 51;
    public static final int HEIGHT = 19;

    /** Paleta padrão do CC (índice 0 = white ... 15 = black). */
    public static final int[] PALETTE = {
            0xFFF0F0F0, 0xFFF2B233, 0xFFE57FD8, 0xFF99B2F2,
            0xFFDEDE6C, 0xFF7FCC19, 0xFFF2B2CC, 0xFF4C4C4C,
            0xFF999999, 0xFF4C99B2, 0xFFB266E5, 0xFF3366CC,
            0xFF7F664C, 0xFF57A64E, 0xFFCC4C4C, 0xFF111111
    };

    private final int[] palette = PALETTE.clone();
    private int width, height;
    private char[] text;
    private byte[] colours; // (fg << 4) | bg
    private int cx, cy;
    private boolean blink, powered;
    private int fg = 0, bg = 15;
    private int version;

    public Terminal() {
        this(WIDTH, HEIGHT);
    }

    public Terminal(int w, int h) {
        allocate(w, h);
        fillAll();
    }

    private void allocate(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        text = new char[width * height];
        colours = new byte[width * height];
    }

    private byte pack() {
        return (byte) ((fg << 4) | bg);
    }

    private void fillAll() {
        Arrays.fill(text, ' ');
        Arrays.fill(colours, pack());
    }

    private void fillRow(int row) {
        Arrays.fill(text, row * width, (row + 1) * width, ' ');
        Arrays.fill(colours, row * width, (row + 1) * width, pack());
    }

    public synchronized int version() { return version; }
    public synchronized int width() { return width; }
    public synchronized int height() { return height; }

    public synchronized void resize(int w, int h) {
        if (w == width && h == height) return;
        allocate(w, h);
        cx = 0; cy = 0;
        fillAll();
        version++;
    }

    // ---------- escrita ----------

    public synchronized void write(String s) {
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                int i = cy * width + cx;
                text[i] = c < 32 ? '?' : c;
                colours[i] = pack();
            }
            cx++;
        }
        version++;
    }

    public synchronized void blit(String t, String f, String b) {
        for (int k = 0; k < t.length(); k++) {
            if (cy >= 0 && cy < height && cx >= 0 && cx < width) {
                int i = cy * width + cx;
                char c = t.charAt(k);
                int fi = Math.max(0, Character.digit(f.charAt(k), 16));
                int bi = Math.max(0, Character.digit(b.charAt(k), 16));
                text[i] = c < 32 ? '?' : c;
                colours[i] = (byte) ((fi << 4) | bi);
            }
            cx++;
        }
        version++;
    }

    /** Escreve texto e vai para a linha seguinte (mensagens do sistema). */
    public synchronized void println(String s) {
        cx = 0;
        write(s);
        cx = 0;
        cy++;
        if (cy >= height) {
            scroll(1);
            cy = height - 1;
        }
    }

    public synchronized void clear() { fillAll(); version++; }

    public synchronized void clearLine() {
        if (cy >= 0 && cy < height) fillRow(cy);
        version++;
    }

    public synchronized void scroll(int n) {
        if (n == 0) return;
        int abs = Math.abs(n);
        if (abs >= height) {
            fillAll();
        } else if (n > 0) {
            System.arraycopy(text, abs * width, text, 0, (height - abs) * width);
            System.arraycopy(colours, abs * width, colours, 0, (height - abs) * width);
            for (int r = height - abs; r < height; r++) fillRow(r);
        } else {
            System.arraycopy(text, 0, text, abs * width, (height - abs) * width);
            System.arraycopy(colours, 0, colours, abs * width, (height - abs) * width);
            for (int r = 0; r < abs; r++) fillRow(r);
        }
        version++;
    }

    public synchronized void reset() {
        fg = 0; bg = 15; cx = 0; cy = 0; blink = false;
        System.arraycopy(PALETTE, 0, palette, 0, 16);
        fillAll();
        version++;
    }

    // ---------- estado ----------

    public synchronized void setCursor(int x, int y) { cx = x; cy = y; version++; }
    public synchronized int cursorX() { return cx; }
    public synchronized int cursorY() { return cy; }
    public synchronized void setBlink(boolean b) { blink = b; version++; }
    public synchronized boolean blink() { return blink; }
    public synchronized void setFg(int i) { fg = i; }
    public synchronized void setBg(int i) { bg = i; }
    public synchronized int fg() { return fg; }
    public synchronized int bg() { return bg; }
    public synchronized void setPalette(int index, int rgb) {
        palette[index] = 0xFF000000 | (rgb & 0xFFFFFF);
        version++;
    }

    public synchronized int palette(int index) { return palette[index]; }

    public synchronized void snapshotPalette(int[] out) {
        System.arraycopy(palette, 0, out, 0, 16);
    }

    public synchronized void setPowered(boolean p) { powered = p; version++; }
    public synchronized boolean powered() { return powered; }

    /** Copia o conteúdo para arrays do chamador (render do cliente). Os arrays devem ter width*height. */
    public synchronized void snapshot(char[] t, byte[] c) {
        System.arraycopy(text, 0, t, 0, Math.min(text.length, t.length));
        System.arraycopy(colours, 0, c, 0, Math.min(colours.length, c.length));
    }

    public synchronized void copyFrom(Terminal o) {
        synchronized (o) {
            if (o.width != width || o.height != height) allocate(o.width, o.height);
            System.arraycopy(o.text, 0, text, 0, text.length);
            System.arraycopy(o.colours, 0, colours, 0, colours.length);
            cx = o.cx; cy = o.cy; blink = o.blink; powered = o.powered;
            fg = o.fg; bg = o.bg;
            System.arraycopy(o.palette, 0, palette, 0, 16);
            version++;
        }
    }

    // ---------- rede ----------

    public synchronized void write(FriendlyByteBuf buf) {
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeBoolean(powered);
        buf.writeBoolean(blink);
        buf.writeInt(cx);
        buf.writeInt(cy);
        for (int c : palette) buf.writeInt(c);
        for (int i = 0; i < text.length; i++) {
            buf.writeChar(text[i]);
            buf.writeByte(colours[i]);
        }
    }

    public synchronized void read(FriendlyByteBuf buf) {
        int w = buf.readVarInt(), h = buf.readVarInt();
        if (w < 1 || h < 1 || w > 256 || h > 128) throw new IllegalArgumentException("Bad terminal size");
        if (w != width || h != height) allocate(w, h);
        powered = buf.readBoolean();
        blink = buf.readBoolean();
        cx = buf.readInt();
        cy = buf.readInt();
        for (int i = 0; i < 16; i++) palette[i] = buf.readInt();
        for (int i = 0; i < text.length; i++) {
            text[i] = buf.readChar();
            colours[i] = buf.readByte();
        }
        version++;
    }

    // ---------- NBT (monitores) ----------

    public synchronized CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("W", width);
        t.putInt("H", height);
        t.putString("T", new String(text));
        t.putByteArray("C", colours.clone());
        t.putInt("X", cx);
        t.putInt("Y", cy);
        t.putBoolean("B", blink);
        t.putIntArray("P", palette.clone());
        return t;
    }

    public synchronized void fromTag(CompoundTag t) {
        int w = t.getInt("W"), h = t.getInt("H");
        if (w < 1 || h < 1 || w > 256 || h > 128) return;
        if (w != width || h != height) allocate(w, h);
        String s = t.getString("T");
        for (int i = 0; i < text.length && i < s.length(); i++) text[i] = s.charAt(i);
        byte[] c = t.getByteArray("C");
        System.arraycopy(c, 0, colours, 0, Math.min(c.length, colours.length));
        cx = t.getInt("X");
        cy = t.getInt("Y");
        blink = t.getBoolean("B");
        int[] p = t.getIntArray("P");
        if (p.length == 16) System.arraycopy(p, 0, palette, 0, 16);
        version++;
    }
}
