Map<Integer, Vec3> anchorPositions = new HashMap<Integer, Vec3>();
Map<Integer, Long> stuckSince = new HashMap<Integer, Long>();
Map<Integer, Boolean> alerted = new HashMap<Integer, Boolean>();
Map<String, Integer> teamColours = new HashMap<String, Integer>();
Map<Integer, String> beaconNames = new LinkedHashMap<Integer, String>();
Map<Integer, Integer> beaconColours = new LinkedHashMap<Integer, Integer>();
Map<Integer, Vec3> beaconPositions = new LinkedHashMap<Integer, Vec3>();
Map<Integer, Boolean> beaconMessagesSent = new LinkedHashMap<Integer, Boolean>();

long frozenTimeMs = 4500L;
double xzThreshold = 3.0;
double xzHardResetThreshold = 7.0;
double slowFallBlocksPerSecond = 2.25;

void onLoad() {
    modules.registerDescription("Draws beacon @ timered players.");
    modules.registerButton("Allow self", true);
    modules.registerButton("Ping", true);
    loadTeamColours();
}

void onEnable() {
    resetAll();
}

void onDisable() {
    resetAll();
}

void resetAll() {
    anchorPositions.clear();
    stuckSince.clear();
    alerted.clear();
    clearAllBeacons();
}

void loadTeamColours() {
    teamColours.put("0", new Color(0, 0, 0, 255).getRGB());
    teamColours.put("1", new Color(0, 0, 170, 255).getRGB());
    teamColours.put("2", new Color(0, 170, 0, 255).getRGB());
    teamColours.put("3", new Color(0, 170, 170, 255).getRGB());
    teamColours.put("4", new Color(170, 0, 0, 255).getRGB());
    teamColours.put("5", new Color(170, 0, 170, 255).getRGB());
    teamColours.put("6", new Color(255, 170, 0, 255).getRGB());
    teamColours.put("7", new Color(170, 170, 170, 255).getRGB());
    teamColours.put("8", new Color(85, 85, 85, 255).getRGB());
    teamColours.put("9", new Color(85, 85, 255, 255).getRGB());
    teamColours.put("a", new Color(85, 255, 85, 255).getRGB());
    teamColours.put("b", new Color(85, 255, 255, 255).getRGB());
    teamColours.put("c", new Color(255, 85, 85, 255).getRGB());
    teamColours.put("d", new Color(255, 85, 255, 255).getRGB());
    teamColours.put("e", new Color(255, 255, 85, 255).getRGB());
    teamColours.put("f", new Color(255, 255, 255, 255).getRGB());
}

void onPreUpdate() {
    if (getBedwarsStatus() != 3 || isClientSpectator()) {
        anchorPositions.clear();
        stuckSince.clear();
        alerted.clear();
        clearAllBeacons();
        return;
    }

    scanPlayers();
}

void scanPlayers() {
    Entity self = client.getPlayer();
    if (self == null) return;

    long now = client.time();
    HashSet<Integer> activeIds = new HashSet<Integer>();

    if (isRenderSelfEnabled()) {
        scanEntity(self, now, activeIds);
    } else {
        clearSelfTimerState(self);
    }

    for (Entity entity : world.getPlayerEntities()) {
        if (entity == null || isSelfEntity(entity)) continue;
        scanEntity(entity, now, activeIds);
    }

    pruneInactive(activeIds);
}

void scanEntity(Entity entity, long now, HashSet<Integer> activeIds) {
    if (isInvalidTarget(entity)) return;

    int id = entity.entityId;
    activeIds.add(id);

    Vec3 pos = entity.getPosition();
    if (pos == null) return;

    if (!isInVoid(pos) || isInWater(pos)) {
        resetTracking(id, pos);
        return;
    }

    if (!anchorPositions.containsKey(id)) {
        anchorPositions.put(id, pos);
        stuckSince.put(id, now);
        alerted.put(id, false);
        return;
    }

    Vec3 anchor = anchorPositions.get(id);
    long since = stuckSince.containsKey(id) ? stuckSince.get(id) : now;
    long trackedFor = now - since;
    double xzDistSq = anchor == null ? Double.MAX_VALUE : xzDistanceSq(anchor, pos);
    if (anchor == null || xzDistSq > xzHardResetThreshold * xzHardResetThreshold) {
        resetTracking(id, pos);
        return;
    }

    if (xzDistSq > xzThreshold * xzThreshold) {
        if (!isSlowVoidFall(anchor, pos, trackedFor)) {
            resetTracking(id, pos);
            return;
        }

        anchorPositions.put(id, new Vec3(pos.x, anchor.y, pos.z));
    }

    if (trackedFor >= frozenTimeMs && !alerted.getOrDefault(id, false)) {
        if (!isSlowVoidFall(anchorPositions.get(id), pos, trackedFor)) {
            resetTracking(id, pos);
            return;
        }

        if (notifyPotentialTimer(entity)) {
            alerted.put(id, true);
        }
    }
}

