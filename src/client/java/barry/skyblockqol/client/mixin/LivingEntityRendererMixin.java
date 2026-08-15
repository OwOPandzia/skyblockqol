package barry.skyblockqol.client.mixin;

import barry.skyblockqol.client.SkyblockQOLClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(
            method = "extractRenderState",
            at = @At("RETURN")
    )
    private void skyblockQOL$setOutlineColor(
            LivingEntity entity,
            LivingEntityRenderState state,
            float partialTicks,
            CallbackInfo ci
    ) {
        SkyblockQOLClient.GlowColor color =
                SkyblockQOLClient.getGlowColor(entity);

        if (color != SkyblockQOLClient.GlowColor.NONE) {
            state.outlineColor = color.getColor();
        }
    }
}