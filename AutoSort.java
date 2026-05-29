String[] slotOptions = {
    util.color("&cDisabled"),"1","2","3","4","5","6","7","8","9"
};
 
String[] managedNames = {
    "Sword","Wool","Gapple","Fireball","Pearl",
    "Bridge Egg","Water","Bow","Stick","Ladders"
};
 
boolean pending   = false;
boolean processed = false;
boolean sortDone  = false;
boolean stackDone = false;
 
long openedAt   = 0L;
long sortDoneAt = 0L;
int  openCycle  = 0;
 
String[] desiredSlots = new String[9];
 
void onLoad() {
    modules.registerDescription("0ms delay not recommended.");
    modules.registerButton("Only in Bed Wars", true);
 
    modules.registerDescription("AutoStack");
    modules.registerButton("AutoStack", false);
    modules.registerButton("BW items only", true);
    modules.registerButton("Stack hotbar", false);
    modules.registerSlider("Stack Delay", "ms", 75, 0, 1000, 25);
 
    modules.registerDescription("AutoSort");
    modules.registerButton("Override tools", false);
    modules.registerSlider("Sort Delay", "ms", 75, 0, 1000, 25);
 
    for (int i = 0; i < managedNames.length; i++)
        modules.registerSlider(managedNames[i], "", 0, slotOptions);
}
 
void onEnable()  { reset(); }
void onDisable() { reset(); }
 
void reset() {
    pending = processed = sortDone = stackDone = false;
    openedAt = sortDoneAt = 0L;
    for (int i = 0; i < 9; i++) desiredSlots[i] = null;
}
 
void onGuiUpdate(String name, boolean opened) {
    if (!name.equals("GuiInventory")) return;
    if (opened) {
        reset();
        pending  = true;
        openedAt = sortDoneAt = client.time();
        openCycle++;
        computeDesiredSlots();
    }
}
 
void onPreUpdate() {
    if (!pending || processed) return;
    if (!client.getScreen().contains("Inventory")) return;
 
    if (modules.getButton(scriptName, "Only in Bed Wars") && getBedwarsStatus() != 3) {
        processed = true;
        return;
    }
 
    long now = client.time();
 
    if (!sortDone) {
        if (now - openedAt >= (long) modules.getSlider(scriptName, "Sort Delay")) {
            runAutoSort();
            sortDone = true;
            sortDoneAt = now;
        }
        return;
    }
 
    if (!stackDone) {
        if (!modules.getButton(scriptName, "AutoStack")) {
            stackDone = processed = true;
        } else if (now - sortDoneAt >= (long) modules.getSlider(scriptName, "Stack Delay")) {
            runAutoStack();
            stackDone = processed = true;
        }
    }
}
 
void computeDesiredSlots() {
    for (int slot = 0; slot < 9; slot++) {
        List<String> cats = getCatsForSlot(slot);
        if (cats.isEmpty()) { desiredSlots[slot] = null; continue; }
        if (cats.size() == 1) { desiredSlots[slot] = cats.get(0); continue; }
 
        int base = (openCycle - 1) % cats.size();
        if (base < 0) base = 0;
 
        desiredSlots[slot] = null;
        for (int off = 0; off < cats.size(); off++) {
            String cat = cats.get((base + off) % cats.size());
            if (matchesCategory(inventory.getStackInSlot(slot), cat) || hasInInventory(cat, slot)) {
                desiredSlots[slot] = cat;
                break;
            }
        }
        if (desiredSlots[slot] == null) desiredSlots[slot] = cats.get(base);
    }
}
 
List<String> getCatsForSlot(int slot) {
    List<String> cats = new ArrayList<>();
    for (int i = 0; i < managedNames.length; i++) {
        int sel = (int) modules.getSlider(scriptName, managedNames[i]);
        if (sel > 0 && sel - 1 == slot) cats.add(managedNames[i]);
    }
    return cats;
}
 
