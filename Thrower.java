// Thrower - Voids Bed Wars resources on activation.
// Automatic - Voids Bed Wars resources on inventory open in the void.
// Binds - Voids Bed Wars resources on inventory open, in the void when bind is pressed.

String[] bedWarsResources = {"iron_ingot", "gold_ingot", "emerald", "diamond"};
String[] activateOptions = {
    "Automatic",
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
    "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10",
    "F11", "F12",
    "Escape", "Minus", "Equals", "Backspace", "Tab",
    "Left Bracket", "Right Bracket", "Enter",
    "Left Ctrl", "Right Ctrl", "Left Shift", "Right Shift", "Left Alt", "Right Alt",
    "Space", "Caps Lock", "Grave", "Backslash",
    "Semicolon", "Apostrophe", "Comma", "Period", "Slash",
    "Num Lock", "Scroll Lock",
    "Insert", "Delete", "Home", "End", "Page Up", "Page Down",
    "Up", "Down", "Left", "Right"
};
int[] activateKeyCodes = {
    0,
    2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
    30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50,
    49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44,
    59, 60, 61, 62, 63, 64, 65, 66, 67, 68,
    87, 88,
    1, 12, 13, 14, 15,
    26, 27, 28,
    29, 157, 42, 54, 56, 184,
    57, 58, 41, 43,
    39, 40, 51, 52, 53,
    69, 70,
    210, 211, 199, 207, 201, 209,
    200, 208, 203, 205
};

boolean lastInventoryOpen = false;
boolean throwing = false;
boolean confirmingThrow = false;
boolean didThrowThisSession = false;

List<Integer> throwQueue = new ArrayList<Integer>();
int[] beforeCounts = new int[bedWarsResources.length];
long confirmAt = 0L;
long confirmDelay = 1000;
long nextThrowAt = 0L;
int safeBlockRadius = 1;
int automaticMinFallTicks = 2;
int automaticFallTicks = 0;
double lastPlayerY = 0.0;
boolean hasLastPlayerY = false;

void onLoad() {
    modules.registerDescription("Voids Bed Wars resources.");
    modules.registerButton("Iron", true);
    modules.registerButton("Gold", true);
    modules.registerButton("Emerald", true);
    modules.registerButton("Diamond", true);
    modules.registerSlider("Activate", "", 57, activateOptions);
    modules.registerSlider("Throw delay", "ms", 0, 0, 100, 25);
    modules.registerButton("Only in Bed Wars", true);
    modules.registerButton("Send message", true);
}

void onEnable() {
    reset();
}

void onDisable() {
    reset();
}

void reset() {
    lastInventoryOpen = false;
    throwing = false;
    confirmingThrow = false;
    didThrowThisSession = false;
    throwQueue.clear();
    confirmAt = 0L;
    nextThrowAt = 0L;
    automaticFallTicks = 0;
    hasLastPlayerY = false;
    for (int i = 0; i < beforeCounts.length; i++) beforeCounts[i] = 0;
}

void onPreUpdate() {
    boolean inventoryOpen = isInventoryOpen();
    boolean justOpened = inventoryOpen && !lastInventoryOpen;
    lastInventoryOpen = inventoryOpen;
    boolean bindHeld = isBindHeld();
    boolean automatic = isAutomaticActivate();
    boolean inVoid = isInVoid();
    boolean clearVoid = inVoid && isClearVoidAroundPlayer();
    updateAutomaticFallTicks(clearVoid);

    if (!inventoryOpen) {
        if (throwing) {
            throwing = false;
            throwQueue.clear();
            nextThrowAt = 0L;
            startThrowConfirm();
        }

        didThrowThisSession = false;
        if (confirmingThrow && client.time() >= confirmAt) {
            confirmThrowResult();
        }
        return;
    }

    boolean fallReady = !automatic || automaticFallTicks >= automaticMinFallTicks;
    boolean shouldStartThrowing = bindHeld && fallReady && !didThrowThisSession && !confirmingThrow && canUseHere() && inVoid && clearVoid && hasAnyTarget();

    if (shouldStartThrowing || throwing) {
        suppressSprint();
    }

    if (shouldStartThrowing) {
        startThrowing();
    }

    if (throwing) {
        throwNextVisibleStack();
        return;
    }

    if (confirmingThrow && client.time() >= confirmAt) {
        confirmThrowResult();
    }
}

boolean isInventoryOpen() {
    String screen = client.getScreen();
    return screen != null && screen.toLowerCase().contains("inventory");
}

boolean canUseHere() {
    return !modules.getButton(scriptName, "Only in Bed Wars") || getBedwarsStatus() == 3;
}

boolean isAutomaticActivate() {
    return (int) modules.getSlider(scriptName, "Activate") <= 0;
}

