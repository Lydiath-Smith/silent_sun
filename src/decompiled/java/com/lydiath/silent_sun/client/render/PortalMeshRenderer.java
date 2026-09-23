package com.lydiath.silent_sun.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.GroupObject;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把拔刀剑 OBJ 模型的**真实面**写进传送门 buffer（本模组版）。
 *
 * <p>2026-09-14：照 <b>灭却之日</b>（`extinction_day_mod_1784441698`）的
 * `fx/PortalMeshRenderer` 移植 —— 他们的交接文档（`_传送门与残影渲染-交接给silent_sun.md`
 * §1.2 / §1.3 / §八）明确推荐这个做法：**沿真实模型面写顶点**，而不是用包围盒或简化矩形；
 * 三角面用**退化 quad**（`a,b,c,c`）补齐以满足 {@code VertexFormat.Mode.QUADS}。
 *
 * <p><b>性能</b>：模型空间 quad 列表按（模型实例 + 组名）**缓存一次**，之后每帧只做矩阵乘法与写色。
 * 拖尾最多 11 个历史矩阵 × 同一份缓存 ⇒ 省掉逐帧的面遍历。
 *
 * <p><b>⚠️ 顶点格式必须与目标 RenderType 严格配对</b>（交接文档踩坑清单 #1：配错会
 * {@code IllegalStateException: Missing elements in vertex: Color} 并**崩渲染线程**）：
 * {@link #writePortalTrail} 面向 {@code PORTAL_TRAIL}（POSITION_COLOR，逐顶点写 RGBA），
 * {@link #writePortal} 面向 POSITION-only（不得调 setColor）。
 */
public final class PortalMeshRenderer {

    /** 模型空间 quad 缓存：模型实例 → (组名 → quad 数组)。每个 quad = 12 个 float（4 顶点 × xyz）。 */
    private static final Map<WavefrontObject, Map<String, float[][]>> QUAD_CACHE = new IdentityHashMap<>();

    private PortalMeshRenderer() {
    }

    /** 写 POSITION-only 传送门 buffer（原版 {@code RenderType.endPortal()} 用；无 alpha）。 */
    public static void writePortal(VertexConsumer buf, Matrix4f matrix, WavefrontObject model, String groupName) {
        writeQuads(buf, matrix, model, groupName, false, 0);
    }

    /**
     * 写 {@code PortalTrailRenderType.PORTAL_TRAIL}（POSITION_COLOR）。
     *
     * @param argb 打包 ARGB（{@code 0xAARRGGBB}，alpha 在最高字节）
     */
    public static void writePortalTrail(VertexConsumer buf, Matrix4f matrix, WavefrontObject model, String groupName, int argb) {
        writeQuads(buf, matrix, model, groupName, true, argb);
    }

    private static void writeQuads(VertexConsumer buf, Matrix4f matrix, WavefrontObject model, String groupName, boolean colored, int argb) {
        if (buf == null || model == null || model.groupObjects == null) {
            return;
        }
        float[][] quads = quadsOf(model, groupName);
        if (quads.length == 0) {
            return;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >>> 24) & 0xFF;
        for (float[] q : quads) {
            for (int v = 0; v + 2 < q.length; v += 3) {
                if (colored) {
                    buf.addVertex(matrix, q[v], q[v + 1], q[v + 2]).setColor(r, g, b, a);
                } else {
                    buf.addVertex(matrix, q[v], q[v + 1], q[v + 2]);
                }
            }
        }
    }

    private static float[][] quadsOf(WavefrontObject model, String groupName) {
        Map<String, float[][]> perGroup = QUAD_CACHE.computeIfAbsent(model, m -> new HashMap<>());
        float[][] quads = perGroup.get(groupName);
        if (quads == null) {
            quads = buildQuads(model, groupName);
            perGroup.put(groupName, quads);
        }
        return quads;
    }

    private static float[][] buildQuads(WavefrontObject model, String groupName) {
        List<float[]> out = new ArrayList<>();
        for (GroupObject group : model.groupObjects) {
            if (group == null || group.faces == null) {
                continue;
            }
            if (groupName != null && !groupName.isEmpty() && !groupName.equals(group.name)) {
                continue;
            }
            for (Face face : group.faces) {
                if (face == null || face.vertices == null) {
                    continue;
                }
                Vertex[] v = face.vertices;
                int n = v.length;
                if (n == 3) {
                    addQuad(out, v[0], v[1], v[2], v[2]);       // 退化 quad：第 4 点重复第 3 点
                } else if (n == 4) {
                    addQuad(out, v[0], v[1], v[2], v[3]);
                } else if (n >= 3) {
                    for (int i = 1; i + 1 < n; i++) {
                        addQuad(out, v[0], v[i], v[i + 1], v[i + 1]);
                    }
                }
            }
        }
        return out.toArray(new float[0][]);
    }

    private static void addQuad(List<float[]> out, Vertex a, Vertex b, Vertex c, Vertex d) {
        if (a == null || b == null || c == null || d == null) {
            return;
        }
        out.add(new float[] { a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z });
    }
}