boolean hasInInventory(String cat, int excludeSlot) {
    for (int s = 0; s < inventory.getSize(); s++) {
        if (s == excludeSlot) continue;
        if (matchesCategory(inventory.getStackInSlot(s), cat)) return true;
    }
    return false;
}
 
void runAutoStack() {
    boolean bwOnly = modules.getButton(scriptName, "BW items only");
 
    if (modules.getButton(scriptName, "Stack hotbar")) {
        stackHotbarDuplicates();
    }
 
    for (int hSlot = 0; hSlot < 9; hSlot++) {
        ItemStack hotbarItem = inventory.getStackInSlot(hSlot);
        if (!canBeStackTarget(hotbarItem, bwOnly)) continue;
 
        List<Integer> sources = new ArrayList<>();
        for (int iSlot = 9; iSlot < inventory.getSize(); iSlot++) {
            ItemStack src = inventory.getStackInSlot(iSlot);
            if (canMergeInto(hotbarItem, src, bwOnly)) sources.add(iSlot);
        }
 
        for (int a = 1; a < sources.size(); a++) {
            int key = sources.get(a);
            int keySize = inventory.getStackInSlot(key).stackSize;
            int b = a - 1;
            while (b >= 0 && inventory.getStackInSlot(sources.get(b)).stackSize < keySize) {
                sources.set(b + 1, sources.get(b));
                b--;
            }
            sources.set(b + 1, key);
        }
 
        for (int idx = 0; idx < sources.size(); idx++) {
            int iSlot = sources.get(idx);
            ItemStack refreshed = inventory.getStackInSlot(hSlot);
            if (!canBeStackTarget(refreshed, bwOnly)) break;
            inventory.click(toContainerSlot(iSlot), 0, 1);
        }
    }
}
 
void stackHotbarDuplicates() {
    boolean[] handled = new boolean[9];
 
    for (int hSlot = 0; hSlot < 9; hSlot++) {
        if (handled[hSlot]) continue;
 
        ItemStack base = inventory.getStackInSlot(hSlot);
        if (!isHotbarStackCandidate(base)) continue;
 
        List<Integer> group = new ArrayList<>();
        group.add(hSlot);
 
        for (int other = hSlot + 1; other < 9; other++) {
            ItemStack check = inventory.getStackInSlot(other);
            if (sameStackType(base, check)) group.add(other);
        }
 
        if (group.size() <= 1) {
            handled[hSlot] = true;
            continue;
        }
 
        int keepSlot = chooseHotbarKeepSlot(group);
        for (int i = 0; i < group.size(); i++) handled[group.get(i)] = true;
 
        for (int i = 0; i < group.size(); i++) {
            int slot = group.get(i);
            if (slot == keepSlot) continue;
            inventory.click(toContainerSlot(slot), 0, 1);
        }
    }
}
 
int chooseHotbarKeepSlot(List<Integer> group) {
    int keep = group.get(0);
    int bestSize = -1;
 
    for (int i = 0; i < group.size(); i++) {
        int slot = group.get(i);
        ItemStack s = inventory.getStackInSlot(slot);
        if (isSortedHotbarSlot(slot, s)) {
            if (s != null && s.stackSize > bestSize) {
                keep = slot;
                bestSize = s.stackSize;
            }
        }
    }
 
    if (bestSize >= 0) return keep;
 
    bestSize = -1;
    for (int i = 0; i < group.size(); i++) {
        int slot = group.get(i);
        ItemStack s = inventory.getStackInSlot(slot);
        int size = s == null ? 0 : s.stackSize;
        if (size > bestSize) {
            keep = slot;
            bestSize = size;
        }
    }
 
    return keep;
}
 
boolean isSortedHotbarSlot(int slot, ItemStack s) {
    if (slot < 0 || slot >= 9) return false;
    String wanted = desiredSlots[slot];
    if (wanted == null) return false;
    return matchesCategory(s, wanted);
}
 
boolean isHotbarStackCandidate(ItemStack s) {
    if (s == null || s.name == null) return false;
    return s.maxStackSize > 1;
}
 
