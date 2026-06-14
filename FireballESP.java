String[] highlightedBlockWhitelist = {
    "diamond_block",
    "emerald_block"
};

HashSet<String> highlightedBlocks = new HashSet<>();
String selectedTheme = "white";
String selectedHighlightTheme = "white";
boolean shortenLine = true;
boolean highlightEntities = true;

String[] themeOptions = {
    "Default","Rainbow","Aurora","Cherry","Cotton Candy",
    "Flare","Flower","Forest","Frost","Gold",
    "Grayscale","Inferno","Royal","Sandstorm","Sky","Vine"
};

String[] settingsThemeMap = {
    "Rainbow","Aurora","Cherry","Cotton candy","Flare","Flower","Forest","Frost",
    "Gold","Grayscale","Inferno","Royal","Sandstorm","Sky","Vine"
};

void onLoad() {
    modules.registerDescription("Fireball trajectories.");
    modules.registerSlider("Theme", "", 0, themeOptions);
    modules.registerSlider("Highlight Theme", "", 0, themeOptions);
    modules.registerButton("Shorten Line",      true);
    modules.registerButton("Highlight Entity",  true);
    rebuildHighlightedBlocks();
}

void onEnable() {
    rebuildHighlightedBlocks();
    refreshSettings();
}

void onPreUpdate() {
    refreshSettings();
}

void refreshSettings() {
    selectedTheme = resolveTheme();
    selectedHighlightTheme = resolveHighlightTheme();
    shortenLine = modules.getButton(scriptName, "Shorten Line");
    highlightEntities = modules.getButton(scriptName, "Highlight Entity");
}

void rebuildHighlightedBlocks() {
    highlightedBlocks.clear();
    for (int i = 0; i < highlightedBlockWhitelist.length; i++) {
        String name = highlightedBlockWhitelist[i];
        if (name != null && !name.trim().isEmpty()) {
            highlightedBlocks.add(name.toLowerCase().trim());
        }
    }
}

