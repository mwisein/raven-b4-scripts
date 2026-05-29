List<Map<String, Object>> mods = new ArrayList<>();
Map<String, Map<String, String>> customModuleData = new HashMap<>();
List<Map<String, Object>> customDataList = new ArrayList<>();
String[] themeOptions = {
    "Default", "Rainbow", "Aurora", "Cherry", "Cotton Candy",
    "Flare", "Flower", "Forest", "Frost", "Gold",
    "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
};
String[] settingsThemeMap = {
    "Rainbow", "Aurora", "Cherry", "Cotton candy", "Flare", "Flower", "Forest", "Frost",
    "Gold", "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
};

int background;
float lineGap = 1f, textScale;
int defaultHudOffsetX = 0, defaultHudOffsetY = 1;
int hudOffsetX = 0, hudOffsetY = 1;
float xOffset = 1f, yOffset = 1f;
int dragOX = 0, dragOY = 0;
boolean dragging = false;
boolean prevLeft = false;
boolean lineOnRight = true;
int resetTicks = 0;
String activeTheme = "white";
boolean lowercase;
boolean autoStartWithF;
boolean textShadow;
boolean animate;
boolean lineEnabled;
boolean bloomEnabled;
boolean invertGradient;
float backgroundRounding;

static final int BLOOM_SHADOW_L4 = 0x08000000;
static final int BLOOM_SHADOW_L3 = 0x12000000;
static final int BLOOM_SHADOW_L2 = 0x1C000000;
static final int BLOOM_SHADOW_L1 = 0x30000000;

void onLoad() {
    setDataSlider("AutoClicker", "AutoClicker", "%v1", new String[]{"Min CPS", "Max CPS"});
    setDataSlider("Fake Lag", "Fake Lag", "%v1ms", new String[]{"Outbound delay"});
    setDataSlider("Timer", "Timer", "%v1x", new String[]{"Slider B"});
    setDataSlider("Hitbox", "Hit Box", "%v1x", new String[]{"Multiplier"});
    setDataArray("KillAura", "KillAura", "Targets", new String[]{"Silent"});
    setDataSlider("AntiKnockback", "KB Delay", "%v1ms", new String[]{"Delay"});
    setDataSlider("Backtrack", "", "%v1ms", new String[]{"Delay"});
    setDataSlider("FastMine", "Fast Mine", "%v1x", new String[]{"Break speed"});
    setDataArray("NoSlow", "No Slow", "", new String[]{""});
    setDataArray("BedAura", "Nuker", "Break mode", new String[]{"Legit", "Instant", "Swap"});
    setDataSlider("Lag Range", "Lag Range", "%v1ms", new String[]{"Packet delay (far)"});
    setDataSlider("Hit Select", "", "%v1ms", new String[]{"Pause"});
    setDataStatic("GhostHand", "Piercing", "");
    setDataStatic("AimAssist", "Aim Assist", "Lock");
    setDataStatic("WTap", "Sprint Reset", "NoStop");
    setDataStatic("SafeWalk", "SafeWalk", "");
    setDataStatic("Jump Reset", "Velocity", "Jump");
    setDataStatic("FastPlace", "Fast Place", "");
    setDataStatic("AutoTool", "Auto Tool", "");
    setDataStatic("Displace", "KB Displacement", "110\u00B0");
    setDataStatic("Stasis", "", "");

    modules.registerDescription("GPTed. by @mwisein");
    modules.registerSlider("Background", "", 255, 0, 255, 1);
    modules.registerSlider("Rounding", "", 8, 0, 12, 0.1);
    modules.registerSlider("Scale", "", 0.9, 0.5, 2, 0.02);
    modules.registerSlider("Theme", "", 0, themeOptions);

    modules.registerButton("Animate", true);
    modules.registerButton("Bloom", false);
    modules.registerButton("Line", false);
    modules.registerButton("Invert gradient", false);
    modules.registerButton("Lowercase", true);
    modules.registerButton("Text shadow", true);
    modules.registerButton("Start With {f}", true);
}