boolean isBindHeld() {
    int activate = (int) modules.getSlider(scriptName, "Activate");
    if (activate <= 0) return true;
    if (activate >= activateKeyCodes.length) return false;

    int keyCode = activateKeyCodes[activate];
    if (keyCode <= 0) return true;

    try {
        return keybinds.isKeyDown(keyCode);
    } catch (Exception ignored) {
    }

    return false;
}

void updateAutomaticFallTicks(boolean clearVoid) {
    Entity player = client.getPlayer();
    if (player == null || !clearVoid) {
        automaticFallTicks = 0;
        hasLastPlayerY = false;
        return;
    }

    Vec3 pos = player.getPosition();
    if (!hasLastPlayerY) {
        lastPlayerY = pos.y;
        hasLastPlayerY = true;
        automaticFallTicks = 0;
        return;
    }

    if (pos.y < lastPlayerY - 0.001) {
        automaticFallTicks++;
    } else if (pos.y > lastPlayerY + 0.001) {
        automaticFallTicks = 0;
    }

    lastPlayerY = pos.y;
}

void suppressSprint() {
    try {
        keybinds.setPressed("sprint", false);
    } catch (Exception ignored) {
    }

    try {
        keybinds.setPressed("key.sprint", false);
    } catch (Exception ignored) {
    }
}

void startThrowing() {
    throwQueue.clear();

    for (int r = 0; r < bedWarsResources.length; r++) {
        beforeCounts[r] = isResourceEnabled(r) ? countItem(bedWarsResources[r]) : 0;
    }

    int invSize = inventory.getSize() - 4;
    if (invSize > 36) invSize = 36;

    for (int slot = 0; slot < invSize; slot++) {
        if (isTargetResource(inventory.getStackInSlot(slot))) {
            throwQueue.add(slot);
        }
    }

    throwing = !throwQueue.isEmpty();
    didThrowThisSession = throwing;
    nextThrowAt = client.time();
}

void throwNextVisibleStack() {
    if (!isBindHeld()) {
        throwing = false;
        throwQueue.clear();
        nextThrowAt = 0L;
        startThrowConfirm();
        return;
    }

    suppressSprint();

    long now = client.time();
    if (now < nextThrowAt) return;

    long delay = (long) modules.getSlider(scriptName, "Throw delay");
    if (delay <= 0L) {
        while (!throwQueue.isEmpty()) {
            throwSlotIfTarget(throwQueue.remove(0));
        }

        finishThrowing();
        return;
    }

    while (!throwQueue.isEmpty()) {
        if (throwSlotIfTarget(throwQueue.remove(0))) {
            nextThrowAt = now + delay;
            return;
        }
    }

    finishThrowing();
}

boolean throwSlotIfTarget(int slot) {
    ItemStack stack = inventory.getStackInSlot(slot);
    if (!isTargetResource(stack)) return false;

    inventory.click(toContainerSlot(slot), 0, 0);
    inventory.click(-999, 0, 0);
    return true;
}

void finishThrowing() {
    throwing = false;
    nextThrowAt = 0L;
    startThrowConfirm();
}

void startThrowConfirm() {
    confirmingThrow = true;
    confirmAt = client.time() + confirmDelay;
}

void confirmThrowResult() {
    confirmingThrow = false;
    confirmAt = 0L;

    if (!modules.getButton(scriptName, "Send message")) return;

    if (isDeadOrSpectator()) {
        sendFailMessage();
        return;
    }

    if (!sendMessage(beforeCounts)) {
        sendFailMessage();
    }
}

boolean isDeadOrSpectator() {
    Entity player = client.getPlayer();
    if (player == null) return true;

    try {
        if (player.isDead()) return true;
    } catch (Exception ignored) {
    }

    try {
        if (player.getHealth() <= 0) return true;
    } catch (Exception ignored) {
    }

    try {
        if (client.allowFlying()) return true;
    } catch (Exception ignored) {
    }

    String screen = client.getScreen();
    return screen != null && screen.toLowerCase().contains("gameover");
}

boolean sendMessage(int[] countsBefore) {
    int[] afterCounts = new int[bedWarsResources.length];
    for (int r = 0; r < bedWarsResources.length; r++) {
        if (isResourceEnabled(r)) afterCounts[r] = countItem(bedWarsResources[r]);
    }

    String cs = util.colorSymbol;
    List<String> parts = new ArrayList<String>();
    int[] thrown = new int[bedWarsResources.length];

    for (int i = 0; i < thrown.length; i++) {
        thrown[i] = Math.max(0, countsBefore[i] - afterCounts[i]);
    }

    if (thrown[0] > 0) parts.add(cs + "e" + thrown[0] + " " + cs + "fIron");
    if (thrown[1] > 0) parts.add(cs + "e" + thrown[1] + " " + cs + "6Gold");
    if (thrown[2] > 0) parts.add(cs + "e" + thrown[2] + " " + cs + "aEmeralds");
    if (thrown[3] > 0) parts.add(cs + "e" + thrown[3] + " " + cs + "bDiamonds");

    if (parts.size() == 0) return false;

    String list = "";
    for (int i = 0; i < parts.size(); i++) {
        if (i > 0) list += cs + "7, ";
        list += parts.get(i);
    }

    client.print(cs + "7[" + cs + "dR" + cs + "7] " + cs + "cVoided " + list + cs + "7.");
    return true;
}