boolean isInvalidTarget(Entity entity) {
    try {
        if (entity.isDead()) return true;
    } catch (Exception ignored) {
    }

    try {
        if (entity.getHealth() <= 0.0f) return true;
    } catch (Exception ignored) {
    }

    return false;
}

void resetTracking(int id, Vec3 pos) {
    anchorPositions.put(id, pos);
    stuckSince.put(id, client.time());
    alerted.put(id, false);
}

void pruneInactive(HashSet<Integer> activeIds) {
    for (Iterator<Map.Entry<Integer, Vec3>> it = anchorPositions.entrySet().iterator(); it.hasNext();) {
        Map.Entry<Integer, Vec3> entry = it.next();
        int id = entry.getKey();
        if (!activeIds.contains(id)) {
            it.remove();
            stuckSince.remove(id);
            alerted.remove(id);
        }
    }
}

double xzDistanceSq(Vec3 a, Vec3 b) {
    double dx = a.x - b.x;
    double dz = a.z - b.z;
    return dx * dx + dz * dz;
}

boolean isSlowVoidFall(Vec3 anchor, Vec3 pos, long elapsedMs) {
    if (anchor == null || pos == null || elapsedMs < 750L) return true;

    double elapsedSeconds = Math.max(0.75, elapsedMs / 1000.0);
    double yDrop = Math.max(0.0, anchor.y - pos.y);
    return yDrop / elapsedSeconds <= slowFallBlocksPerSecond;
}

Vec3 getLockedBeaconPosition(int id, Vec3 fallback) {
    Vec3 anchor = anchorPositions.get(id);
    Vec3 source = anchor != null ? anchor : fallback;
    if (source == null) return null;

    double x = Math.floor(source.x) + 0.5;
    double y = Math.floor(source.y);
    double z = Math.floor(source.z) + 0.5;
    return new Vec3(x, y, z);
}

boolean notifyPotentialTimer(Entity entity) {
    if (isClientSpectator()) return false;
    if (isSelfEntity(entity) && !isRenderSelfEnabled()) return false;
    if (beaconNames.containsKey(entity.entityId)) return true;
    activateBeaconForEntity(entity);
    return true;
}

boolean isRenderSelfEnabled() {
    try {
        return modules.getButton(scriptName, "Allow self");
    } catch (Exception ignored) {
    }

    return false;
}

boolean isPingEnabled() {
    try {
        return modules.getButton(scriptName, "Ping");
    } catch (Exception ignored) {
    }

    return true;
}

boolean isClientSpectator() {
    Entity player = client.getPlayer();
    if (player == null) return true;

    try {
        if (player.isDead()) return true;
    } catch (Exception ignored) {
    }

    try {
        if (player.getHealth() <= 0.0f) return true;
    } catch (Exception ignored) {
    }

    try {
        if (client.allowFlying()) return true;
    } catch (Exception ignored) {
    }

    String screen = client.getScreen();
    return screen != null && screen.toLowerCase().contains("gameover");
}

void playAlertSound() {
    if (!isPingEnabled()) return;
    try {
        client.ping();
    } catch (Exception ignored) {
    }
}

boolean isSelfEntity(Entity entity) {
    Entity self = client.getPlayer();
    return self != null && entity != null && self.entityId == entity.entityId;
}

boolean isFriendlyEntity(Entity entity) {
    if (isSelfEntity(entity)) return true;

    String myPrefix = getOwnTeamPrefix();
    if (myPrefix == null || myPrefix.isEmpty()) return false;

    String display = getEntityDisplayName(entity);
    return display != null && display.startsWith(myPrefix);
}