boolean sameStackType(ItemStack a, ItemStack b) {
    if (a == null || b == null) return false;
    if (a.name == null || b.name == null) return false;
    if (a.maxStackSize <= 1 || b.maxStackSize <= 1) return false;
    if (!a.name.equals(b.name)) return false;
    return a.meta == b.meta;
}
 
boolean canBeStackTarget(ItemStack s, boolean bwOnly) {
    if (s == null || s.name == null)   return false;
    if (s.maxStackSize <= 1)           return false;
    if (s.stackSize >= s.maxStackSize) return false;
    if (bwOnly && !isBedWarsStackable(s)) return false;
    return true;
}
 
boolean canMergeInto(ItemStack target, ItemStack src, boolean bwOnly) {
    if (src == null || src.name == null)   return false;
    if (src.maxStackSize <= 1)             return false;
    if (!target.name.equals(src.name))     return false;
    if (target.meta != src.meta)           return false;
    if (bwOnly && !isBedWarsStackable(src)) return false;
    return true;
}
 
void runAutoSort() {
    boolean overrideTools = modules.getButton(scriptName, "Override tools");
 
    for (int hSlot = 0; hSlot < 9; hSlot++) {
        String cat = desiredSlots[hSlot];
        if (cat == null) continue;
 
        ItemStack current = inventory.getStackInSlot(hSlot);
 
        if (cat.equals("Sword")) {
            int bestSrc = findBestSword(hSlot);
            if (bestSrc == -1) continue;
            int curStr  = getSwordStrength(current);
            int bestStr = getSwordStrength(inventory.getStackInSlot(bestSrc));
            if (curStr >= bestStr) continue;
            if (!overrideTools && isProtectedTool(current)) continue;
            swapToHotbar(bestSrc, hSlot);
        } else {
            if (matchesCategory(current, cat)) continue;
            if (!overrideTools && isProtectedTool(current)) continue;
            int src = findBestSource(cat, hSlot);
            if (src == -1) continue;
            swapToHotbar(src, hSlot);
        }
    }
}

 
void swapToHotbar(int srcPlayerSlot, int targetHotbarIndex) {
    if (srcPlayerSlot == targetHotbarIndex && srcPlayerSlot < 9) return;
    inventory.click(toContainerSlot(srcPlayerSlot), targetHotbarIndex, 2);
}
 
int findBestSword(int excludeSlot) {
    int bestSlot = -1, bestStr = -1;
    for (int s = 0; s < inventory.getSize(); s++) {
        if (s == excludeSlot) continue;
        ItemStack stk = inventory.getStackInSlot(s);
        if (!isSword(stk)) continue;
        int str = getSwordStrength(stk);
        if (str > bestStr) { bestStr = str; bestSlot = s; }
    }
    return bestSlot;
}
 
int findBestSource(String cat, int excludeSlot) {
    int bestSlot = -1, bestPri = -1;
    for (int s = 0; s < inventory.getSize(); s++) {
        if (s == excludeSlot) continue;
        ItemStack stk = inventory.getStackInSlot(s);
        if (!matchesCategory(stk, cat)) continue;
        int pri = (s >= 9 ? 2000 : 1000) + stk.stackSize;
        if (pri > bestPri) { bestPri = pri; bestSlot = s; }
    }
    return bestSlot;
}
 
int toContainerSlot(int playerSlot) {
    return (playerSlot < 9) ? 36 + playerSlot : playerSlot;
}
 
boolean matchesCategory(ItemStack s, String cat) {
    if (s == null || s.name == null) return false;
    if (cat.equals("Sword"))      return isSword(s);
    if (cat.equals("Wool"))       return isWool(s);
    if (cat.equals("Gapple"))     return isGapple(s);
    if (cat.equals("Fireball"))   return isFireball(s);
    if (cat.equals("Pearl"))      return isPearl(s);
    if (cat.equals("Bridge Egg")) return isBridgeEgg(s);
    if (cat.equals("Water"))      return isWater(s);
    if (cat.equals("Bow"))        return isBow(s);
    if (cat.equals("Stick"))      return isStick(s);
    if (cat.equals("Ladders"))    return isLadder(s);
    return false;
}
 
