package org.vivecraft.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraft.world.storage.MapData;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.vivecraft.render.Renderable;
import org.vivecraft.utils.math.Quaternion;
import org.vivecraft.utils.math.Vector3;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

public class Debug {
    public static boolean allowDebug = true;
    public static boolean isEnabled = true;
    public static HashMap<String, Debug> debuggers = new HashMap<>();
    static Polygon cross = new Polygon(6);
    static Polygon arrowHead = new Polygon(8);
    private static DebugRendererManual renderer = new DebugRendererManual();

    static {
        cross.colors[0] = new Color(0, 0, 0, 0);
        cross.vertices[0] = new Vector3d(0, -0.1, 0);
        cross.vertices[1] = new Vector3d(0, 0.1, 0);
        cross.colors[2] = new Color(0, 0, 0, 0);

        cross.vertices[2] = new Vector3d(0, 0, -0.1);
        cross.vertices[3] = new Vector3d(0, 0, 0.1);
        cross.colors[4] = new Color(0, 0, 0, 0);

        cross.vertices[4] = new Vector3d(-0.1, 0, 0);
        cross.vertices[5] = new Vector3d(0.1, 0, 0);

        arrowHead.colors[0] = new Color(0, 0, 0, 0);
        arrowHead.vertices[0] = new Vector3d(0, 0, 0);
        arrowHead.vertices[1] = new Vector3d(-0.05, -0.05, 0);

        arrowHead.colors[2] = new Color(0, 0, 0, 0);
        arrowHead.vertices[2] = new Vector3d(0, 0, 0);
        arrowHead.vertices[3] = new Vector3d(0.05, -0.05, 0);

        arrowHead.colors[4] = new Color(0, 0, 0, 0);
        arrowHead.vertices[4] = new Vector3d(0, 0, 0);
        arrowHead.vertices[5] = new Vector3d(0, -0.05, -0.05);

        arrowHead.colors[6] = new Color(0, 0, 0, 0);
        arrowHead.vertices[6] = new Vector3d(0, 0, 0);
        arrowHead.vertices[7] = new Vector3d(0, -0.05, 0.05);
    }

    private final String context;
    public HashMap<String, Polygon[]> toDraw = new HashMap<>();
    public boolean enabled = true;
    Vector3d root;
    Quaternion rotation;


    private Debug(String context, Vector3d root, Quaternion rotation) {
        this.context = context;
        this.root = root;
        this.rotation = rotation;
    }

    public static DebugRendererManual getRenderer() {
        return renderer;
    }

    public static Debug get(String context) {
        return get(context, Vector3d.ZERO, new Quaternion());
    }

    public static Debug get(String context, Vector3d root) {
        return get(context, root, new Quaternion());
    }

    public static Debug get(String context, Vector3d root, Quaternion rotation) {
        Debug d = debuggers.get(context);
        if (d == null) {
            d = new Debug(context, root, rotation);

            if(allowDebug)
                debuggers.put(context, d);
        }
        return d;
    }

    public static void close(String context) {
        debuggers.remove(context);
    }