String getOwnTeamPrefix() {
    Entity self = client.getPlayer();
    if (self == null) return "";

    String prefix = getTeamPrefixFromDisplay(getEntityDisplayName(self));
    if (!prefix.isEmpty()) return prefix;

    try {
        String myName = self.getName();
        for (NetworkPlayer player : world.getNetworkPlayers()) {
            if (player == null || !player.getName().equals(myName)) continue;
            prefix = getTeamPrefixFromDisplay(player.getDisplayName());
            if (!prefix.isEmpty()) return prefix;
        }
    } catch (Exception ignored) {
    }

    return "";
}

String getEntityDisplayName(Entity entity) {
    try {
        String display = entity.getDisplayName();
        if (display != null) return display;
    } catch (Exception ignored) {
    }

    return "";
}

String getTeamPrefixFromDisplay(String display) {
    if (display == null || display.isEmpty()) return "";

    for (String color : new String[]{"c", "9", "a", "e", "b", "f", "d", "8"}) {
        String prefix = util.color("&" + color);
        if (display.startsWith(prefix)) {
            return prefix;
        }
    }

    return "";
}

void clearSelfTimerState(Entity self) {
    if (self == null) return;

    int id = self.entityId;
    anchorPositions.remove(id);
    stuckSince.remove(id);
    alerted.remove(id);
    clearBeacon(id);
}

void onRenderTick(float partialTicks) {
    renderBeaconLabel(partialTicks);
}

void onRenderWorld(float partialTicks) {
    renderBeacon(partialTicks);
}

void activateBeaconForEntity(Entity entity) {
    int id = entity.entityId;
    beaconNames.put(id, getEntityName(entity));
    beaconColours.put(id, withAlpha(getPlayerColour(entity), 190));
    beaconPositions.put(id, null);
    beaconMessagesSent.put(id, false);
}

void clearBeacon(int id) {
    beaconNames.remove(id);
    beaconColours.remove(id);
    beaconPositions.remove(id);
    beaconMessagesSent.remove(id);
}

void clearAllBeacons() {
    beaconNames.clear();
    beaconColours.clear();
    beaconPositions.clear();
    beaconMessagesSent.clear();
}

void renderBeacon(float partialTicks) {
    if (beaconNames.isEmpty()) return;
    if (isClientSpectator()) {
        clearAllBeacons();
        return;
    }

    List<Integer> ids = new ArrayList<Integer>(beaconNames.keySet());
    for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        Entity entity = findEntityById(id);
        if (entity == null) {
            clearBeacon(id);
            continue;
        }

        Vec3 currentPos = entity.getPosition();
        if (currentPos == null || !isInVoid(currentPos) || isInWater(currentPos)) {
            clearBeacon(id);
            continue;
        }

        Vec3 pos = getLiveBeaconPosition(entity, partialTicks);
        if (pos == null) {
            clearBeacon(id);
            continue;
        }

        int colour = withAlpha(getPlayerColour(entity), 190);
        beaconPositions.put(id, pos);
        beaconNames.put(id, getEntityName(entity));
        beaconColours.put(id, colour);

        drawBeaconBeam(pos, colour);

        boolean messageSent = beaconMessagesSent.containsKey(id) && beaconMessagesSent.get(id);
        if (!messageSent) {
            beaconMessagesSent.put(id, true);
            printBeamRenderedMessage(entity, pos);
        }
    }
}

void renderBeaconLabel(float partialTicks) {
    if (beaconNames.isEmpty()) return;
    if (isClientSpectator()) return;

    List<Integer> ids = new ArrayList<Integer>(beaconNames.keySet());
    for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        Entity entity = findEntityById(id);
        if (entity == null) continue;

        Vec3 currentPos = entity.getPosition();
        if (currentPos == null || !isInVoid(currentPos) || isInWater(currentPos)) continue;

        Vec3 pos = getLiveBeaconPosition(entity, partialTicks);
        if (pos == null) continue;

        int[] display = client.getDisplaySize();
        int guiScale = display[2];
        Vec3 label = render.worldToScreen(pos.x, pos.y + getEntityHeight(entity) + 8.0, pos.z, guiScale, partialTicks);

        if (label == null || label.z < 0 || label.z >= 1) continue;

        int colour = beaconColours.containsKey(id) ? beaconColours.get(id) : 0xAAFFFFFF;
        int coreColour = withAlpha(colour, 210);
        String name = beaconNames.containsKey(id) ? beaconNames.get(id) : "player";

        String prefix = "Timer: ";
        float labelScale = getBeaconLabelScale(pos);
        float prefixWidth = plainTextWidth(prefix) * labelScale;
        float nameWidth = plainTextWidth(name) * labelScale;
        float x = (float) label.x - (prefixWidth + nameWidth) / 2.0f;
        int outlineColor = isFriendlyEntity(entity) ? 0xFF22D66B : 0xFFFF4444;
        drawBeaconLabelBox(prefix, name, x, (float) label.y - 18.0f, labelScale, 0xFFFFFFFF, coreColour, outlineColor);
    }
}

