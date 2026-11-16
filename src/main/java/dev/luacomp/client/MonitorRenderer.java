package dev.luacomp.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.luacomp.block.MonitorBlock;
import dev.luacomp.block.MonitorBlockEntity;
import dev.luacomp.terminal.Terminal;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Desenha o terminal do monitor na face da frente, ocupando todo o retângulo de monitores. */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final float CELL_W = 6f, CELL_H = 9f;

    private final Font font;

    public MonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isOriginClient()) return;
        BlockState state = be.getBlockState();
        if (!state.hasProperty(MonitorBlock.FACING)) return;

        Terminal t = be.getTerminal();
        int cols = t.width(), rows = t.height();
        char[] chars = new char[cols * rows];
        byte[] colours = new byte[cols * rows];
        t.snapshot(chars, colours);
        int[] pal = new int[16];
        t.snapshotPalette(pal);

        Direction f = state.getValue(MonitorBlock.FACING);
        Direction r = f.getCounterClockWise();
        Vec3 tl = MonitorBlockEntity.faceTopLeft(BlockPos.ZERO, f);

        ps.pushPose();
        ps.translate(tl.x + f.getStepX() * 0.002, tl.y, tl.z + f.getStepZ() * 0.002);
        ps.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(-r.getStepZ(), r.getStepX()))));
        float sx = be.getGroupWidth() / (float) cols / CELL_W;
        float sy = be.getGroupHeight() / (float) rows / CELL_H;
        ps.scale(sx, -sy, sx); // y para baixo, como em placas

        // fundos
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE));
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        for (int y = 0; y < rows; y++) {
            int x = 0;
            while (x < cols) {
                int bg = colours[y * cols + x] & 0x0F;
                int start = x;
                while (x < cols && (colours[y * cols + x] & 0x0F) == bg) x++;
                quad(vc, pose, normal, start * CELL_W, y * CELL_H, x * CELL_W, (y + 1) * CELL_H, pal[bg]);
            }
        }

        // texto (um pouco à frente dos fundos)
        ps.translate(0, 0, 0.5);
        Matrix4f textPose = ps.last().pose();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                char c = chars[y * cols + x];
                if (c == ' ') continue;
                String s = String.valueOf(c);
                int fg = (colours[y * cols + x] >> 4) & 0x0F;
                float px = x * CELL_W + Math.max(0f, (CELL_W - font.width(s)) / 2f);
                font.drawInBatch(s, px, y * CELL_H + 0.5f, pal[fg], false, textPose, buffers,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }

        if (t.blink() && (Util.getMillis() / 400) % 2 == 0) {
            int cx = t.cursorX(), cy = t.cursorY();
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                int fg = (colours[cy * cols + cx] >> 4) & 0x0F;
                font.drawInBatch("_", cx * CELL_W, cy * CELL_H + 0.5f, pal[fg], false, textPose, buffers,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
        }
        ps.popPose();
    }

    /** Quadrado desenhado dos dois lados (a inversão do eixo Y inverte o sentido dos vértices). */
    private static void quad(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x0, float y0, float x1, float y1, int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[][] pts = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        for (int i = 0; i < 4; i++) vertex(vc, pose, normal, pts[i][0], pts[i][1], r, g, b);
        for (int i = 3; i >= 0; i--) vertex(vc, pose, normal, pts[i][0], pts[i][1], r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, float x, float y, int r, int g, int b) {
        vc.vertex(pose, x, y, 0f)
                .color(r, g, b, 255)
                .uv(0f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0f, 0f, 1f)
                .endVertex();
    }
}
