/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value="silent_sun", dist={Dist.CLIENT})
public final class SilentSunClient {
    public SilentSunClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)ConfigurationScreen::new);
    }
}