float getBeaconLabelScale(Vec3 pos) {
    Entity player = client.getPlayer();
    if (player == null || pos == null) return 0.72f;

    Vec3 playerPos = player.getPosition();
    if (playerPos == null) return 0.72f;

    double dx = playerPos.x - pos.x;
    double dy = playerPos.y - pos.y;
    double dz = playerPos.z - pos.z;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

    return clamp((float) (18.0 / Math.max(20.0, distance)), 0.45f, 0.72f);
}

void printBeamRenderedMessage(Entity entity, Vec3 pos) {
    playAlertSound();

    String cs = util.colorSymbol;
    String playerName = formatPlayerNameForChat(entity);
    String coords = formatCoords(pos);
    client.print(cs + "7[" + cs + "dR" + cs + "7] " + playerName + cs + "7 detected for " + cs + "bTimer " + cs + "7(" + cs + "2" + coords + cs + "7)");
}

void drawBeaconLabelBox(String prefix, String name, float x, float y, float scale, int prefixColor, int nameColor, int outlineColor) {
    float prefixWidth = plainTextWidth(prefix) * scale;
    float width = prefixWidth + plainTextWidth(name) * scale;
    float height = render.getFontHeight() * scale;
    float padX = 3.5f * scale;
    float padY = 2.0f * scale;

    float x1 = x - padX;
    float y1 = y - padY;
    float x2 = x + width + padX;
    float y2 = y + height + padY;
    drawRectOutline(x1, y1, x2, y2, 1.0f, withAlpha(outlineColor, 230));
    drawBeaconLabelText(prefix, name, x, y, scale, prefixColor, nameColor, true, prefixWidth);
}

void drawRectOutline(float x1, float y1, float x2, float y2, float thickness, int color) {
    render.rect(x1, y1, x2, y1 + thickness, color);
    render.rect(x1, y2 - thickness, x2, y2, color);
    render.rect(x1, y1, x1 + thickness, y2, color);
    render.rect(x2 - thickness, y1, x2, y2, color);
}

void drawBeaconLabelText(String prefix, String name, float x, float y, float scale, int prefixColor, int nameColor, boolean shadow, float prefixWidth) {
    render.text(plainText(prefix), x, y, scale, prefixColor, shadow);
    render.text(plainText(name), x + prefixWidth, y, scale, nameColor, shadow);
}

String formatCoords(Vec3 pos) {
    int x = (int) Math.floor(pos.x);
    int y = (int) Math.floor(pos.y);
    int z = (int) Math.floor(pos.z);
    return x + ", " + y + ", " + z;
}

String formatPlayerNameForChat(Entity entity) {
    String name = getEntityName(entity);
    if (name == null || name.length() == 0) {
        name = "player";
    }

    String cs = util.colorSymbol;
    String code = getPlayerColourCode(entity);
    String first = name.substring(0, 1);
    String rest = name.length() > 1 ? name.substring(1) : "";
    return cs + code + cs + "l" + first + cs + "r" + cs + code + rest;
}

void drawBeaconBeam(Vec3 pos, int colour) {
    render.drawBeam(new Vec3(pos.x - 0.5, pos.y, pos.z - 0.5), 128.0, 0.10, withAlpha(colour, 85), 0.4f);
}

Vec3 getLiveBeaconPosition(Entity entity, float partialTicks) {
    return getInterpolatedPosition(entity, partialTicks);
}

double getEntityHeight(Entity entity) {
    try {
        return entity.getHeight();
    } catch (Exception ignored) {
    }

    return 2.0;
}

