String[] themeOptions = {
    "Default","Rainbow","Aurora","Cherry","Cotton Candy",
    "Flare","Flower","Forest","Frost","Gold",
    "Grayscale","Inferno","Royal","Sandstorm","Sky","Vine"
};

String[] settingsThemeMap = {
    "Rainbow","Aurora","Cherry","Cotton candy","Flare","Flower","Forest","Frost",
    "Gold","Grayscale","Inferno","Royal","Sandstorm","Sky","Vine"
};

String[] rmbOptions = {
    util.color("&cDisabled"),"Always","5 tick delay","10 tick delay"
};

int defaultHudOffsetX = 0;
int defaultHudOffsetY = 14;

int hudOffsetX = 0;
int hudOffsetY = 14;
boolean positionLoaded = false;
boolean positionDirty = false;

boolean dragging = false;
int dragOX = 0, dragOY = 0;
boolean prevLeft = false;

float fadeAlpha = 0.0f;
long lastRenderMs = 0L;
String lastCountStr = "64";

long rightHoldStartMs = 0L;
boolean prevRight = false;

boolean lastScaffoldState = false;
boolean wasShowingReal = false;
long switchFadeUntil = 0L;
long switchFadeMs = 110L;

void onLoad() {
    modules.registerDescription("Displays block count.");
    modules.registerButton("Scaffold only", false);
    modules.registerSlider("Theme", "", 0, themeOptions);
    modules.registerSlider("Scale", "x", 1.0, 0.5, 3.0, 0.1);
    modules.registerSlider("Only on RMB", "", 0, rmbOptions);
    modules.registerSlider("Grid", "px", 10, 0, 50, 1);
    loadPos();
}

void onEnable() {
    loadPos();
    dragging = false;
    prevLeft = false;
    prevRight = false;
    fadeAlpha = 0.0f;
    lastRenderMs = 0L;
    lastCountStr = "64";
    rightHoldStartMs = 0L;
    lastScaffoldState = false;
    wasShowingReal = false;
    switchFadeUntil = 0L;
}

void onDisable() {
    if (dragging || positionDirty) savePos();
    dragging = false;
    rightHoldStartMs = 0L;
    prevRight = false;
    lastScaffoldState = false;
    wasShowingReal = false;
    switchFadeUntil = 0L;
}

void onPreUpdate() {
    updateRightHoldState();
}

void updateRightHoldState() {
    boolean rightDown = keybinds.isMouseDown(1);
    long now = client.time();

    if (rightDown) {
        if (!prevRight) {
            rightHoldStartMs = now;
        }
    } else {
        rightHoldStartMs = 0L;
    }

    prevRight = rightDown;
}

void savePos() {
    boolean savedX = config.set("hudOffsetX", String.valueOf(hudOffsetX));
    boolean savedY = config.set("hudOffsetY", String.valueOf(hudOffsetY));
    if (savedX && savedY) positionDirty = false;
}

void loadPos() {
    int loadedX = positionLoaded ? hudOffsetX : defaultHudOffsetX;
    int loadedY = positionLoaded ? hudOffsetY : defaultHudOffsetY;

    String sx = config.get("hudOffsetX");
    String sy = config.get("hudOffsetY");
    if (sx != null && !sx.trim().isEmpty()) loadedX = parseIntOr(sx, loadedX);
    if (sy != null && !sy.trim().isEmpty()) loadedY = parseIntOr(sy, loadedY);

    hudOffsetX = loadedX;
    hudOffsetY = loadedY;
    positionLoaded = true;
    positionDirty = false;
}

int parseIntOr(String value, int fallback) {
    try {
        return Integer.parseInt(value.trim());
    } catch (Exception ignored) {
    }

    return fallback;
}

int clampInt(int v, int lo, int hi) {
    return v < lo ? lo : v > hi ? hi : v;
}

float clampFloat(float v, float lo, float hi) {
    return v < lo ? lo : v > hi ? hi : v;
}

float snapToGrid(float value) {
    float grid = (float) modules.getSlider(scriptName, "Grid");
    if (grid <= 0.0f) return value;
    if (grid < 1.0f) grid = 1.0f;
    return Math.round(value / grid) * grid;
}

void drawHudGrid(int[] display, float x, float y, float width, float height) {
    float grid = (float) modules.getSlider(scriptName, "Grid");
    if (grid <= 0.0f) return;
    if (grid < 1.0f) grid = 1.0f;

    int gridColor = 0x55FFFFFF;
    float sw = display[0];
    float sh = display[1];
    for (float gx = 0.0f; gx <= sw; gx += grid) render.rect(gx, 0.0f, gx + 0.5f, sh, gridColor);
    for (float gy = 0.0f; gy <= sh; gy += grid) render.rect(0.0f, gy, sw, gy + 0.5f, gridColor);

    int centerColor = 0xFFFF3333;
    float screenCenterX = sw / 2.0f;
    float screenCenterY = sh / 2.0f;
    float boxCenterX = x + width / 2.0f;
    float boxCenterY = y + height / 2.0f;
    if (Math.abs(boxCenterX - screenCenterX) <= 0.5f) render.rect(screenCenterX - 0.5f, 0.0f, screenCenterX + 0.5f, sh, centerColor);
    if (Math.abs(boxCenterY - screenCenterY) <= 0.5f) render.rect(0.0f, screenCenterY - 0.5f, sw, screenCenterY + 0.5f, centerColor);
}

