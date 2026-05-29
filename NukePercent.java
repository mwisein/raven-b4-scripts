int defaultHudOffsetX = 0;
int defaultHudOffsetY = 12;

int hudOffsetX = 0;
int hudOffsetY = 12;

boolean dragging = false;
int dragOX = 0, dragOY = 0;
boolean prevLeft = false;

float fadeAlpha = 0.0f;
long lastRenderMs = 0L;
float displayProgress = 0.0f;
float smoothProgress = 0.0f;
float progressVelocityPerMs = 0.0f;
long lastSmoothMs = 0L;
String lastPctStr = "50%";

boolean resetPending = false;
long resetStartedMs = 0L;
long resetButtonDelayMs = 50L;

void onLoad() {
    modules.registerDescription("Shows BedAura break progress.");
    modules.registerSlider("Scale", "x", 1.0, 0.5, 3.0, 0.1);
    modules.registerButton("Start with {f}", true);
    modules.registerButton("Reset Position", false);
    loadPos();
}

void onEnable() {
    loadPos();
    dragging = false;
    prevLeft = false;
    fadeAlpha = 0.0f;
    lastRenderMs = 0L;
    displayProgress = 0.0f;
    smoothProgress = 0.0f;
    progressVelocityPerMs = 0.0f;
    lastSmoothMs = 0L;
    lastPctStr = "50%";
    resetPending = false;
    resetStartedMs = 0L;
}

void onDisable() {
    savePos();
    dragging = false;
    resetPending = false;
}

void onPreUpdate() {
    handleResetPositionButton();
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
    config.set("nukePercentOffsetX", String.valueOf(hudOffsetX));
    config.set("nukePercentOffsetY", String.valueOf(hudOffsetY));
}