boolean isOnScreen(Vec3 point) {
    return point != null && point.z >= 0 && point.z < 1;
}

Entity findEntityById(int id) {
    Entity self = client.getPlayer();
    if (self != null && self.entityId == id) return self;

    for (Entity entity : world.getPlayerEntities()) {
        if (entity != null && entity.entityId == id) return entity;
    }

    return null;
}

Vec3 getInterpolatedPosition(Entity entity, float partialTicks) {
    Vec3 current = entity.getPosition();
    Vec3 last = entity.getLastPosition();
    if (current == null) return null;
    if (last == null) return current;

    double x = interpolate(current.x, last.x, partialTicks);
    double y = interpolate(current.y, last.y, partialTicks);
    double z = interpolate(current.z, last.z, partialTicks);
    return new Vec3(x, y, z);
}

boolean isInVoid(Vec3 pos) {
    int y = (int) Math.floor(pos.y);
    double radius = 0.42;
    double[] xs = new double[] {pos.x, pos.x - radius, pos.x + radius};
    double[] zs = new double[] {pos.z, pos.z - radius, pos.z + radius};

    for (double sampleX : xs) {
        for (double sampleZ : zs) {
            int x = (int) Math.floor(sampleX);
            int z = (int) Math.floor(sampleZ);

            for (int checkY = y - 1; checkY >= 0; checkY--) {
                if (!isAir(world.getBlockAt(x, checkY, z))) return false;
            }
        }
    }

    return true;
}

boolean isInWater(Vec3 pos) {
    Block block = world.getBlockAt((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z));
    if (block == null) return false;

    String name = block.name == null ? "" : block.name.toLowerCase();
    String type = block.type == null ? "" : block.type.toLowerCase();
    return name.contains("water") || type.contains("water");
}

boolean isAir(Block block) {
    if (block == null) return true;
    if (block.name != null && (block.name.equals("air") || block.name.equals("minecraft:air"))) return true;
    return block.type != null && block.type.toLowerCase().contains("air");
}

int getPlayerColour(Entity entity) {
    try {
        String name = entity.getDisplayName();
        String symbol = util.colorSymbol;
        if (name != null && symbol != null && !symbol.isEmpty()) {
            for (int i = 0; i < name.length() - 1; i++) {
                if (name.substring(i, i + 1).equals(symbol)) {
                    String code = String.valueOf(name.charAt(i + 1)).toLowerCase();
                    if (teamColours.containsKey(code)) return teamColours.get(code);
                }
            }
        }
    } catch (Exception ignored) {
    }

    return new Color(255, 255, 255, 255).getRGB();
}

String getPlayerColourCode(Entity entity) {
    try {
        String name = entity.getDisplayName();
        String symbol = util.colorSymbol;
        if (name != null && symbol != null && !symbol.isEmpty()) {
            for (int i = 0; i < name.length() - 1; i++) {
                if (name.substring(i, i + 1).equals(symbol)) {
                    String code = String.valueOf(name.charAt(i + 1)).toLowerCase();
                    if (teamColours.containsKey(code)) return code;
                }
            }
        }
    } catch (Exception ignored) {
    }

    return "f";
}

int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
}

String plainText(String text) {
    return text == null ? "" : text;
}

float plainTextWidth(String text) {
    return render.getFontWidth(plainText(text));
}

float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
}

String getEntityName(Entity entity) {
    try {
        String name = entity.getDisplayName();
        if (name != null && !name.isEmpty()) return util.strip(name);
    } catch (Exception ignored) {
    }

    try {
        NetworkPlayer networkPlayer = entity.getNetworkPlayer();
        if (networkPlayer != null) {
            String name = networkPlayer.getName();
            if (name != null && !name.isEmpty()) return util.strip(name);
        }
    } catch (Exception ignored) {
    }

    return "player";
}

int getBedwarsStatus() {
    List<String> sidebar = world.getScoreboard();
    if (sidebar == null || sidebar.size() == 0) return -1;

    if (!util.strip(sidebar.get(0)).startsWith("BED WARS")) return -1;

    for (String line : sidebar) {
        String clean = util.strip(line);
        if (clean.equals("Waiting...") || clean.startsWith("Starting in")) {
            return 2;
        }
    }

    return 3;
}

double interpolate(double current, double old, float scale) {
    return old + (current - old) * scale;
}