int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
}

int lerpColor(int c1, int c2, double t) {
    int r = clampInt((int)(((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t)), 0, 255);
    int g = clampInt((int)(((c1 >> 8)  & 0xFF) + ((((c2 >> 8)  & 0xFF) - ((c1 >> 8)  & 0xFF)) * t)), 0, 255);
    int b = clampInt((int)(((c1)       & 0xFF) + ((((c2)       & 0xFF) - ((c1)       & 0xFF)) * t)), 0, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
}

int getThemeColor(String name) {
    String lo = name.toLowerCase().trim();
    double ms = client.time();

    if (lo.equals("rainbow")) {
        double t = ms / 420.0;
        return 0xFF000000
            | (clampInt((int)(128 + 127 * Math.sin(t)),         0, 255) << 16)
            | (clampInt((int)(128 + 127 * Math.sin(t + 2.094)), 0, 255) << 8)
            |  clampInt((int)(128 + 127 * Math.sin(t + 4.189)), 0, 255);
    }

    double p = (Math.sin(ms / 1200.0) + 1.0) / 2.0;
    if (lo.equals("aurora"))       return lerpColor(0xFF7301C2, 0xFF17F0B1, p);
    if (lo.equals("cherry"))       return lerpColor(0xFFDD3D69, 0xFFE0B3B7, p);
    if (lo.equals("cotton candy")) return lerpColor(0xFF92DAE8, 0xFFED68B8, p);
    if (lo.equals("flare"))        return lerpColor(0xFFF26B16, 0xFFE4A61D, p);
    if (lo.equals("flower"))       return lerpColor(0xFFC89AD8, 0xFFAC59B9, p);
    if (lo.equals("forest"))       return lerpColor(0xFF1F7617, 0xFF60A623, p);
    if (lo.equals("frost"))        return lerpColor(0xFFDFE3E3, 0xFFBCC5CA, p);
    if (lo.equals("gold"))         return lerpColor(0xFFE5DF30, 0xFFDADAB6, p);
    if (lo.equals("grayscale"))    return lerpColor(0xFF616368, 0xFFE7E8EA, p);
    if (lo.equals("inferno"))      return lerpColor(0xFF350000, 0xFFC03912, p);
    if (lo.equals("royal"))        return lerpColor(0xFF85BFE8, 0xFF1D3D87, p);
    if (lo.equals("sandstorm"))    return lerpColor(0xFF9D9369, 0xFFF5E3B4, p);
    if (lo.equals("sky"))          return lerpColor(0xFF81EAF8, 0xFF15BCD3, p);
    if (lo.equals("vine"))         return lerpColor(0xFF27E439, 0xFF9AF8A1, p);
    return 0xFFFFFFFF;
}

String resolveTheme() {
    int idx = (int) modules.getSlider(scriptName, "Theme");
    if (idx == 0) {
        try {
            int si = (int) modules.getSlider("Settings", "Default theme");
            if (si >= 0 && si < settingsThemeMap.length) return settingsThemeMap[si];
        } catch (Exception ex) {}
        return "white";
    }
    if (idx >= 1 && idx < themeOptions.length) return themeOptions[idx];
    return "white";
}

boolean passesRmbMode() {
    int mode = (int) modules.getSlider(scriptName, "Only on RMB");
    if (mode == 0) return true;

    if (!keybinds.isMouseDown(1)) return false;
    if (mode == 1) return true;
    if (rightHoldStartMs == 0L) return false;

    long heldMs = client.time() - rightHoldStartMs;
    if (mode == 2) return heldMs >= 5L * 50L;
    if (mode == 3) return heldMs >= 10L * 50L;

    return true;
}

boolean passesScaffoldOnly() {
    try {
        return !modules.getButton(scriptName, "Scaffold only") || modules.isEnabled("Scaffold");
    } catch (Exception ignored) {}
    return true;
}

boolean isScaffoldEnabled() {
    try {
        return modules.isEnabled("Scaffold");
    } catch (Exception ignored) {}
    return false;
}

int getHotbarBlockTotal() {
    int total = 0;
    for (int slot = 0; slot < 9; slot++) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack == null) continue;
        if (!stack.isBlock) continue;
        if (stack.stackSize <= 0) continue;
        total += stack.stackSize;
    }
    return total;
}

void updateFade(boolean targetVisible) {
    long now = client.time();
    if (lastRenderMs == 0L) lastRenderMs = now;

    long dt = now - lastRenderMs;
    lastRenderMs = now;
    if (dt < 0L) dt = 0L;
    if (dt > 100L) dt = 100L;

    float fadeSpeedPerMs = 1.0f / 100.0f;

    if (targetVisible) fadeAlpha += dt * fadeSpeedPerMs;
    else fadeAlpha -= dt * fadeSpeedPerMs;

    fadeAlpha = clampFloat(fadeAlpha, 0.0f, 1.0f);
}

