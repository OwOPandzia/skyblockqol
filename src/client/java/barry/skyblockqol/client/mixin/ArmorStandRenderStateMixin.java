package barry.skyblockqol.client.mixin;

import barry.skyblockqol.client.duck.HeadOnlyGlow;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ArmorStandRenderState.class)
public class ArmorStandRenderStateMixin implements HeadOnlyGlow {

    @Unique
    private boolean skyblockQOL$headOnly = false;

    @Override
    public boolean skyblockQOL$isHeadOnly() {
        return this.skyblockQOL$headOnly;
    }

    @Override
    public void skyblockQOL$setHeadOnly(boolean value) {
        this.skyblockQOL$headOnly = value;
    }
}