void setDataStatic(String moduleName, String alias, String overrideValue) {
    Map<String, Object> customData = new HashMap<>();
    customData.put("moduleName", moduleName);
    customData.put("alias", alias);
    customData.put("overrideValue", overrideValue);
    customData.put("type", "fixed");
    customDataList.add(customData);
    updateCustomData(customData);
}

void setDataSlider(String moduleName, String alias, String displayString, String[] placeholders) {
    Map<String, Object> customData = new HashMap<>();
    customData.put("moduleName", moduleName);
    if (!alias.isEmpty()) customData.put("alias", alias);
    customData.put("displayString", displayString);
    customData.put("placeholders", placeholders);
    customData.put("type", "placeholders");
    customDataList.add(customData);
    updateCustomData(customData);
}

void setDataArray(String moduleName, String alias, String setting, String[] possibleValues) {
    Map<String, Object> customData = new HashMap<>();
    customData.put("moduleName", moduleName);
    if (!alias.isEmpty()) customData.put("alias", alias);
    customData.put("setting", setting);
    customData.put("possibleValues", possibleValues);
    customData.put("type", "strings");
    customDataList.add(customData);
    updateCustomData(customData);
}

void updateCustomData(Map<String, Object> customData) {
    String moduleName = (String) customData.get("moduleName");
    String alias = moduleName;

    if (customData.containsKey("alias")) {
        String customAlias = (String) customData.get("alias");
        if (customAlias != null && !customAlias.isEmpty()) alias = customAlias;
    }

    String overrideValue = "";

    switch ((String) customData.get("type")) {
        case "fixed":
            overrideValue = (String) customData.get("overrideValue");
            break;

        case "placeholders":
            String displayString = (String) customData.get("displayString");
            String[] placeholders = (String[]) customData.get("placeholders");
            for (int i = 0; i < placeholders.length; i++) {
                String placeholder = "%v" + (i + 1);
                String sliderValue = formatDoubleStr(modules.getSlider(moduleName, placeholders[i]));
                displayString = displayString.replace(placeholder, sliderValue);
            }
            overrideValue = displayString;
            break;

        case "strings":
            String setting = (String) customData.get("setting");
            String[] possibleValues = (String[]) customData.get("possibleValues");
            int index = (int) modules.getSlider(moduleName, setting);
            overrideValue = possibleValues[Math.min(index, possibleValues.length - 1)];
            break;
    }

    Map<String, String> data = new HashMap<>();
    data.put("alias", alias);
    data.put("overrideValue", overrideValue);
    customModuleData.put(moduleName, data);
}

void onEnable() {
    mods.clear();
    resetTicks = 0;
    Map<String, List<String>> categories = modules.getCategories();
    for (String category : categories.keySet()) {
        if (category.equalsIgnoreCase("profiles")) continue;
        List<String> modulesList = categories.get(category);
        for (String module : modulesList) {
            Map<String, Object> modData = new HashMap<>();
            modData.put("name", module);
            modData.put("visibility", false);
            modData.put("scale", 1f);
            modData.put("animStart", 0L);
            modData.put("animDirection", 1);
            modData.put("removing", false);
            mods.add(modData);
        }
    }

    loadPosition();
    dragging = false;
    prevLeft = false;
    updateButtonStates();
    updateSliders();
    updateEnabledModules();
    sortModules();
}

void onDisable() {
    savePosition();
    dragging = false;
}

void updateHudState() {
    resetTicks++;
    updateEnabledModules();
    lineGap = 1f;

    if (resetTicks == 1 || resetTicks % 5 == 0) {
        updateSliders();
    }
}

