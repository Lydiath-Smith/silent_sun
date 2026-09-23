package com.lydiath.silent_sun.client.render;

import com.lydiath.silent_sun.entity.RediosEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RediosApparelLayer extends RenderLayer<RediosEntity, HumanoidModel<RediosEntity>> {
    // 纯黑色披风贴图 (稍后会用脚本生成一张 64x64 的纯黑图片)
    private static final ResourceLocation CAPE_TEXTURE = ResourceLocation.fromNamespaceAndPath("silent_sun", "textures/entity/redios_apparel.png");
    private final ModelPart collar;
    private final ModelPart capeTop;
    private final ModelPart capeMid;
    private final ModelPart capeBottom;

    public RediosApparelLayer(RenderLayerParent<RediosEntity, HumanoidModel<RediosEntity>> parent) {
        super(parent);
        ModelPart root = createLayer().bakeRoot();
        this.collar = root.getChild("collar");
        this.capeTop = root.getChild("capeTop");
        this.capeMid = this.capeTop.getChild("capeMid");
        this.capeBottom = this.capeMid.getChild("capeBottom");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 领子 (环绕后颈和肩膀)
        PartDefinition collar = root.addOrReplaceChild("collar", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.5f, -0.5f, 1.5f, 9.0f, 5.5f, 1.5f) // 后颈
            .texOffs(0, 7).addBox(-7.0f, -1.0f, -2.0f, 3.0f, 5.0f, 5.0f) // 右肩
            .texOffs(16, 7).addBox(4.0f, -1.0f, -2.0f, 3.0f, 5.0f, 5.0f), // 左肩
            PartPose.offset(0.0f, 0.0f, 0.0f));

        // 披风上段
        PartDefinition capeTop = root.addOrReplaceChild("capeTop", CubeListBuilder.create()
            .texOffs(0, 18).addBox(-6.0f, 0.0f, 0.0f, 12.0f, 8.0f, 1.0f),
            PartPose.offset(0.0f, 0.0f, 2.5f));

        // 披风中段 (子节点)
        PartDefinition capeMid = capeTop.addOrReplaceChild("capeMid", CubeListBuilder.create()
            .texOffs(0, 27).addBox(-6.5f, 0.0f, 0.0f, 13.0f, 8.0f, 1.0f),
            PartPose.offset(0.0f, 8.0f, 0.0f));

        // 披风下段 (子节点)
        capeMid.addOrReplaceChild("capeBottom", CubeListBuilder.create()
            .texOffs(0, 36).addBox(-7.0f, 0.0f, 0.0f, 14.0f, 8.0f, 1.0f),
            PartPose.offset(0.0f, 8.0f, 0.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, RediosEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        poseStack.pushPose();
        // 跟随身体旋转
        this.getParentModel().body.translateAndRotate(poseStack);

        // 动画逻辑：披风随风飘动 (利用正弦波)
        // 基础摆动幅度
        float baseSwing = Mth.sin(ageInTicks * 0.1f) * 0.1f;
        // 如果实体在移动，披风会向后扬起
        float moveSwing = Mth.lerp(limbSwingAmount, 0.0f, 0.5f);
        
        this.capeTop.xRot = 0.1f + baseSwing + moveSwing;
        this.capeMid.xRot = 0.15f + Mth.sin(ageInTicks * 0.1f - 0.5f) * 0.1f;
        this.capeBottom.xRot = 0.15f + Mth.sin(ageInTicks * 0.1f - 1.0f) * 0.1f;

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entitySolid(CAPE_TEXTURE));
        this.collar.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        this.capeTop.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}
