package dev.luacomp.client;

import dev.luacomp.menu.ComputerMenu;
import dev.luacomp.network.ComputerInputPacket;
import dev.luacomp.network.ModNetwork;
import dev.luacomp.terminal.Terminal;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/** Tela do computador: desenha o terminal e envia teclado para o servidor. */
public class ComputerScreen extends AbstractContainerScreen<ComputerMenu> {
    private static final int CW = 6;   // largura da célula
    private static final int CH = 9;   // altura da célula
    private static final int PAD = 8;

    private final char[] chars = new char[Terminal.WIDTH * Terminal.HEIGHT];
    private final byte[] cols = new byte[Terminal.WIDTH * Terminal.HEIGHT];
    private final int[] pal = new int[16];
    private final Set<Integer> down = new HashSet<>();

    public ComputerScreen(ComputerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = Terminal.WIDTH * CW + PAD * 2;
        this.imageHeight = Terminal.HEIGHT * CH + PAD * 2;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // desenhado em render()
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // moldura
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF9A9A9A);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF2B2B2B);

        Terminal t = menu.getTerminal();
        t.snapshot(chars, cols);
        t.snapshotPalette(pal);
        int x0 = leftPos + PAD, y0 = topPos + PAD;

        // fundos (agrupa células vizinhas com a mesma cor)
        for (int y = 0; y < Terminal.HEIGHT; y++) {
            int x = 0;
            while (x < Terminal.WIDTH) {
                int bg = cols[y * Terminal.WIDTH + x] & 0x0F;
                int start = x;
                while (x < Terminal.WIDTH && (cols[y * Terminal.WIDTH + x] & 0x0F) == bg) x++;
                g.fill(x0 + start * CW, y0 + y * CH, x0 + x * CW, y0 + (y + 1) * CH, pal[bg]);
            }
        }

        // texto
        for (int y = 0; y < Terminal.HEIGHT; y++) {
            for (int x = 0; x < Terminal.WIDTH; x++) {
                char c = chars[y * Terminal.WIDTH + x];
                if (c == ' ') continue;
                int fg = (cols[y * Terminal.WIDTH + x] >> 4) & 0x0F;
                String s = String.valueOf(c);
                int px = x0 + x * CW + Math.max(0, (CW - font.width(s)) / 2);
                g.drawString(font, s, px, y0 + y * CH + 1, pal[fg], false);
            }
        }

        // cursor piscando
        if (t.powered() && t.blink() && (Util.getMillis() / 400) % 2 == 0) {
            int cx = t.cursorX(), cy = t.cursorY();
            if (cx >= 0 && cx < Terminal.WIDTH && cy >= 0 && cy < Terminal.HEIGHT) {
                int fg = (cols[cy * Terminal.WIDTH + cx] >> 4) & 0x0F;
                g.drawString(font, "_", x0 + cx * CW, y0 + cy * CH + 1, pal[fg], false);
            }
        }

        // aviso de desligado
        if (!t.powered()) {
            g.fill(x0, y0 + (Terminal.HEIGHT / 2 - 1) * CH, x0 + Terminal.WIDTH * CW, y0 + (Terminal.HEIGHT / 2 + 2) * CH, 0xCC000000);
            g.drawCenteredString(font, "Desligado", leftPos + imageWidth / 2, y0 + (Terminal.HEIGHT / 2 - 1) * CH + 4, 0xFFFFFF);
            g.drawCenteredString(font, "Ctrl+R para ligar", leftPos + imageWidth / 2, y0 + (Terminal.HEIGHT / 2) * CH + 6, 0xAAAAAA);
        }
    }

    // ---------- entrada ----------

    private void send(ComputerInputPacket p) {
        ModNetwork.CHANNEL.sendToServer(p);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        int id = menu.containerId;

        if (ctrl && key == GLFW.GLFW_KEY_T) { send(ComputerInputPacket.command(id, ComputerInputPacket.Type.TERMINATE)); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_R) { send(ComputerInputPacket.command(id, ComputerInputPacket.Type.REBOOT)); return true; }
        if (ctrl && shift && key == GLFW.GLFW_KEY_S) { send(ComputerInputPacket.command(id, ComputerInputPacket.Type.SHUTDOWN)); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null && !clip.isEmpty()) send(ComputerInputPacket.paste(id, clip));
            return true;
        }

        boolean held = !down.add(key);
        send(ComputerInputPacket.key(id, key, held));
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (down.remove(key)) send(ComputerInputPacket.keyUp(menu.containerId, key));
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (c >= 32 && c != 127) send(ComputerInputPacket.chr(menu.containerId, c));
        return true;
    }
}
