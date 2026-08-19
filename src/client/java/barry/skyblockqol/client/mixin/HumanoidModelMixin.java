package barry.skyblockqol.client.mixin;

import barry.skyblockqol.client.duck.HeadOnlyGlow;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Targets HumanoidModel directly - the ACTUAL declaring class of
 * body/leftArm/rightArm/leftLeg/rightLeg (confirmed via decompiled
 * source: all five are "public final ModelPart" fields set in
 * HumanoidModel's constructor). The previous attempt shadowed these from
 * ArmorStandModel, two classes below the real declaration
 * (ArmorStandModel -> ArmorStandArmorModel -> HumanoidModel) - Mixin's
 * @Shadow field lookup only searches the exact target class, not
 * superclasses, which is exactly what the "was not located in the target
 * class" error was reporting. Shadowing them here, on their real
 * declaring class, avoids that entirely.
 *
 * This runs for setupAnim on every humanoid-family model (players,
 * zombies, armor stands, etc.) every frame, but the instanceof check
 * below makes it a no-op for anything that isn't a HeadOnlyGlow-flagged
 * render state - in practice today, only ArmorStandRenderState instances
 * flagged by ArmorStandRendererMixin for KeyLocatorFeature targets.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftLeg;
    @Shadow @Final public ModelPart rightLeg;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void skyblockQOL$hideBodyPartsForHeadOnly(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof HeadOnlyGlow glow) || !glow.skyblockQOL$isHeadOnly()) return;

        this.body.visible = false;
        this.leftArm.visible = false;
        this.rightArm.visible = false;
        this.leftLeg.visible = false;
        this.rightLeg.visible = false;
    }
}