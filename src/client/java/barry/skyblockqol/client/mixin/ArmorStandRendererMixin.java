package barry.skyblockqol.client.mixin;

import barry.skyblockqol.client.duck.HeadOnlyGlow;
import barry.skyblockqol.client.feature.KeyLocatorFeature;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandRenderer.class)
public class ArmorStandRendererMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void skyblockQOL$markHeadOnly(ArmorStand entity, ArmorStandRenderState state,
                                          float partialTicks, CallbackInfo ci) {
        boolean headOnly = KeyLocatorFeature.shouldGlow(entity);
        ((HeadOnlyGlow) state).skyblockQOL$setHeadOnly(headOnly);
    }
}