void onRenderWorld(float partialTicks) {
    Entity player = client.getPlayer();
    if (player == null) return;

    ItemStack held = player.getHeldItem();
    if (held == null || !held.name.contains("fire_charge")) return;

    double range = 256.0;
    Vec3   cam   = render.getPosition();
    float aimYaw = player.getYaw();
    float aimPitch = player.getPitch();
    double yaw   = Math.toRadians(aimYaw);
    double pitch = Math.toRadians(aimPitch);
    double dirX  = -Math.sin(yaw) * Math.cos(pitch);
    double dirY  = -Math.sin(pitch);
    double dirZ  =  Math.cos(yaw) * Math.cos(pitch);

    double eyeX = cam.x;
    double eyeY = cam.y + player.getEyeHeight();
    double eyeZ = cam.z;

    double handX = eyeX - Math.cos(yaw) * 0.16;
    double handY = eyeY - 0.10;
    double handZ = eyeZ - Math.sin(yaw) * 0.16;

    Object[] blockHit = client.raycastBlock(range, aimYaw, aimPitch);

    double blockDist   = range;
    double entityDist  = range + 1.0;
    double blockWorldX = 0, blockWorldY = 0, blockWorldZ = 0;
    boolean blockWhitelisted = false;
    String blockSide = "UP";
    Vec3 hitBlockPosition = null;

    if (blockHit != null) {
        Vec3 bp     = ((Vec3) blockHit[0]).floor();
        Vec3 offset = (Vec3)  blockHit[1];
        hitBlockPosition = bp;
        blockSide = (String) blockHit[2];
        blockWhitelisted = isWhitelistedBlock(bp);
        blockWorldX = bp.x + offset.x;
        blockWorldY = bp.y + offset.y;
        blockWorldZ = bp.z + offset.z;

        double surfaceDistance = rayPlaneDistance(
            eyeX, eyeY, eyeZ,
            dirX, dirY, dirZ,
            blockWorldX, blockWorldY, blockWorldZ,
            blockSide
        );
        if (surfaceDistance >= 0.0 && surfaceDistance <= range) {
            blockDist = surfaceDistance;
            blockWorldX = eyeX + dirX * blockDist;
            blockWorldY = eyeY + dirY * blockDist;
            blockWorldZ = eyeZ + dirZ * blockDist;
        } else {
            double dx = blockWorldX - eyeX;
            double dy = blockWorldY - eyeY;
            double dz = blockWorldZ - eyeZ;
            blockDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    Object[] entityHit = client.raycastEntity(blockDist, aimYaw, aimPitch);
    Entity raycastEntity = null;
    if (entityHit != null) {
        raycastEntity = (Entity) entityHit[0];
        entityDist = rayEntityDistance(raycastEntity, partialTicks, eyeX, eyeY, eyeZ, dirX, dirY, dirZ);
        if (entityDist < 0.0) entityDist = Math.sqrt((double) entityHit[2]);
    }

    int    hitType = 0;
    double endX, endY, endZ;
    Entity hitEntity = null;

    if (raycastEntity != null && entityDist < blockDist) {
        hitType   = 1;
        hitEntity = raycastEntity;
        endX = eyeX + dirX * entityDist;
        endY = eyeY + dirY * entityDist;
        endZ = eyeZ + dirZ * entityDist;
    } else if (blockHit != null) {
        hitType = 2;
        endX = blockWorldX;
        endY = blockWorldY;
        endZ = blockWorldZ;
    } else {
        endX = eyeX + dirX * range;
        endY = eyeY + dirY * range;
        endZ = eyeZ + dirZ * range;
    }

    boolean highlightedTarget = hitType == 1 || (hitType == 2 && blockWhitelisted);
    int themeColor = getThemeColor(selectedTheme);
    int highlightColor = highlightedTarget ? getThemeColor(selectedHighlightTheme) : themeColor;
    int activeColor = highlightedTarget ? highlightColor : themeColor;
    float r = ((activeColor >> 16) & 0xFF) / 255.0f;
    float g = ((activeColor >> 8) & 0xFF) / 255.0f;
    float b = (activeColor & 0xFF) / 255.0f;

    float lineW = 2.0f;

    double lvX = endX - handX, lvY = endY - handY, lvZ = endZ - handZ;
    double ld  = Math.sqrt(lvX * lvX + lvY * lvY + lvZ * lvZ);
    double drawX = handX, drawY = handY, drawZ = handZ;
    if (shortenLine && ld > 0.5) {
        double t = 0.5 / ld;
        drawX = handX + lvX * t;
        drawY = handY + lvY * t;
        drawZ = handZ + lvZ * t;
    }

    beginWorldLine(lineW);
    gl.color(r, g, b, 1f);
    gl.begin(1);
    gl.vertex3(drawX - cam.x, drawY - cam.y, drawZ - cam.z);
    gl.vertex3(endX  - cam.x, endY  - cam.y, endZ  - cam.z);
    gl.end();
    endWorldLine();

    if (hitType == 1 && hitEntity != null && highlightEntities) {
        drawHighlightedEntity(hitEntity, partialTicks, highlightColor);
    }

    if (hitType > 0) {
        double lx, ly, lz;
        String landingSide = blockSide;
        if (hitType == 1 && hitEntity != null) {
            Vec3 ePos = hitEntity.getPosition();
            Vec3 eLast = hitEntity.getLastPosition();
            lx = eLast.x + (ePos.x - eLast.x) * partialTicks;
            ly = eLast.y + (ePos.y - eLast.y) * partialTicks + hitEntity.getHeight() * 0.5;
            lz = eLast.z + (ePos.z - eLast.z) * partialTicks;
            landingSide = "UP";
        } else {
            lx = endX;
            ly = endY;
            lz = endZ;
        }
        drawLanding(lx, ly, lz, landingSide, r, g, b, cam);

        if (hitType == 2 && blockWhitelisted && hitBlockPosition != null) {
            render.block(hitBlockPosition, withAlpha(highlightColor, 36), false, true);
            render.block(hitBlockPosition, withAlpha(highlightColor, 255), true, false);
        }
    }
}

boolean isWhitelistedBlock(Vec3 position) {
    Block block = world.getBlockAt((int) position.x, (int) position.y, (int) position.z);
    if (block == null || block.name == null) return false;

    String blockName = block.name.toLowerCase().trim();
    if (highlightedBlocks.contains(blockName)) return true;
    int namespace = blockName.indexOf(":");
    return namespace >= 0 && highlightedBlocks.contains(blockName.substring(namespace + 1));
}

double rayPlaneDistance(
    double eyeX, double eyeY, double eyeZ,
    double dirX, double dirY, double dirZ,
    double hitX, double hitY, double hitZ,
    String side
) {
    double denominator;
    double distance;

    if (side.equals("UP") || side.equals("DOWN")) {
        denominator = dirY;
        if (Math.abs(denominator) < 0.0000001) return -1.0;
        distance = (hitY - eyeY) / denominator;
    } else if (side.equals("NORTH") || side.equals("SOUTH")) {
        denominator = dirZ;
        if (Math.abs(denominator) < 0.0000001) return -1.0;
        distance = (hitZ - eyeZ) / denominator;
    } else {
        denominator = dirX;
        if (Math.abs(denominator) < 0.0000001) return -1.0;
        distance = (hitX - eyeX) / denominator;
    }

    return distance >= 0.0 ? distance : -1.0;
}

double rayEntityDistance(
    Entity entity, float partialTicks,
    double eyeX, double eyeY, double eyeZ,
    double dirX, double dirY, double dirZ
) {
    if (entity == null) return -1.0;

    Vec3 position = entity.getPosition();
    Vec3 previous = entity.getLastPosition();
    double x = previous.x + (position.x - previous.x) * partialTicks;
    double y = previous.y + (position.y - previous.y) * partialTicks;
    double z = previous.z + (position.z - previous.z) * partialTicks;
    double halfWidth = entity.getWidth() * 0.5;

    double minX = x - halfWidth;
    double minY = y;
    double minZ = z - halfWidth;
    double maxX = x + halfWidth;
    double maxY = y + entity.getHeight();
    double maxZ = z + halfWidth;
    double near = 0.0;
    double far = 256.0;

    if (Math.abs(dirX) < 0.0000001) {
        if (eyeX < minX || eyeX > maxX) return -1.0;
    } else {
        double first = (minX - eyeX) / dirX;
        double second = (maxX - eyeX) / dirX;
        if (first > second) { double swap = first; first = second; second = swap; }
        near = Math.max(near, first);
        far = Math.min(far, second);
        if (near > far) return -1.0;
    }

    if (Math.abs(dirY) < 0.0000001) {
        if (eyeY < minY || eyeY > maxY) return -1.0;
    } else {
        double first = (minY - eyeY) / dirY;
        double second = (maxY - eyeY) / dirY;
        if (first > second) { double swap = first; first = second; second = swap; }
        near = Math.max(near, first);
        far = Math.min(far, second);
        if (near > far) return -1.0;
    }

    if (Math.abs(dirZ) < 0.0000001) {
        if (eyeZ < minZ || eyeZ > maxZ) return -1.0;
    } else {
        double first = (minZ - eyeZ) / dirZ;
        double second = (maxZ - eyeZ) / dirZ;
        if (first > second) { double swap = first; first = second; second = swap; }
        near = Math.max(near, first);
        far = Math.min(far, second);
        if (near > far) return -1.0;
    }

    return near >= 0.0 ? near : far;
}

int clampInt(int value, int minimum, int maximum) {
    return value < minimum ? minimum : value > maximum ? maximum : value;
}

int lerpColor(int first, int second, double progress) {
    int red = clampInt((int) (((first >> 16) & 0xFF) + ((((second >> 16) & 0xFF) - ((first >> 16) & 0xFF)) * progress)), 0, 255);
    int green = clampInt((int) (((first >> 8) & 0xFF) + ((((second >> 8) & 0xFF) - ((first >> 8) & 0xFF)) * progress)), 0, 255);
    int blue = clampInt((int) ((first & 0xFF) + (((second & 0xFF) - (first & 0xFF)) * progress)), 0, 255);
    return 0xFF000000 | (red << 16) | (green << 8) | blue;
}

int getThemeColor(String name) {
    String lower = name.toLowerCase().trim();
    double millis = client.time();

    if (lower.equals("rainbow")) {
        double phase = millis / 420.0;
        return 0xFF000000
            | (clampInt((int) (128 + 127 * Math.sin(phase)), 0, 255) << 16)
            | (clampInt((int) (128 + 127 * Math.sin(phase + 2.094)), 0, 255) << 8)
            | clampInt((int) (128 + 127 * Math.sin(phase + 4.189)), 0, 255);
    }

    double pulse = (Math.sin(millis / 1200.0) + 1.0) / 2.0;
    if (lower.equals("aurora"))       return lerpColor(0xFF7301C2, 0xFF17F0B1, pulse);
    if (lower.equals("cherry"))       return lerpColor(0xFFDD3D69, 0xFFE0B3B7, pulse);
    if (lower.equals("cotton candy")) return lerpColor(0xFF92DAE8, 0xFFED68B8, pulse);
    if (lower.equals("flare"))        return lerpColor(0xFFF26B16, 0xFFE4A61D, pulse);
    if (lower.equals("flower"))       return lerpColor(0xFFC89AD8, 0xFFAC59B9, pulse);
    if (lower.equals("forest"))       return lerpColor(0xFF1F7617, 0xFF60A623, pulse);
    if (lower.equals("frost"))        return lerpColor(0xFFDFE3E3, 0xFFBCC5CA, pulse);
    if (lower.equals("gold"))         return lerpColor(0xFFE5DF30, 0xFFDADAB6, pulse);
    if (lower.equals("grayscale"))    return lerpColor(0xFF616368, 0xFFE7E8EA, pulse);
    if (lower.equals("inferno"))      return lerpColor(0xFF350000, 0xFFC03912, pulse);
    if (lower.equals("royal"))        return lerpColor(0xFF85BFE8, 0xFF1D3D87, pulse);
    if (lower.equals("sandstorm"))    return lerpColor(0xFF9D9369, 0xFFF5E3B4, pulse);
    if (lower.equals("sky"))          return lerpColor(0xFF81EAF8, 0xFF15BCD3, pulse);
    if (lower.equals("vine"))         return lerpColor(0xFF27E439, 0xFF9AF8A1, pulse);
    return 0xFFFFFFFF;
}

String resolveTheme() {
    int index = (int) modules.getSlider(scriptName, "Theme");
    if (index == 0) {
        try {
            int settingsIndex = (int) modules.getSlider("Settings", "Default theme");
            if (settingsIndex >= 0 && settingsIndex < settingsThemeMap.length) {
                return settingsThemeMap[settingsIndex];
            }
        } catch (Exception ignored) {}
        return "white";
    }
    if (index >= 1 && index < themeOptions.length) return themeOptions[index];
    return "white";
}

String resolveHighlightTheme() {
    int index = (int) modules.getSlider(scriptName, "Highlight Theme");
    if (index == 0) {
        try {
            int settingsIndex = (int) modules.getSlider("Settings", "Default theme");
            if (settingsIndex >= 0 && settingsIndex < settingsThemeMap.length) {
                return settingsThemeMap[settingsIndex];
            }
        } catch (Exception ignored) {}
        return "white";
    }
    if (index >= 1 && index < themeOptions.length) return themeOptions[index];
    return "white";
}

int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
}

void applyGlColor(int color) {
    float alpha = ((color >>> 24) & 0xFF) / 255.0f;
    float red = ((color >>> 16) & 0xFF) / 255.0f;
    float green = ((color >>> 8) & 0xFF) / 255.0f;
    float blue = (color & 0xFF) / 255.0f;
    gl.color(red, green, blue, alpha);
}

void vertexBoxFace(double x1, double y1, double z1, double x2, double y2, double z2, int face) {
    if (face == 0) {
        gl.vertex3(x1, y1, z1); gl.vertex3(x2, y1, z1);
        gl.vertex3(x2, y1, z2); gl.vertex3(x1, y1, z2);
    } else if (face == 1) {
        gl.vertex3(x1, y2, z1); gl.vertex3(x1, y2, z2);
        gl.vertex3(x2, y2, z2); gl.vertex3(x2, y2, z1);
    } else if (face == 2) {
        gl.vertex3(x1, y1, z1); gl.vertex3(x1, y2, z1);
        gl.vertex3(x2, y2, z1); gl.vertex3(x2, y1, z1);
    } else if (face == 3) {
        gl.vertex3(x1, y1, z2); gl.vertex3(x2, y1, z2);
        gl.vertex3(x2, y2, z2); gl.vertex3(x1, y2, z2);
    } else if (face == 4) {
        gl.vertex3(x1, y1, z1); gl.vertex3(x1, y1, z2);
        gl.vertex3(x1, y2, z2); gl.vertex3(x1, y2, z1);
    } else {
        gl.vertex3(x2, y1, z1); gl.vertex3(x2, y2, z1);
        gl.vertex3(x2, y2, z2); gl.vertex3(x2, y1, z2);
    }
}

void drawHighlightedEntity(Entity entity, float partialTicks, int color) {
    Vec3 position = entity.getPosition();
    Vec3 lastPosition = entity.getLastPosition();
    Vec3 camera = render.getPosition();

    double x = lastPosition.x + (position.x - lastPosition.x) * partialTicks;
    double y = lastPosition.y + (position.y - lastPosition.y) * partialTicks;
    double z = lastPosition.z + (position.z - lastPosition.z) * partialTicks;
    double halfWidth = entity.getWidth() / 2.0 + 0.11;
    double height = entity.getHeight() + 0.11;

    double x1 = x - halfWidth - camera.x;
    double y1 = y - 0.05 - camera.y;
    double z1 = z - halfWidth - camera.z;
    double x2 = x + halfWidth - camera.x;
    double y2 = y + height - camera.y;
    double z2 = z + halfWidth - camera.z;

    gl.push();
    gl.blend(true);
    gl.alpha(false);
    gl.texture2d(false);
    gl.lighting(false);
    gl.cull(false);
    gl.depth(false);
    gl.depthMask(false);

    applyGlColor(withAlpha(color, 36));
    gl.begin(7);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 0);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 1);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 2);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 3);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 4);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 5);
    gl.end();

    gl.lineSmooth(true);
    gl.lineWidth(2.0f);
    applyGlColor(withAlpha(color, 255));
    gl.begin(1);
    gl.vertex3(x1,y1,z1); gl.vertex3(x2,y1,z1);
    gl.vertex3(x2,y1,z1); gl.vertex3(x2,y1,z2);
    gl.vertex3(x2,y1,z2); gl.vertex3(x1,y1,z2);
    gl.vertex3(x1,y1,z2); gl.vertex3(x1,y1,z1);
    gl.vertex3(x1,y2,z1); gl.vertex3(x2,y2,z1);
    gl.vertex3(x2,y2,z1); gl.vertex3(x2,y2,z2);
    gl.vertex3(x2,y2,z2); gl.vertex3(x1,y2,z2);
    gl.vertex3(x1,y2,z2); gl.vertex3(x1,y2,z1);
    gl.vertex3(x1,y1,z1); gl.vertex3(x1,y2,z1);
    gl.vertex3(x2,y1,z1); gl.vertex3(x2,y2,z1);
    gl.vertex3(x2,y1,z2); gl.vertex3(x2,y2,z2);
    gl.vertex3(x1,y1,z2); gl.vertex3(x1,y2,z2);
    gl.end();

    gl.lineSmooth(false);
    gl.depthMask(true);
    gl.depth(true);
    gl.cull(true);
    gl.lighting(true);
    gl.texture2d(true);
    gl.alpha(true);
    gl.blend(false);
    gl.resetColor();
    gl.pop();
}

