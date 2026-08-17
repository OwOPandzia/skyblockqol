package barry.skyblockqol.client.finder;

import java.util.UUID;

/** Plain data holder for a single saved finder. Gson-serialized as-is. */
public class FinderConfig {

    public String id = UUID.randomUUID().toString();
    public String name = "New Finder";
    public FinderType type = FinderType.BLOCK;

    // --- Block criteria ---
    public String blockId;            // e.g. "minecraft:diamond_ore"
    public boolean includeConnected;  // flood-fill adjacent matching blocks

    // --- Entity criteria ---
    public EntityCriteriaType entityCriteriaType = EntityCriteriaType.MOB_TYPE;
    public String entityTypeId;       // for MOB_TYPE, e.g. "minecraft:zombie"
    public double hpAmount;           // for HP_AMOUNT
    public double hpTolerance = 2.0;  // for HP_AMOUNT
    public String nbtSnippet;         // for NBT, partial/substring match against saved SNBT string

    public int glowColorArgb = 0xFFFFFFFF;

    // --- Behavior ---
    public boolean continuous;        // true = toggle-and-keep-glowing, false = single click search
    public transient boolean enabled; // runtime on/off state for continuous finders, not persisted needed but harmless

    public FinderConfig() {}

    public FinderConfig copy() {
        FinderConfig c = new FinderConfig();
        c.id = this.id;
        c.name = this.name;
        c.type = this.type;
        c.blockId = this.blockId;
        c.includeConnected = this.includeConnected;
        c.entityCriteriaType = this.entityCriteriaType;
        c.entityTypeId = this.entityTypeId;
        c.hpAmount = this.hpAmount;
        c.hpTolerance = this.hpTolerance;
        c.nbtSnippet = this.nbtSnippet;
        c.glowColorArgb = this.glowColorArgb;
        c.continuous = this.continuous;
        return c;
    }
}