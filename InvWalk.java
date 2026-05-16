String[] modes = {"Pause", "Stop"};
String[] sprintModes = {
    "Never",
    "2 ticks", "4 ticks", "6 ticks", "8 ticks", "10 ticks",
    "12 ticks", "14 ticks", "16 ticks", "18 ticks", "20 ticks",
    "Always"
};
boolean invMove = false;
boolean pausedForClick = false;
int pauseTicks = 0;

boolean lastScreenOpen = false;
boolean stoppedUntilReopen = false;
boolean inventorySnapshotReady = false;
String lastInventorySnapshot = "";

int sprintDelayTicks = 0;
boolean sprintEnabled = false;

void onLoad() {
    modules.registerDescription("Legitimate InvWalk options.");

    modules.registerButton("Inventory", true);
    modules.registerButton("Container", false);
    modules.registerSlider("Mode", "", 0, modes);
    modules.registerSlider("Click pause", "ticks", 6, 1, 16, 1);
    modules.registerSlider("Enable sprint", "", 11, sprintModes);
}

void onEnable() {
    resetState();
    applyInvMoveSettings();
}

void onDisable() {
    modules.disable("InvMove");
    resetState();
}

void resetState() {
    invMove = false;
    pausedForClick = false;
    pauseTicks = 0;
    lastScreenOpen = false;
    stoppedUntilReopen = false;
    inventorySnapshotReady = false;
    lastInventorySnapshot = "";

    sprintDelayTicks = 0;
    sprintEnabled = false;
}

void applyInvMoveSettings() {
    modules.setSlider("InvMove", "Inventory", modules.getButton(scriptName, "Inventory") ? 1 : 0);
    modules.setSlider("InvMove", "Chest & others", modules.getButton(scriptName, "Container") ? 1 : 0);
    modules.setSlider("InvMove", "Motion", 0);

    modules.setButton("InvMove", "Allow jumping", true);
    modules.setButton("InvMove", "Allow movement", true);
    modules.setButton("InvMove", "Allow midair", true);
    modules.setButton("InvMove", "Allow rotating", true);
    modules.setButton("InvMove", "Allow speed potion", true);
    modules.setButton("InvMove", "Allow sprinting", sprintEnabled);

    modules.setButton("InvMove", "Modify motion after click", true);
    modules.setButton("InvMove", "Slow motion when necessary", false);
    modules.setButton("InvMove", "Only with inventory manager", false);
}

boolean isAnyScreenOpen() {
    String screen = client.getScreen();
    return screen != null && !screen.isEmpty();
}

void moveKeys(boolean pressed) {
    keybinds.setPressed("left", pressed);
    keybinds.setPressed("right", pressed);
    keybinds.setPressed("forward", pressed);
    keybinds.setPressed("back", pressed);
    keybinds.setPressed("jump", pressed);
}

void onPreUpdate() {
    boolean screenOpen = isAnyScreenOpen();
    int mode = (int) modules.getSlider(scriptName, "Mode");
    int sprintMode = (int) modules.getSlider(scriptName, "Enable sprint");

    int requiredTicks = 0;
    if (sprintMode >= 1 && sprintMode <= 10) {
        requiredTicks = sprintMode * 2;
    }

    if (screenOpen && !lastScreenOpen) {
        invMove = true;
        pausedForClick = false;
        pauseTicks = 0;
        stoppedUntilReopen = false;
        captureInventorySnapshot();

        sprintDelayTicks = 0;
        sprintEnabled = (sprintMode == 11);
    }

    if (!screenOpen && lastScreenOpen) {
        modules.disable("InvMove");
        resetState();
        lastScreenOpen = false;
        return;
    }

    lastScreenOpen = screenOpen;

    if (!screenOpen) return;

    if (inventorySnapshotReady && hasInventoryChanged()) {
        pauseForInventoryAction(mode);
        captureInventorySnapshot();
    } else if (!inventorySnapshotReady) {
        captureInventorySnapshot();
    }

    if (modules.isEnabled("Scaffold")) {
        modules.disable("InvMove");
        invMove = false;
        sprintEnabled = false;
        return;
    }

    if (sprintMode == 0) {
        sprintEnabled = false;
    } else if (sprintMode >= 1 && sprintMode <= 10) {
        sprintDelayTicks++;
        sprintEnabled = sprintDelayTicks >= requiredTicks;
    } else if (sprintMode == 11) {
        sprintEnabled = true;
    }

    applyInvMoveSettings();

    if (pausedForClick) {
        modules.disable("InvMove");
        moveKeys(false);

        pauseTicks++;

        if (pauseTicks >= (int) modules.getSlider(scriptName, "Click pause")) {
            pausedForClick = false;
            pauseTicks = 0;
        }

        return;
    }

    if (mode == 1 && stoppedUntilReopen) {
        modules.disable("InvMove");
        return;
    }

    if (invMove) {
        modules.enable("InvMove");
    }
}

boolean onPacketSent(CPacket packet) {
    if (packet instanceof C0E) {
        pauseForInventoryAction((int) modules.getSlider(scriptName, "Mode"));
        captureInventorySnapshot();
    }

    return true;
}

void pauseForInventoryAction(int mode) {
    modules.disable("InvMove");
    moveKeys(false);

    pausedForClick = true;
    pauseTicks = 0;

    if (mode == 1) {
        stoppedUntilReopen = true;
    }
}

void captureInventorySnapshot() {
    lastInventorySnapshot = getInventorySnapshot();
    inventorySnapshotReady = true;
}

boolean hasInventoryChanged() {
    return !lastInventorySnapshot.equals(getInventorySnapshot());
}

String getInventorySnapshot() {
    String out = "";
    int size = inventory.getSize();

    for (int i = 0; i < size; i++) {
        ItemStack s = inventory.getStackInSlot(i);
        if (s == null || s.name == null) {
            out += i + ":empty;";
        } else {
            out += i + ":" + s.name + ":" + s.meta + ":" + s.stackSize + ";";
        }
    }

    return out;
}

