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
    "Disabled","Always","5 ticks","10 ticks"
};

int defaultHudOffsetX = 0;
int defaultHudOffsetY = 14;

int hudOffsetX = 0;
int hudOffsetY = 14;

boolean dragging = false;
int dragOX = 0, dragOY = 0;
boolean prevLeft = false;

float fadeAlpha = 0.0f;
long lastRenderMs = 0L;
String lastCountStr = "64";

boolean resetPending = false;
long resetStartedMs = 0L;
long resetButtonDelayMs = 50L;

long rightHoldStartMs = 0L;
boolean prevRight = false;

void onLoad() {
    modules.registerSlider("Theme", "", 0, themeOptions);
    modules.registerSlider("Scale", "x", 1.0, 0.5, 3.0, 0.1);
    modules.registerSlider("Time multiplier", "x", 1.0, 0.1, 4.0, 0.1);
    modules.registerSlider("Only on RMB", "", 0, rmbOptions);
    modules.registerButton("Reset Position", false);
    modules.registerButton("Scaffold only", false);
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
    resetPending = false;
    resetStartedMs = 0L;
    rightHoldStartMs = 0L;
}

void onDisable() {
    savePos();
    dragging = false;
    resetPending = false;
    rightHoldStartMs = 0L;
    prevRight = false;
}

void onPreUpdate() {
    handleResetPositionButton();
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

void handleResetPositionButton() {
    boolean enabled = modules.getButton(scriptName, "Reset Position");

    if (!enabled) {
        resetPending = false;
        return;
    }

    if (!resetPending) {
        hudOffsetX = defaultHudOffsetX;
        hudOffsetY = defaultHudOffsetY;
        dragging = false;
        savePos();
        resetPending = true;
        resetStartedMs = client.time();
        return;
    }

    if (client.time() - resetStartedMs >= resetButtonDelayMs) {
        modules.setButton(scriptName, "Reset Position", false);
        resetPending = false;
    }
}

void savePos() {
    config.set("hudOffsetX", String.valueOf(hudOffsetX));
    config.set("hudOffsetY", String.valueOf(hudOffsetY));
}

void loadPos() {
    hudOffsetX = defaultHudOffsetX;
    hudOffsetY = defaultHudOffsetY;

    String sx = config.get("hudOffsetX");
    String sy = config.get("hudOffsetY");
    if (sx != null && !sx.isEmpty()) hudOffsetX = Integer.parseInt(sx);
    if (sy != null && !sy.isEmpty()) hudOffsetY = Integer.parseInt(sy);
}

int clampInt(int v, int lo, int hi) {
    return v < lo ? lo : v > hi ? hi : v;
}

float clampFloat(float v, float lo, float hi) {
    return v < lo ? lo : v > hi ? hi : v;
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
    double mult = modules.getSlider(scriptName, "Time multiplier");
    double ms = client.time() * mult;

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

    boolean targetVisible = showReal || chatOpen;
    if (otherScreenOpen) targetVisible = false;

    if (showReal) lastCountStr = String.valueOf(held.stackSize);
    else if (chatOpen) lastCountStr = "64";

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
            hudOffsetX = (mx - dragOX) - baseX;
            hudOffsetY = (my - dragOY) - baseY;
            x = baseX + hudOffsetX;
            y = baseY + hudOffsetY;
        }
    } else {
        if (dragging) savePos();
        dragging = false;
    }

    render.text(countStr, x, y, scale, numberColor, true);
    render.text(suffix, x + nw, y, scale, suffixColor, true);
}
