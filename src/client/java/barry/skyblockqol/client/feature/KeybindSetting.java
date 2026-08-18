package barry.skyblockqol.client.feature;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class KeybindSetting {

    public static final int UNBOUND = -1;

    private final String name;
    private final String description;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public KeybindSetting(String name, String description, IntSupplier getter, IntConsumer setter) {
        this.name = name;
        this.description = description;
        this.getter = getter;
        this.setter = setter;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getKeyCode() { return getter.getAsInt(); }
    public void setKeyCode(int keyCode) { setter.accept(keyCode); }

    public String getKeyDisplayName() {
        int code = getKeyCode();
        if (code == UNBOUND) return "Unbound";
        return com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(code)
                .getDisplayName().getString();
    }
}