boolean isSword(ItemStack s)     { return s != null && s.name != null && s.name.toLowerCase().contains("sword"); }
boolean isWool(ItemStack s)      { return s != null && s.name != null && s.name.toLowerCase().contains("wool"); }
 
boolean isProtectedTool(ItemStack s) {
    if (s == null || s.name == null) return false;
    String n = s.name.toLowerCase();
    return n.contains("pickaxe") || (n.contains("axe") && !n.contains("sword")) || n.contains("shears");
}
 
int getSwordStrength(ItemStack s) {
    if (!isSword(s)) return 0;
    String n = s.name.toLowerCase();
    if (n.contains("diamond")) return 4;
    if (n.contains("iron"))    return 3;
    if (n.contains("stone"))   return 2;
    if (n.contains("wood") || n.contains("wooden")) return 1;
    return 0;
}
 
boolean isGapple(ItemStack s)    { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("golden_apple")||n.equals("minecraft:golden_apple"); }
boolean isFireball(ItemStack s)  { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("fire_charge")||n.equals("minecraft:fire_charge"); }
boolean isPearl(ItemStack s)     { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("ender_pearl")||n.equals("minecraft:ender_pearl"); }
boolean isBridgeEgg(ItemStack s) { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("egg")||n.equals("minecraft:egg"); }
boolean isWater(ItemStack s)     { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("water_bucket")||n.equals("minecraft:water_bucket"); }
boolean isBow(ItemStack s)       { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("bow")||n.equals("minecraft:bow"); }
boolean isStick(ItemStack s)     { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("stick")||n.equals("minecraft:stick"); }
boolean isLadder(ItemStack s)    { if (s==null||s.name==null) return false; String n=s.name.toLowerCase(); return n.equals("ladder")||n.equals("minecraft:ladder"); }

 
boolean isBedWarsStackable(ItemStack s) {
    if (s == null || s.name == null) return false;
    String n = s.name.toLowerCase();
    if (n.contains("wool")) return true;
    for (int i = 0; i < bedWarsStackables.length; i++)
        if (n.equals(bedWarsStackables[i])) return true;
    return false;
}
 
String[] bedWarsStackables = {
    "wool","minecraft:wool",
    "stained_hardened_clay","minecraft:stained_hardened_clay",
    "end_stone","minecraft:end_stone",
    "wooden_slab","minecraft:wooden_slab",
    "planks","minecraft:planks",
    "ladder","minecraft:ladder",
    "obsidian","minecraft:obsidian",
    "sandstone","minecraft:sandstone",
    "tnt","minecraft:tnt",
    "golden_apple","minecraft:golden_apple",
    "fire_charge","minecraft:fire_charge",
    "ender_pearl","minecraft:ender_pearl",
    "egg","minecraft:egg",
    "snowball","minecraft:snowball",
    "arrow","minecraft:arrow",
    "potion","minecraft:potion"
};
 
int getBedwarsStatus() {
    List<String> sb = world.getScoreboard();
    if (sb == null || sb.size() < 7) return -1;
    if (!util.strip(sb.get(0)).startsWith("BED WARS")) return -1;
    String[] parts = util.strip(sb.get(1)).split("  ");
    if (parts.length < 2) return -1;
    String lobbyId = parts[1];
    if (lobbyId.endsWith("]")) lobbyId = lobbyId.split(" ")[0];
    if (lobbyId.startsWith("L")) return 1;
    if (util.strip(sb.get(5)).startsWith("R Red:")
     && util.strip(sb.get(6)).startsWith("B Blue:")) return 3;
    String six = util.strip(sb.get(6));
    if (six.equals("Waiting...") || six.startsWith("Starting in")) return 2;
    return -1;
}