    public static void logChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.printChatMessage(message);
    }

    public void drawPoint(String name, Vector3d point, Color color) {
        if (!Debug.allowDebug)
            return;

        point = rotation.multiply(point);
        Vector3d global = root.add(point);
        Polygon poly = cross.offset(global);
        for (int i = 0; i < poly.colors.length; i++) {
            if (poly.colors[i] == null)
                poly.colors[i] = color;
        }
        drawPoly(name, poly);
    }

    public void drawVector(String name, Vector3d start, Vector3d direction, Color color) {
        if (!Debug.allowDebug)
            return;

        Polygon poly = new Polygon(2);

        start = rotation.multiply(start);
        direction = rotation.multiply(direction);

        poly.vertices[0] = root.add(start);
        poly.colors[0] = new Color(0, 0, 0, 0);

        poly.vertices[1] = root.add(start).add(direction);
        poly.colors[1] = color;

        Quaternion rot = Quaternion.createFromToVector(new Vector3(0, 1, 0), new Vector3(direction.normalize()));
        Polygon arrow = arrowHead.rotated(rot).offset(root.add(start).add(direction));

        for (int i = 0; i < arrow.colors.length; i++) {
            if (arrow.colors[i] == null)
                arrow.colors[i] = color;
        }

        drawPoly(name, poly, arrow);
    }

    public void drawLine(String name, Vector3d start, Vector3d end, Color color) {
        if (!Debug.allowDebug)
            return;

        start = rotation.multiply(start);
        end = rotation.multiply(end);

        Polygon poly = new Polygon(2);

        poly.vertices[0] = root.add(start);
        poly.colors[0] = new Color(0, 0, 0, 0);

        poly.vertices[1] = root.add(end);
        poly.colors[1] = color;

        drawPoly(name, poly);
    }

    public void drawBoundingBox(String name, AxisAlignedBB box, Color color) {
        if (!Debug.allowDebug)
            return;

        Polygon poly = new Polygon(16);
        Vector3d[] lower = new Vector3d[4];
        Vector3d[] upper = new Vector3d[4];
        int index = 0;

        lower[0] = new Vector3d(box.minX, box.minY, box.minZ);
        lower[1] = new Vector3d(box.minX, box.minY, box.maxZ);
        lower[2] = new Vector3d(box.maxX, box.minY, box.maxZ);
        lower[3] = new Vector3d(box.maxX, box.minY, box.minZ);

        upper[0] = new Vector3d(box.minX, box.maxY, box.minZ);
        upper[1] = new Vector3d(box.minX, box.maxY, box.maxZ);
        upper[2] = new Vector3d(box.maxX, box.maxY, box.maxZ);
        upper[3] = new Vector3d(box.maxX, box.maxY, box.minZ);


        for (int i = 0; i < 4; i++) {
            lower[i] = root.add(rotation.multiply(lower[i]));
            upper[i] = root.add(rotation.multiply(upper[i]));
        }

        for (int i = 0; i < 5; i++) {
            if (i == 0)
                poly.colors[index] = new Color(0, 0, 0, 0);
            else
                poly.colors[index] = color;
            poly.vertices[index] = lower[i % 4];
            index++;
        }

        for (int i = 0; i < 5; i++) {
            poly.colors[index] = color;
            poly.vertices[index] = upper[i % 4];
            index++;
        }

        for (int i = 1; i < 4; i++) {
            poly.vertices[index] = lower[i];
            poly.colors[index] = new Color(0, 0, 0, 0);
            index++;
            poly.vertices[index] = upper[i];
            poly.colors[index] = color;
            index++;
        }
        drawPoly(name, poly);
    }

    private void drawPoly(String name, Polygon... polygons) {
        toDraw.put(name, polygons);
    }

    static class Polygon {
        Vector3d[] vertices;
        Color[] colors;
        public Polygon(int size) {
            vertices = new Vector3d[size];
            colors = new Color[size];
        }

        public Polygon offset(Vector3d offset) {
            Polygon pol = new Polygon(vertices.length);
            for (int i = 0; i < vertices.length; i++) {
                pol.vertices[i] = vertices[i].add(offset);
                pol.colors[i] = colors[i];
            }
            return pol;
        }

        public Polygon rotated(Quaternion quat) {
            Polygon pol = new Polygon(vertices.length);
            for (int i = 0; i < vertices.length; i++) {
                pol.vertices[i] = quat.multiply(new Vector3(vertices[i])).toVector3d();
                pol.colors[i] = colors[i];
            }
            return pol;
        }


    }


    public static class DebugRendererManual implements Renderable {


        public void render(float partialTicks, RenderStage stage) {
            if (!Debug.allowDebug)
                return;

            for (Debug d : debuggers.values()) {
                if (!d.enabled || d.toDraw.isEmpty())
                    return;

                PlayerEntity entityplayer = Minecraft.getInstance().player;
                double d0 = entityplayer.lastTickPosX + (entityplayer.getPosX() - entityplayer.lastTickPosX) * (double) partialTicks;
                double d1 = entityplayer.lastTickPosY + (entityplayer.getPosY() - entityplayer.lastTickPosY) * (double) partialTicks;
                double d2 = entityplayer.lastTickPosZ + (entityplayer.getPosZ() - entityplayer.lastTickPosZ) * (double) partialTicks;


                GlStateManager.enableBlend();
                //GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                GlStateManager.lineWidth(5.0F);
                GlStateManager.disableTexture();
                GlStateManager.disableLighting();
                GlStateManager.depthMask(false);
                //Draw over everything
                GL11.glDepthRange(1,1);
                GlStateManager.disableDepthTest();

                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);


                for (Polygon[] polys : d.toDraw.values()) {
                    for (Polygon polygon : polys) {
                        for (int i = 0; i < polygon.vertices.length; i++) {
                            renderVertex(buffer, polygon.vertices[i], polygon.colors[i], d0, d1, d2);
                        }
                    }
                }

                tessellator.draw();

                GlStateManager.depthMask(true);
                GL11.glDepthRange(0,1);
                GlStateManager.enableTexture();
                GlStateManager.enableLighting();
                GlStateManager.disableBlend();

                GlStateManager.enableDepthTest();

            }
        }

        void renderVertex(BufferBuilder buffer, Vector3d vert, Color color, double offX, double offY, double offZ) {
            buffer.pos(vert.x - offX, vert.y - offY, vert.z - offZ)
                    .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                    .endVertex();
        }
    }
}