void onRenderTick(float partialTicks) {
    if (client.getPlayer() == null) return;

    String scr = client.getScreen();
    String scrLo = scr == null ? "" : scr.toLowerCase();
    boolean chatOpen = scrLo.contains("chat");
    boolean otherScreenOpen = scr != null && !scr.isEmpty() && !chatOpen;

    ItemStack held = client.getPlayer().getHeldItem();
    boolean showReal = client.getPlayer().isHoldingBlock()
                    && held != null
                    && held.name != null
                    && held.maxStackSize > 1
                    && held.stackSize > 0
                    && passesScaffoldOnly()
                    && passesRmbMode();

    long now = client.time();
    boolean scaffoldEnabled = isScaffoldEnabled();
    if (showReal && wasShowingReal && scaffoldEnabled != lastScaffoldState) {
        switchFadeUntil = now + switchFadeMs;
    }
    lastScaffoldState = scaffoldEnabled;
    wasShowingReal = showReal;
    boolean inSwitchFade = now < switchFadeUntil;

    boolean targetVisible = showReal || chatOpen;
    if (otherScreenOpen) targetVisible = false;
    if (inSwitchFade) targetVisible = false;

    if (showReal && !inSwitchFade) {
        int displayCount = isScaffoldEnabled() ? getHotbarBlockTotal() : held.stackSize;
        lastCountStr = String.valueOf(displayCount);
    } else if (chatOpen) {
        lastCountStr = "64";
    }

    updateFade(targetVisible);

    if (otherScreenOpen) {
        if (dragging) savePos();
        dragging = false;
    }

    if (fadeAlpha <= 0.0f) return;

    float scale = (float) modules.getSlider(scriptName, "Scale");

    String countStr = lastCountStr;
    String suffix = " blocks";

    int baseNumberColor = getThemeColor(resolveTheme());
    int baseSuffixColor = 0xFFFFFFFF;

    int alpha = clampInt((int)(fadeAlpha * 255.0f), 0, 255);
    int numberColor = withAlpha(baseNumberColor, alpha);
    int suffixColor = withAlpha(baseSuffixColor, alpha);

    int[] display = client.getDisplaySize();
    int screenW = display[0];
    int screenH = display[1];

    int[] mp = keybinds.getMousePosition();
    int mx = mp[0] / display[2];
    int my = screenH - (mp[1] / display[2]);

    boolean leftDown = keybinds.isMouseDown(0);
    boolean leftJust = leftDown && !prevLeft;
    prevLeft = leftDown;

    int nw = (int)(render.getFontWidth(countStr) * scale);
    int sw = (int)(render.getFontWidth(suffix) * scale);
    int totalW = nw + sw;
    int th = (int)(render.getFontHeight() * scale);

    int baseX = (screenW / 2) - (totalW / 2);
    int baseY = (screenH / 2);

    int x = baseX + hudOffsetX;
    int y = baseY + hudOffsetY;

    if (chatOpen) {
        if (leftJust
            && mx >= x && mx <= x + totalW
            && my >= y && my <= y + th) {
            dragging = true;
            dragOX = mx - x;
            dragOY = my - y;
        }

        if (!leftDown) {
            if (dragging) savePos();
            dragging = false;
        }

        if (dragging) {
            float rawX = clampFloat(mx - dragOX, 2.0f, Math.max(2.0f, screenW - totalW - 2.0f));
            float rawY = clampFloat(my - dragOY, 2.0f, Math.max(2.0f, screenH - th - 2.0f));
            float snappedX = snapToGrid(rawX);
            float snappedY = snapToGrid(rawY);

            float grid = (float) modules.getSlider(scriptName, "Grid");
            if (grid > 0.0f) {
                float centerSnap = Math.max(4.0f, grid);
                float screenCenterX = screenW / 2.0f;
                float screenCenterY = screenH / 2.0f;
                float boxCenterX = snappedX + totalW / 2.0f;
                float boxCenterY = snappedY + th / 2.0f;
                if (Math.abs(boxCenterX - screenCenterX) <= centerSnap) snappedX = screenCenterX - totalW / 2.0f;
                if (Math.abs(boxCenterY - screenCenterY) <= centerSnap) snappedY = screenCenterY - th / 2.0f;
            }

            x = (int) clampFloat(snappedX, 2.0f, Math.max(2.0f, screenW - totalW - 2.0f));
            y = (int) clampFloat(snappedY, 2.0f, Math.max(2.0f, screenH - th - 2.0f));
            hudOffsetX = x - baseX;
            hudOffsetY = y - baseY;
            positionLoaded = true;
            positionDirty = true;
        }
    } else {
        if (dragging) savePos();
        dragging = false;
    }

    if (dragging) drawHudGrid(display, x, y, totalW, th);

    render.text(countStr, x, y, scale, numberColor, true);
    render.text(suffix, x + nw, y, scale, suffixColor, true);
}