void updateSliders() {
    lowercase          = modules.getButton(scriptName, "Lowercase");
    autoStartWithF     = modules.getButton(scriptName, "Start With {f}");
    textShadow         = modules.getButton(scriptName, "Text shadow");
    animate            = modules.getButton(scriptName, "Animate");
    lineEnabled        = modules.getButton(scriptName, "Line");
    bloomEnabled       = modules.getButton(scriptName, "Bloom");
    invertGradient     = modules.getButton(scriptName, "Invert gradient");
    backgroundRounding = (float) modules.getSlider(scriptName, "Rounding");
    textScale          = (float) modules.getSlider(scriptName, "Scale");
    activeTheme        = resolveTheme().toLowerCase().trim();
    background         = clamp((int) Math.floor(modules.getSlider(scriptName, "Background")), 0, 255) << 24;

    for (Map<String, Object> customData : customDataList) {
        updateCustomData(customData);
    }

    sortModules();
}

void onRenderTick(float partialTicks) {
    updateHudState();

    int[] displaySize = client.getDisplaySize();
    int screenW = displaySize[0];
    int screenH = displaySize[1];
    int scaleFactor = displaySize[2];
    if (scaleFactor <= 0) scaleFactor = 1;

    String screen = client.getScreen();
    String screenLo = screen == null ? "" : screen.toLowerCase();
    boolean chatOpen = screenLo.contains("chat");
    boolean otherScreenOpen = screen != null && !screen.isEmpty() && !chatOpen;

    long now = client.time();
    long index = 0;
    List<Map<String, Object>> renderEntries = new ArrayList<>();
    float maxRawTextWidth = 0f;

    for (Map<String, Object> mod : mods) {
        if (!(boolean) mod.get("visibility")) continue;

        String moduleName = (String) mod.get("name");
        String displayName = moduleName;
        String displayValue = "";

        if (customModuleData.containsKey(moduleName)) {
            Map<String, String> customData = customModuleData.get(moduleName);
            displayName  = customData.getOrDefault("alias", moduleName);
            displayValue = customData.getOrDefault("overrideValue", "");
        }

        float scale = (float) mod.get("scale") * textScale;
        String renderedName  = lowercase ? displayName.toLowerCase()  : displayName;
        String renderedValue = lowercase ? displayValue.toLowerCase() : displayValue;
        float rawTextWidth = getPerCharacterTextWidth(renderedName, renderedValue, autoStartWithF);
        if (rawTextWidth > maxRawTextWidth) maxRawTextWidth = rawTextWidth;

        float animProgress = getAnimationProgress(mod, now);
        boolean exiting = (boolean) mod.getOrDefault("removing", false);
        if (exiting && animProgress <= 0.01f) {
            mod.put("visibility", false);
            mod.put("removing", false);
            mod.put("animStart", 0L);
            continue;
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("rawTextWidth", rawTextWidth);
        entry.put("scale", scale);
        entry.put("alpha", animProgress);
        entry.put("exiting", exiting);
        entry.put("index", index);
        entry.put("color", getColorForIndex(invertGradient ? 0L : index, now));
        entry.put("renderedName", renderedName);
        entry.put("renderedValue", renderedValue);
        renderEntries.add(entry);

        index -= (long) (100f * scale);
    }

    if (otherScreenOpen) {
        if (dragging) savePosition();
        dragging = false;
    }

    if (renderEntries.isEmpty() && chatOpen) {
        float scale = textScale;
        String preview = "RavenArray";
        float rawTextWidth = getPerCharacterTextWidth(preview, "", autoStartWithF);
        if (rawTextWidth > maxRawTextWidth) maxRawTextWidth = rawTextWidth;

        Map<String, Object> entry = new HashMap<>();
        entry.put("rawTextWidth", rawTextWidth);
        entry.put("scale", scale);
        entry.put("alpha", 1f);
        entry.put("exiting", false);
        entry.put("index", 0L);
        entry.put("color", getColorForIndex(0L, now));
        entry.put("renderedName", preview);
        entry.put("renderedValue", "");
        renderEntries.add(entry);
    }

    if (renderEntries.isEmpty()) {
        if (dragging) savePosition();
        dragging = false;
        return;
    }

    float uniformRowHeight = getUniformRowHeight(renderEntries);
    int totalW = 1;
    float totalHeight = 0f;
    for (Map<String, Object> entry : renderEntries) {
        float scale = ((Number) entry.get("scale")).floatValue();
        float rawTextWidth = ((Number) entry.get("rawTextWidth")).floatValue();
        float rowWidth = rawTextWidth * textScale + getBackgroundPadding(scale) * 2f;
        if (rowWidth > totalW) totalW = (int) Math.ceil(rowWidth);
        totalHeight += uniformRowHeight;
    }
    int totalH = Math.max(1, (int) Math.ceil(totalHeight));

    int baseX = screenW - totalW - 2;
    int baseY = 2;
    int x = baseX + hudOffsetX;
    int y = baseY + hudOffsetY;

    int[] mp = keybinds.getMousePosition();
    int mx = mp[0] / scaleFactor;
    int my = screenH - (mp[1] / scaleFactor);

    boolean leftDown = keybinds.isMouseDown(0);
    boolean leftJust = leftDown && !prevLeft;
    prevLeft = leftDown;

    if (chatOpen) {
        if (leftJust
            && mx >= x && mx <= x + totalW
            && my >= y && my <= y + totalH) {
            dragging = true;
            dragOX = mx - x;
            dragOY = my - y;
        }

        if (!leftDown) {
            if (dragging) savePosition();
            dragging = false;
        }

        if (dragging) {
            hudOffsetX = (mx - dragOX) - baseX;
            hudOffsetY = (my - dragOY) - baseY;
            x = baseX + hudOffsetX;
            y = baseY + hudOffsetY;
        }
    } else {
        if (dragging) savePosition();
        dragging = false;
    }

    xOffset = x;
    yOffset = y;
    lineOnRight = (x + totalW / 2) >= (screenW / 2);
    layoutEntries(renderEntries, maxRawTextWidth, uniformRowHeight);

    if (((background >>> 24) & 255) > 0) {
        if (bloomEnabled) {
            drawArrayBloom(renderEntries);
        }
        drawArrayBackground(renderEntries, background);
    }

    for (Map<String, Object> entry : renderEntries) {
        float y1 = ((Number) entry.get("y1")).floatValue();
        float finalXPosition = ((Number) entry.get("finalX")).floatValue();
        float scale = ((Number) entry.get("scale")).floatValue();
        float rowHeight = ((Number) entry.get("rowHeight")).floatValue();
        long baseIndex = ((Number) entry.get("index")).longValue();
        String renderedName  = (String) entry.get("renderedName");
        String renderedValue = (String) entry.get("renderedValue");
        float alpha = ((Number) entry.get("alpha")).floatValue();
        int rowColor = applyAlpha(((Number) entry.get("color")).intValue(), alpha);

        renderPerCharacterText(renderedName, renderedValue, finalXPosition, y1 + getTextYOffset(rowHeight, scale), scale, baseIndex, autoStartWithF, alpha, now);

        if (lineEnabled) {
            float edgeExtend = backgroundRounding > 0.05f ? Math.max(1.0f, 1.2f * textScale) : 0.0f;
            float outlineX = ((Number) entry.get("lineX")).floatValue();
            if (lineOnRight) {
                outlineX += edgeExtend;
            } else {
                outlineX -= edgeExtend;
            }
            float y1l = ((Number) entry.get("y1")).floatValue() - edgeExtend * 0.45f;
            float y2l = ((Number) entry.get("y2")).floatValue() + edgeExtend * 0.45f;
            render.rect(outlineX, y1l, outlineX + lineGap, y2l, rowColor);
        }
    }
}

void layoutEntries(List<Map<String, Object>> renderEntries, float maxRawTextWidth, float uniformRowHeight) {
    float y = yOffset;
    for (Map<String, Object> entry : renderEntries) {
        float scale = ((Number) entry.get("scale")).floatValue();
        float rawTextWidth = ((Number) entry.get("rawTextWidth")).floatValue();
        float alpha = ((Number) entry.get("alpha")).floatValue();
        boolean exiting = (boolean) entry.getOrDefault("exiting", false);
        float slide = animate ? 1f - alpha : 0f;
        if (exiting) slide = (float) Math.sqrt(slide);

        float slideX = lineOnRight ? slide * 18f : -slide * 18f;
        float rowHeight = uniformRowHeight;
        float pad = getBackgroundPadding(scale);
        float rowTextWidth = rawTextWidth * textScale;
        float slideY = -slide * Math.max(7f, rowHeight * 1.1f);
        float bgX1, bgX2, lineX;

        if (lineOnRight) {
            bgX2 = xOffset + maxRawTextWidth * textScale + pad * 2f;
            bgX1 = bgX2 - rowTextWidth - pad * 2f;
            lineX = bgX2;
        } else {
            bgX1 = xOffset;
            bgX2 = bgX1 + rowTextWidth + pad * 2f;
            lineX = bgX1 - lineGap;
        }

        float finalXPosition = bgX1 + pad;
        float x1 = bgX1;
        float y1 = y;
        float x2 = bgX2;
        float y2 = y + rowHeight;

        finalXPosition += slideX;
        x1 += slideX;
        x2 += slideX;
        bgX1 += slideX;
        bgX2 += slideX;
        lineX += slideX;
        y1 += slideY;
        y2 += slideY;

        entry.put("x1", x1);
        entry.put("y1", y1);
        entry.put("x2", x2);
        entry.put("y2", y2);
        entry.put("bgX1", bgX1);
        entry.put("bgX2", bgX2);
        entry.put("lineX", lineX);
        entry.put("finalX", finalXPosition);
        entry.put("rowHeight", rowHeight);

        y += rowHeight * (animate ? alpha : 1f);
    }
}

float getBackgroundPadding(float scale) {
    return Math.max(0.8f, scale * 1.0f);
}

float getRowHeight(float scale) {
    float pad = getBackgroundPadding(scale);
    return render.getFontHeight() * scale + pad * 2f;
}

float getUniformRowHeight(List<Map<String, Object>> renderEntries) {
    float rowHeight = getRowHeight(textScale);
    for (Map<String, Object> entry : renderEntries) {
        float scale = ((Number) entry.get("scale")).floatValue();
        float entryHeight = getRowHeight(scale);
        if (entryHeight > rowHeight) rowHeight = entryHeight;
    }
    return rowHeight;
}

float getTextYOffset(float rowHeight, float scale) {
    float textHeight = render.getFontHeight() * scale;
    return Math.max(0.0f, (rowHeight - textHeight) * 0.5f);
}

void drawArrayBackground(List<Map<String, Object>> renderEntries, int fillColor) {
    drawArrayBackgroundLayer(renderEntries, fillColor);
}

void drawArrayBloom(List<Map<String, Object>> renderEntries) {
    drawArrayBackgroundLayer(renderEntries, BLOOM_SHADOW_L4, 4.0f);
    drawArrayBackgroundLayer(renderEntries, BLOOM_SHADOW_L3, 3.0f);
    drawArrayBackgroundLayer(renderEntries, BLOOM_SHADOW_L2, 2.0f);
    drawArrayBackgroundLayer(renderEntries, BLOOM_SHADOW_L1, 1.0f);
}

void drawArrayBackgroundLayer(List<Map<String, Object>> renderEntries, int fillColor) {
    drawArrayBackgroundLayer(renderEntries, fillColor, 0.0f);
}

void drawArrayBackgroundLayer(List<Map<String, Object>> renderEntries, int fillColor, float shadowExpand) {
    boolean rounded = backgroundRounding > 0.05f;
    float radius = Math.max(0.0f, backgroundRounding * textScale) + shadowExpand;
    float baseExtend = rounded ? Math.max(1.0f, 1.2f * textScale) : 0.0f;
    float extend = baseExtend + shadowExpand;
    float extendY = baseExtend * 0.45f + shadowExpand;
    float minY = Float.MAX_VALUE;
    float maxY = -Float.MAX_VALUE;
    float uniformRadius = radius;

    for (Map<String, Object> entry : renderEntries) {
        float rowX1 = ((Number) entry.get("bgX1")).floatValue() - extend;
        float rowY1 = ((Number) entry.get("y1")).floatValue() - extendY;
        float rowX2 = ((Number) entry.get("bgX2")).floatValue() + extend;
        float rowY2 = ((Number) entry.get("y2")).floatValue() + extendY;
        if (rowY1 < minY) minY = rowY1;
        if (rowY2 > maxY) maxY = rowY2;
        if (rounded) {
            float widthHalf = (rowX2 - rowX1) * 0.5f;
            float heightHalf = (rowY2 - rowY1) * 0.5f;
            float constraint = Math.max(0.0f, Math.min(widthHalf, heightHalf) - 0.1f);
            if (constraint < uniformRadius) uniformRadius = constraint;
        }
    }

    if (minY >= maxY) return;
    if (!rounded) uniformRadius = 0.0f;

    boolean roundTopEdge = rounded && minY > 3.0f;
    float step = rounded ? 0.5f : 1.0f;
    for (float y = minY; y < maxY; y += step) {
        float y2 = Math.min(maxY, y + step);
        float midY = (y + y2) * 0.5f;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float alpha = 0.0f;

        for (Map<String, Object> entry : renderEntries) {
            float rowX1 = ((Number) entry.get("bgX1")).floatValue() - extend;
            float rowY1 = ((Number) entry.get("y1")).floatValue() - extendY;
            float rowX2 = ((Number) entry.get("bgX2")).floatValue() + extend;
            float rowY2 = ((Number) entry.get("y2")).floatValue() + extendY;
            if (midY < rowY1 || midY > rowY2) continue;

            float rowLeft = rowX1;
            float rowRight = rowX2;
            if (rounded) {
                boolean isTopRow = Math.abs(rowY1 - minY) < 0.75f;
                float inset = getHorizontalCornerInset(midY, rowY1, rowY2, uniformRadius, roundTopEdge && isTopRow);
                if (lineOnRight) {
                    rowLeft += inset;
                } else {
                    rowRight -= inset;
                }
            }

            if (rowLeft < left) left = rowLeft;
            if (rowRight > right) right = rowRight;

            float rowAlpha = ((Number) entry.get("alpha")).floatValue();
            if (rowAlpha > alpha) alpha = rowAlpha;
        }

        if (right <= left || alpha <= 0.0f) continue;

        int color = applyAlpha(fillColor, alpha);
        if (((color >> 24) & 0xFF) > 0) {
            render.rect(left, y, right, y2, color);
        }
    }
}

float getHorizontalCornerInset(float y, float top, float bottom, float radius, boolean roundTop) {
    if (radius <= 0.05f) return 0.0f;

    if (roundTop && y < top + radius) {
        float distanceTop = y - top;
        distanceTop = Math.max(0.0f, Math.min(radius, distanceTop));
        return radius - (float) Math.sqrt(Math.max(0.0f, radius * radius - (radius - distanceTop) * (radius - distanceTop)));
    }

    if (y <= bottom - radius) return 0.0f;
    float distance = bottom - y;
    distance = Math.max(0.0f, Math.min(radius, distance));
    return radius - (float) Math.sqrt(Math.max(0.0f, radius * radius - (radius - distance) * (radius - distance)));
}
void loadPosition() {
    hudOffsetX = defaultHudOffsetX;
    hudOffsetY = defaultHudOffsetY;
    try {
        String sx = config.get("arrayHudOffsetX");
        String sy = config.get("arrayHudOffsetY");
        if (sx != null && !sx.isEmpty()) hudOffsetX = Integer.parseInt(sx);
        if (sy != null && !sy.isEmpty()) hudOffsetY = Integer.parseInt(sy);
    } catch (Exception ignored) {
        hudOffsetX = defaultHudOffsetX;
        hudOffsetY = defaultHudOffsetY;
    }
}

void savePosition() {
    try {
        config.set("arrayHudOffsetX", String.valueOf(hudOffsetX));
        config.set("arrayHudOffsetY", String.valueOf(hudOffsetY));
    } catch (Exception ignored) {}
}

int getRainbow(float seconds, float saturation, float brightness, long index, long now) {
    float hue = ((now + index) % (int)(seconds * 1000)) / (float)(seconds * 1000);
    return Color.HSBtoRGB(hue, saturation, brightness);
}

int getColorForIndex(long index, long now) {
    return getThemeColor(activeTheme, index, now);
}

String resolveTheme() {
    int idx = (int) modules.getSlider(scriptName, "Theme");
    if (idx == 0) {
        try {
            int settingsIdx = (int) modules.getSlider("Settings", "Default theme");
            if (settingsIdx >= 0 && settingsIdx < settingsThemeMap.length) return settingsThemeMap[settingsIdx];
        } catch (Exception ignored) {}
        return "White";
    }
    if (idx >= 1 && idx < themeOptions.length) return themeOptions[idx];
    return "White";
}

int getThemeColor(String name, long index, long now) {
    String lo = name;
    if (lo.equals("rainbow")) return getRainbow(5f, 1f, 1f, index, now);
    double p = getWaveRatio(5f, index, now);
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

float getAnimationProgress(Map<String, Object> mod, long now) {
    if (!animate) return 1f;
    Object startObject = mod.get("animStart");
    if (!(startObject instanceof Number)) return 1f;

    long start = ((Number) startObject).longValue();
    if (start <= 0L) return 1f;

    int direction = ((Number) mod.getOrDefault("animDirection", 1)).intValue();
    float duration = direction < 0 ? 95f : 135f;
    float linear = (float) (now - start) / duration;
    if (linear >= 1f) {
        mod.put("animStart", 0L);
        if (direction < 0) {
            mod.put("visibility", false);
            mod.put("removing", false);
            return 0f;
        }
        return 1f;
    }
    if (linear < 0f) linear = 0f;

    float eased = (float) (1.0 - Math.pow(1.0 - linear, 3.0));
    float fade = direction < 0 ? (1f - linear) * (1f - linear) : eased * eased;
    return fade;
}

int applyAlpha(int color, float alpha) {
    if (alpha >= 1f) return color;
    if (alpha < 0f) alpha = 0f;
    int baseAlpha = (color >> 24) & 0xFF;
    int newAlpha = clamp((int) (baseAlpha * alpha), 0, 255);
    return (newAlpha << 24) | (color & 0x00FFFFFF);
}

int lerpColor(int c1, int c2, double t) {
    int r = clamp((int)(((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t)), 0, 255);
    int g = clamp((int)(((c1 >> 8)  & 0xFF) + ((((c2 >> 8)  & 0xFF) - ((c1 >> 8)  & 0xFF)) * t)), 0, 255);
    int b = clamp((int)(((c1)       & 0xFF) + ((((c2)       & 0xFF) - ((c1)       & 0xFF)) * t)), 0, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
}

void renderPerCharacterText(String nameText, String valueText, float x, float y, float scale, long baseIndex, boolean useFormatPrefix, float alpha, long now) {
    String formatPrefix = useFormatPrefix ? "{f}" : "";
    float currentX = x;
    long characterStep = invertGradient ? 145L : 15L;
    int visibleIndex = 0;
    char colorChar = util.colorSymbol.isEmpty() ? '\0' : util.colorSymbol.charAt(0);

    for (int i = 0; i < nameText.length(); i++) {
        char c = nameText.charAt(i);
        if (colorChar != '\0' && c == colorChar && i + 1 < nameText.length()) {
            i++;
            continue;
        }
        String charText = formatPrefix + c;
        long colorIndex = invertGradient ? -((long) visibleIndex * characterStep) : baseIndex - (long) visibleIndex * characterStep;
        int characterColor = applyAlpha(getColorForIndex(colorIndex, now), alpha);
        render.text(charText, currentX, y, scale, characterColor, textShadow);
        currentX += render.getFontWidth(charText) * scale;
        visibleIndex++;
    }

    if (!valueText.isEmpty()) {
        String suffixText = formatPrefix + " " + util.colorSymbol + "7" + valueText;
        render.text(suffixText, currentX, y, scale, applyAlpha(0xFFFFFFFF, alpha), textShadow);
    }
}

float getPerCharacterTextWidth(String nameText, String valueText, boolean useFormatPrefix) {
    String formatPrefix = useFormatPrefix ? "{f}" : "";
    float width = 0f;
    char colorChar = util.colorSymbol.isEmpty() ? '\0' : util.colorSymbol.charAt(0);

    for (int i = 0; i < nameText.length(); i++) {
        char c = nameText.charAt(i);
        if (colorChar != '\0' && c == colorChar && i + 1 < nameText.length()) {
            i++;
            continue;
        }
        width += render.getFontWidth(formatPrefix + c);
    }

    if (!valueText.isEmpty()) {
        String suffixText = formatPrefix + " " + util.colorSymbol + "7" + valueText;
        width += render.getFontWidth(suffixText);
    }

    return width;
}

double getWaveRatio(float seconds, long index, long now) {
    float time = ((now + index) % (int)(seconds * 1000)) / (float)(seconds * 1000);
    return (time <= 0.5) ? (time * 2) : (2 - time * 2);
}

int clamp(int val, int min, int max) {
    if (val < min) return min;
    if (val > max) return max;
    return val;
}

void updateEnabledModules() {
    long now = client.time();

    if (resetTicks < 60 || resetTicks % 20 == 0) {
        updateButtonStates();
    }

    for (Map<String, Object> mod : mods) {
        String moduleName = (String) mod.get("name");
        boolean currentlyVisible = (boolean) mod.get("visibility");
        boolean shouldBeVisible = (boolean) mod.getOrDefault("buttonEnabled", false) && modules.isEnabled(moduleName);
        boolean removing = (boolean) mod.getOrDefault("removing", false);

        if (shouldBeVisible) {
            if (!currentlyVisible || removing) {
                mod.put("visibility", true);
                mod.put("removing", false);
                mod.put("scale", 1f);
                mod.put("animDirection", 1);
                mod.put("animStart", animate ? now : 0L);
            }
        } else if (currentlyVisible) {
            if (animate) {
                if (!removing) {
                    mod.put("removing", true);
                    mod.put("scale", 1f);
                    mod.put("animDirection", -1);
                    mod.put("animStart", now);
                }
            } else {
                mod.put("visibility", false);
                mod.put("removing", false);
                mod.put("animStart", 0L);
            }
        }
    }
}

void updateButtonStates() {
    for (Map<String, Object> mod : mods) {
        String moduleName = (String) mod.get("name");
        boolean isButtonEnabled = !modules.isHidden(moduleName);
        mod.put("buttonEnabled", isButtonEnabled);
    }
    sortModules();
}

void sortModules() {
    mods.sort((a, b) -> {
        float widthA = getModuleSortWidth(a);
        float widthB = getModuleSortWidth(b);
        if (widthA < widthB) return 1;
        if (widthA > widthB) return -1;
        return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
    });
}

float getModuleSortWidth(Map<String, Object> mod) {
    String moduleName = (String) mod.get("name");
    String displayName = moduleName;
    String displayValue = "";

    if (customModuleData.containsKey(moduleName)) {
        Map<String, String> data = customModuleData.get(moduleName);
        displayName = data.getOrDefault("alias", moduleName);
        displayValue = data.getOrDefault("overrideValue", "");
    }

    if (lowercase) {
        displayName = displayName.toLowerCase();
        displayValue = displayValue.toLowerCase();
    }

    return getPerCharacterTextWidth(displayName, displayValue, autoStartWithF);
}

String formatDoubleStr(double val) {
    return val == (long) val ? Long.toString((long) val) : Double.toString(val);
}