void beginWorldLine(float width) {
    gl.push();
    gl.blend(true);
    gl.alpha(false);
    gl.texture2d(false);
    gl.depth(false);
    gl.depthMask(false);
    gl.lineSmooth(true);
    gl.lineWidth(width);
}

void endWorldLine() {
    gl.lineSmooth(false);
    gl.resetColor();
    gl.depthMask(true);
    gl.depth(true);
    gl.texture2d(true);
    gl.alpha(true);
    gl.blend(false);
    gl.pop();
}

void drawLanding(double hx, double hy, double hz, String side, float r, float g, float b, Vec3 cam) {
    double size = 0.30;
    double offset = 0.02;
    double thickness = 0.015;
    double x1, y1, z1;
    double x2, y2, z2;

    if (side.equals("UP") || side.equals("DOWN")) {
        x1 = hx - cam.x - size;
        x2 = hx - cam.x + size;
        z1 = hz - cam.z - size;
        z2 = hz - cam.z + size;
        double surface = hy - cam.y + (side.equals("UP") ? offset : -offset);
        y1 = side.equals("UP") ? surface : surface - thickness;
        y2 = side.equals("UP") ? surface + thickness : surface;
    } else if (side.equals("NORTH") || side.equals("SOUTH")) {
        x1 = hx - cam.x - size;
        x2 = hx - cam.x + size;
        y1 = hy - cam.y - size;
        y2 = hy - cam.y + size;
        double surface = hz - cam.z + (side.equals("SOUTH") ? offset : -offset);
        z1 = side.equals("SOUTH") ? surface : surface - thickness;
        z2 = side.equals("SOUTH") ? surface + thickness : surface;
    } else {
        z1 = hz - cam.z - size;
        z2 = hz - cam.z + size;
        y1 = hy - cam.y - size;
        y2 = hy - cam.y + size;
        double surface = hx - cam.x + (side.equals("EAST") ? offset : -offset);
        x1 = side.equals("EAST") ? surface : surface - thickness;
        x2 = side.equals("EAST") ? surface + thickness : surface;
    }

    gl.push();
    gl.blend(true);
    gl.alpha(false);
    gl.texture2d(false);
    gl.depth(false);
    gl.depthMask(false);
    gl.cull(false);

    gl.color(r, g, b, 0.18f);
    gl.begin(7);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 0);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 1);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 2);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 3);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 4);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 5);
    gl.end();

    gl.lineSmooth(true);
    gl.lineWidth(2.0f);
    gl.color(r, g, b, 0.75f);
    gl.begin(1);
    gl.vertex3(x1,y1,z1); gl.vertex3(x2,y1,z1);
    gl.vertex3(x2,y1,z1); gl.vertex3(x2,y1,z2);
    gl.vertex3(x2,y1,z2); gl.vertex3(x1,y1,z2);
    gl.vertex3(x1,y1,z2); gl.vertex3(x1,y1,z1);
    gl.vertex3(x1,y2,z1); gl.vertex3(x2,y2,z1);
    gl.vertex3(x2,y2,z1); gl.vertex3(x2,y2,z2);
    gl.vertex3(x2,y2,z2); gl.vertex3(x1,y2,z2);
    gl.vertex3(x1,y2,z2); gl.vertex3(x1,y2,z1);
    gl.vertex3(x1,y1,z1); gl.vertex3(x1,y2,z1);
    gl.vertex3(x2,y1,z1); gl.vertex3(x2,y2,z1);
    gl.vertex3(x2,y1,z2); gl.vertex3(x2,y2,z2);
    gl.vertex3(x1,y1,z2); gl.vertex3(x1,y2,z2);
    gl.end();

    gl.cull(true);
    gl.lineSmooth(false);
    gl.depthMask(true);
    gl.depth(true);
    gl.texture2d(true);
    gl.alpha(true);
    gl.blend(false);
    gl.resetColor();
    gl.pop();
}