void sendFailMessage() {
    String cs = util.colorSymbol;
    client.print(cs + "7[" + cs + "dR" + cs + "7] " + cs + "cFailed to void items.");
}

boolean isResourceEnabled(int index) {
    if (index == 0) return modules.getButton(scriptName, "Iron");
    if (index == 1) return modules.getButton(scriptName, "Gold");
    if (index == 2) return modules.getButton(scriptName, "Emerald");
    if (index == 3) return modules.getButton(scriptName, "Diamond");
    return false;
}

boolean isTargetResource(ItemStack stack) {
    if (stack == null || stack.name == null) return false;
    String name = util.strip(stack.name).toLowerCase();

    if (modules.getButton(scriptName, "Iron") && isNamed(name, "iron_ingot")) return true;
    if (modules.getButton(scriptName, "Gold") && isNamed(name, "gold_ingot")) return true;
    if (modules.getButton(scriptName, "Emerald") && isNamed(name, "emerald")) return true;
    if (modules.getButton(scriptName, "Diamond") && isNamed(name, "diamond")) return true;
    return false;
}

boolean isNamed(String name, String wanted) {
    return name.equals(wanted) || name.equals("minecraft:" + wanted) || name.endsWith(":" + wanted);
}

boolean hasAnyTarget() {
    int invSize = inventory.getSize() - 4;
    if (invSize > 36) invSize = 36;

    for (int i = 0; i < invSize; i++) {
        if (isTargetResource(inventory.getStackInSlot(i))) return true;
    }
    return false;
}

int countItem(String name) {
    int total = 0;
    int invSize = inventory.getSize() - 4;
    if (invSize > 36) invSize = 36;

    for (int i = 0; i < invSize; i++) {
        ItemStack s = inventory.getStackInSlot(i);
        if (s != null && s.name != null && isNamed(util.strip(s.name).toLowerCase(), name)) {
            total += s.stackSize;
        }
    }
    return total;
}

boolean isInVoid() {
    Entity player = client.getPlayer();
    if (player == null) return false;

    Vec3 pos = player.getPosition();
    int x = (int) Math.floor(pos.x);
    int y = (int) Math.floor(pos.y);
    int z = (int) Math.floor(pos.z);

    for (int checkY = y - 1; checkY >= 0; checkY--) {
        Block b = world.getBlockAt(x, checkY, z);
        if (!isAir(b)) return false;
    }

    return true;
}

boolean isClearVoidAroundPlayer() {
    Entity player = client.getPlayer();
    if (player == null) return false;

    Vec3 pos = player.getPosition();
    int px = (int) Math.floor(pos.x);
    int py = (int) Math.floor(pos.y);
    int pz = (int) Math.floor(pos.z);

    for (int x = px - safeBlockRadius; x <= px + safeBlockRadius; x++) {
        for (int z = pz - safeBlockRadius; z <= pz + safeBlockRadius; z++) {
            double dx = (x + 0.5) - pos.x;
            double dz = (z + 0.5) - pos.z;
            if (dx * dx + dz * dz > safeBlockRadius * safeBlockRadius) continue;

            for (int y = py - 1; y >= 0; y--) {
                if (!isAir(world.getBlockAt(x, y, z))) {
                    return false;
                }
            }
        }
    }

    return true;
}

boolean isAir(Block b) {
    if (b == null) return true;
    if (b.name != null && (b.name.equals("air") || b.name.equals("minecraft:air"))) return true;
    return b.type != null && b.type.toLowerCase().contains("air");
}

int toContainerSlot(int playerSlot) {
    return (playerSlot < 9) ? 36 + playerSlot : playerSlot;
}

int getBedwarsStatus() {
    List<String> sidebar = world.getScoreboard();
    if (sidebar == null || sidebar.size() < 7) return -1;

    if (!util.strip(sidebar.get(0)).startsWith("BED WARS")) return -1;

    if (util.strip(sidebar.get(5)).startsWith("R Red:") &&
        util.strip(sidebar.get(6)).startsWith("B Blue:")) {
        return 3;
    }

    String six = util.strip(sidebar.get(6));
    if (six.equals("Waiting...") || six.startsWith("Starting in")) {
        return 2;
    }

    return -1;
}