void loadPos() {
    hudOffsetX = defaultHudOffsetX;
    hudOffsetY = defaultHudOffsetY;

    String sx = config.get("nukePercentOffsetX");
    String sy = config.get("nukePercentOffsetY");
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

int getProgressColor(float progress) {
    float t = clampFloat(progress, 0f, 1f);
    return lerpColor(0xFFFF1100, 0xFF26FF00, t);
}

float getMaxBedAuraProgress() {
    try {
        float[] progress = modules.getBedAuraProgress();
        if (progress == null || progress.length == 0) return 0f;
        float max = 0f;
        for (float p : progress) {
            if (p > max) max = p;
        }
        if (max > 1f) max = 1f;
        if (max < 0f) max = 0f;
        return max;
    } catch (Exception ignored) {}
    return 0f;
}

void updateFade(boolean targetVisible) {
    long now = client.time();
    if (lastRenderMs == 0L) lastRenderMs = now;

    long dt = now - lastRenderMs;
    lastRenderMs = now;
    if (dt < 0L) dt = 0L;
    if (dt > 100L) dt = 100L;

    float fadeSpeedPerMs = 1.0f / 120.0f;

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

    float current = getMaxBedAuraProgress();
    boolean breaking = current > 0f;
    if (breaking) displayProgress = current;
    else if (chatOpen) displayProgress = 0.5f;

    boolean targetVisible = breaking || chatOpen;
    if (otherScreenOpen) targetVisible = false;

    long smoothNow = client.time();
    if (lastSmoothMs == 0L) {
        lastSmoothMs = smoothNow;
        smoothProgress = displayProgress;
        progressVelocityPerMs = 0.0f;
    } else {
        long dt = smoothNow - lastSmoothMs;
        lastSmoothMs = smoothNow;
        if (dt < 0L) dt = 0L;
        if (dt > 200L) dt = 200L;
        if (dt > 0L) {
            float smoothFactor = 1f - (float) Math.exp(-dt / 80.0);
            float prevSmooth = smoothProgress;
            smoothProgress += (displayProgress - smoothProgress) * smoothFactor;
            float instantVel = (smoothProgress - prevSmooth) / (float) dt;
            float velFactor = 1f - (float) Math.exp(-dt / 250.0);
            progressVelocityPerMs += (instantVel - progressVelocityPerMs) * velFactor;
        }
    }

    float predictedProgress = smoothProgress + progressVelocityPerMs * 220f;
    if (predictedProgress > 1f) predictedProgress = 1f;
    if (predictedProgress < smoothProgress) predictedProgress = smoothProgress;
    if (predictedProgress < 0f) predictedProgress = 0f;

    int pct = clampInt((int) Math.floor(smoothProgress * 100f), 0, 100);
    lastPctStr = pct + "%";

    updateFade(targetVisible);

    if (otherScreenOpen) {
        if (dragging) savePos();
        dragging = false;
    }

    if (fadeAlpha <= 0.0f) return;

    float scale = (float) modules.getSlider(scriptName, "Scale");
    boolean useFormatPrefix = modules.getButton(scriptName, "Start with {f}");

    String pctStr = (useFormatPrefix ? "{f}" : "") + lastPctStr;

    int baseNumberColor = getProgressColor(smoothProgress);

    int alpha = clampInt((int)(fadeAlpha * 255.0f), 0, 255);
    int numberColor = withAlpha(baseNumberColor, alpha);

    int[] display = client.getDisplaySize();
    int screenW = display[0];
    int screenH = display[1];

    int[] mp = keybinds.getMousePosition();
    int mx = mp[0] / display[2];
    int my = screenH - (mp[1] / display[2]);

    boolean leftDown = keybinds.isMouseDown(0);
    boolean leftJust = leftDown && !prevLeft;
    prevLeft = leftDown;

    int totalW = (int)(render.getFontWidth(pctStr) * scale);
    int th = (int)(render.getFontHeight() * scale);
    float barHeight = Math.max(1.7f, scale * 1.9f);
    float bottomLineHeight = Math.max(0.5f, scale * 0.6f);
    float barOverhang = Math.max(2.5f, scale * 3.0f);
    float barGap = Math.max(2.5f, scale * 3.0f);
    int totalH = th + (int) Math.ceil(barGap + barHeight);

    int baseX = (screenW / 2) - (totalW / 2);
    int baseY = (screenH / 2);

    int x = baseX + hudOffsetX;
    int y = baseY + hudOffsetY;

    if (chatOpen) {
        float clickLeft = x - barOverhang;
        float clickRight = x + totalW + barOverhang;
        if (leftJust
            && mx >= clickLeft && mx <= clickRight
            && my >= y && my <= y + totalH) {
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

    render.text(pctStr, x, y, scale, numberColor, true);

    float barLeft = x - barOverhang;
    float barWidth = totalW + barOverhang * 2f;
    float barY = y + th + barGap;
    float actualFillWidth = barWidth * clampFloat(smoothProgress, 0f, 1f);
    float predictedFillWidth = barWidth * clampFloat(predictedProgress, 0f, 1f);
    float bottomLineY = barY + barHeight - bottomLineHeight;

    int darkGreyRgb = 0xFF1F1F1F;
    int trackColor = withAlpha(darkGreyRgb, clampInt((int)(fadeAlpha * 0.85f * 255f), 0, 255));
    int themeFillColor = withAlpha(baseNumberColor, alpha);
    int themePredictColor = withAlpha(baseNumberColor, clampInt((int)(fadeAlpha * 0.45f * 255f), 0, 255));

    render.rect(barLeft, barY, barLeft + barWidth, barY + barHeight, trackColor);
    if (predictedFillWidth > actualFillWidth) {
        render.rect(barLeft, barY, barLeft + predictedFillWidth, barY + barHeight, themePredictColor);
    }
    if (actualFillWidth > 0f) {
        render.rect(barLeft, barY, barLeft + actualFillWidth, barY + barHeight, themeFillColor);
    }
    if (predictedFillWidth > 0f) {
        render.rect(barLeft, bottomLineY, barLeft + predictedFillWidth, barY + barHeight, trackColor);
    }
}
