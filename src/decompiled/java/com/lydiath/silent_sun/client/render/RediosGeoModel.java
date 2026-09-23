package com.lydiath.silent_sun.client.render;

import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RediosGeoModel extends GeoModel<RediosEntity> {
    @Override
    public ResourceLocation getModelResource(RediosEntity object) {
        return ResourceLocation.fromNamespaceAndPath("silent_sun", "geo/redios.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RediosEntity object) {
        return ResourceLocation.fromNamespaceAndPath("silent_sun", "textures/entity/redios.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RediosEntity animatable) {
        // 目前没有动画，返回一个空占位
        return ResourceLocation.fromNamespaceAndPath("silent_sun", "animations/redios.animation.json");
    }
}
