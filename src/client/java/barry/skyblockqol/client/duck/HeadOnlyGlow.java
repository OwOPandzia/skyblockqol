package barry.skyblockqol.client.duck;

/**
 * Duck-type interface implemented by ArmorStandRenderStateMixin so
 * ArmorStandRendererMixin (which sets the flag) and ArmorStandModelMixin /
 * HumanoidModelMixin (which read it) can talk to each other through the
 * render state object without depending on each other's mixin classes
 * directly.
 *
 * Deliberately NOT in the barry.skyblockqol.client.mixin package - Mixin
 * reserves that entire package exclusively for @Mixin-annotated
 * transformer classes (declared via "package" in the mixins.json config)
 * and refuses to let anything reference plain classes/interfaces placed
 * there directly, even non-mixin ones. Confirmed via IllegalClassLoadError
 * at runtime when this lived in the mixin package.
 */
public interface HeadOnlyGlow {
    boolean skyblockQOL$isHeadOnly();
    void skyblockQOL$setHeadOnly(boolean value);
}