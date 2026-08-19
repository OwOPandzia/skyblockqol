package barry.skyblockqol.client.mixin;

import barry.skyblockqol.client.duck.HeadOnlyGlow;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.armorstand.ArmorStandModel;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * basePlate is declared directly in ArmorStandModel itself (private
 * final ModelPart, confirmed in its own constructor) - a genuine
 * first-level target, unlike body/leftArm/rightArm/leftLeg/rightLeg
 * which moved to HumanoidModelMixin. See that class's javadoc for why.
 */
@Mixin(ArmorStandModel.class)
public abstract class ArmorStandModelMixin {

    @Shadow @Final private ModelPart basePlate;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void skyblockQOL$hideBasePlateForHeadOnly(ArmorStandRenderState state, CallbackInfo ci) {
        if (!((HeadOnlyGlow) state).skyblockQOL$isHeadOnly()) return;

        this.basePlate.visible = false;
    }
}