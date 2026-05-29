String blacklistedModules = "InvMove";

ArrayList<Map<String, Object>> notifications = new ArrayList<Map<String, Object>>();
Map<String, Boolean> lastStates = new HashMap<String, Boolean>();
String[] themeOptions = {
    "Default", "Rainbow", "Aurora", "Cherry", "Cotton Candy",
    "Flare", "Flower", "Forest", "Frost", "Gold",
    "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
};
String[] disableThemeOptions = {
    util.color("&cDisabled"), "Rainbow", "Aurora", "Cherry", "Cotton Candy",
    "Flare", "Flower", "Forest", "Frost", "Gold",
    "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
};
String[] settingsThemeMap = {
    "Rainbow", "Aurora", "Cherry", "Cotton candy", "Flare", "Flower", "Forest", "Frost",
    "Gold", "Grayscale", "Inferno", "Royal", "Sandstorm", "Sky", "Vine"
};

boolean mouseDown = false;
boolean lastMouseDown = false;
float mouseX = 0.0f;
float mouseY = 0.0f;
boolean notificationDragging = false;
float notificationDragX = 0.0f;
float notificationDragY = 0.0f;
float brOffsetX = 12.0f;
float brOffsetY = 12.0f;
float blOffsetX = 12.0f;
float blOffsetY = 12.0f;
float trOffsetX = 12.0f;
float trOffsetY = 12.0f;
float tlOffsetX = 12.0f;
float tlOffsetY = 12.0f;
long closeMs = 230L;
long lastEditPositionWarningMs = 0L;

void onLoad() {
    modules.registerDescription("Slinky inspired notifications.");
    modules.registerButton("Start with {f}", true);
    modules.registerSlider("Theme", "", 0, themeOptions);
    modules.registerSlider("Disable theme", "", 0, disableThemeOptions);
    modules.registerSlider("Duration", "ms", 3000, 1000, 7000, 100);
    modules.registerSlider("Scale", "x", 0.9, 0.5, 2.0, 0.1);
    modules.registerSlider("Position", "", 3, new String[] {"Bottom Right", "Bottom Left", "Top Right", "Top Left"});
    modules.registerButton("Edit position", false);
    loadNotificationPosition();
}

void onEnable() {
    loadNotificationPosition();
    notifications.clear();
    notificationDragging = false;
    lastMouseDown = false;
    mouseDown = false;
    lastEditPositionWarningMs = 0L;
    syncModuleStates();
}

void onDisable() {
    saveNotificationPosition();
    notifications.clear();
    lastStates.clear();
    notificationDragging = false;
}

void checkModuleChanges() {
    printEditPositionWarningIfNeeded();

    int enabledCount = 0;
    int disabledCount = 0;
    String enabledName = "";
    String disabledName = "";
    Map<String, List<String>> categories = modules.getCategories();

    for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
        String category = entry.getKey();
        if (category != null && category.equalsIgnoreCase("profiles")) continue;

        List<String> moduleNames = entry.getValue();
        for (String moduleName : moduleNames) {
            if (moduleName.equals(scriptName)) continue;
            if (shouldIgnoreModule(moduleName)) {
                lastStates.remove(moduleName);
                continue;
            }

            boolean current = modules.isEnabled(moduleName);
            Boolean old = lastStates.get(moduleName);
            if (old == null) {
                lastStates.put(moduleName, Boolean.valueOf(current));
                continue;
            }

            if (old.booleanValue() != current) {
                lastStates.put(moduleName, Boolean.valueOf(current));
                if (current) {
                    enabledCount++;
                    if (enabledCount == 1) enabledName = moduleName;
                } else {
                    disabledCount++;
                    if (disabledCount == 1) disabledName = moduleName;
                }
            }
        }
    }

    if (enabledCount > 0) {
        pushNotification(buildMessage(enabledName, enabledCount, true), true);
    }

    if (disabledCount > 0) {
        pushNotification(buildMessage(disabledName, disabledCount, false), false);
    }
}

void onRenderTick(float partialTicks) {
    checkModuleChanges();
    renderNotifications();
}

void syncModuleStates() {
    lastStates.clear();
    Map<String, List<String>> categories = modules.getCategories();

    for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
        String category = entry.getKey();
        if (category != null && category.equalsIgnoreCase("profiles")) continue;

        List<String> moduleNames = entry.getValue();
        for (String moduleName : moduleNames) {
            if (!moduleName.equals(scriptName) && !shouldIgnoreModule(moduleName)) {
                lastStates.put(moduleName, Boolean.valueOf(modules.isEnabled(moduleName)));
            }
        }
    }
}

String buildMessage(String firstName, int count, boolean enabled) {
    String prefix = enabled ? "Enabled " : "Disabled ";
    if (count == 1) {
        return prefix + firstName;
    }

    return prefix + count + " mods";
}

void pushNotification(String text, boolean enabled) {
    Map<String, Object> notification = new HashMap<String, Object>();
    notification.put("text", text);
    notification.put("enabled", Boolean.valueOf(enabled));
    notification.put("created", Long.valueOf(client.time()));
    notifications.add(0, notification);

    int openCount = 0;
    for (int i = 0; i < notifications.size(); i++) {
        if (!isNotificationClosing(notifications.get(i))) openCount++;
    }

    for (int i = notifications.size() - 1; openCount > 7 && i >= 0; i--) {
        Map<String, Object> oldest = notifications.get(i);
        if (!isNotificationClosing(oldest)) {
            startNotificationClose(oldest);
            openCount--;
        }
    }
}

void updateMouse(int[] size) {
    lastMouseDown = mouseDown;
    mouseDown = keybinds.isMouseDown(0);

    float guiScale = size[2];
    if (guiScale <= 0.0f) guiScale = 1.0f;
    int[] mp = keybinds.getMousePosition();
    mouseX = mp[0] / guiScale;
    mouseY = size[1] - (mp[1] / guiScale);
}

void renderNotifications() {
    int[] display = client.getDisplaySize();
    int screenWidth = display[0];
    int screenHeight = display[1];
    long now = client.time();
    String screen = client.getScreen();
    boolean chatOpen = screen != null && !screen.isEmpty() && screen.toLowerCase().contains("chat");
    boolean editPosition = isEditingPosition();
    boolean previewMode = chatOpen && editPosition;
    if (previewMode) {
        updateMouse(display);
    } else {
        notificationDragging = false;
        lastMouseDown = false;
        mouseDown = false;
    }

    if (!notifications.isEmpty()) {
        long duration = (long) modules.getSlider(scriptName, "Duration");
        for (int i = notifications.size() - 1; i >= 0; i--) {
            Map<String, Object> notification = notifications.get(i);
            if (isNotificationClosing(notification)) {
                long closingAt = ((Long) notification.get("closingAt")).longValue();
                if (now - closingAt > closeMs) {
                    notifications.remove(i);
                }
            } else {
                long created = ((Long) notification.get("created")).longValue();
                if (now - created > duration) {
                    startNotificationClose(notification);
                }
            }
        }
    }

    if (notifications.isEmpty() && !previewMode) {
        notificationDragging = false;
        return;
    }

    float uiScale = (float) modules.getSlider(scriptName, "Scale");
    boolean fontPrefix = useFontPrefix();
    float scale = 0.78f * uiScale;
    float tagScale = 0.76f * uiScale;
    float padding = 5.0f * uiScale;
    float height = 22.5f * uiScale;
    float tagHeight = 13.5f * uiScale;
    float spacing = height + 4.0f * uiScale;
    int corner = (int) modules.getSlider(scriptName, "Position");
    boolean right = corner == 0 || corner == 2;
    boolean bottom = corner == 0 || corner == 1;
    float qX1 = right ? screenWidth / 2.0f : 0.0f;
    float qX2 = right ? screenWidth : screenWidth / 2.0f;
    float qY1 = bottom ? screenHeight / 2.0f : 0.0f;
    float qY2 = bottom ? screenHeight : screenHeight / 2.0f;

    if (chatOpen && editPosition) {
        render.rect(qX1, qY1, qX2, qY2, 0x22000000);
        drawRectOutline(qX1 + 1.0f, qY1 + 1.0f, qX2 - 1.0f, qY2 - 1.0f, 1.0f, 0x88FFFFFF);
    }

    int count = previewMode ? 1 : notifications.size();
    float stackHeight = height + (count - 1) * spacing;
    String tag = "RAVEN";
    String tagText = fontText(tag, fontPrefix);
    float tagTextWidth = render.getFontWidth(tagText);
    float fontHeight = render.getFontHeight() * scale;
    float tagFontHeight = render.getFontHeight() * tagScale;
    float tagWidth = tagTextWidth * tagScale + 12.0f * uiScale;
    float tagRadius = Math.max(3.0f * uiScale, tagHeight * 0.34f);
    float innerGap = 2.5f * uiScale;
    float rightGap = 8.0f * uiScale;

    for (int i = 0; i < count; i++) {
        Map<String, Object> notification = null;
        String text = "Enabled Player ESP";
        boolean enabledNotification = true;
        long created = now;

        if (!previewMode) {
            notification = notifications.get(i);
            text = (String) notification.get("text");
            Object enabledValue = notification.get("enabled");
            enabledNotification = !(enabledValue instanceof Boolean) || ((Boolean) enabledValue).booleanValue();
            created = ((Long) notification.get("created")).longValue();
        }

        long age = now - created;
        float inAnim = previewMode ? 1.0f : clamp(age / 210.0f, 0.0f, 1.0f);
        float outAnim = !previewMode && isNotificationClosing(notification)
            ? 1.0f - clamp((now - ((Long) notification.get("closingAt")).longValue()) / (float) closeMs, 0.0f, 1.0f)
            : 1.0f;
        float anim = easeOutCubic(Math.min(inAnim, outAnim));

        String messageText = fontText(text, fontPrefix);
        float messageTextWidth = render.getFontWidth(messageText);
        float messageWidth = messageTextWidth * scale;
        float width = padding + tagWidth + innerGap + messageWidth + rightGap;
        float xOffset = clamp(getNotificationOffsetX(corner), 2.0f, Math.max(2.0f, (qX2 - qX1) - width - 2.0f));
        float yOffset = clamp(getNotificationOffsetY(corner), 2.0f, Math.max(2.0f, (qY2 - qY1) - stackHeight - 2.0f));

        float shownX = right ? (qX2 - width - xOffset) : (qX1 + xOffset);
        float hiddenX = right ? (screenWidth + width + 8.0f) : (-width - 8.0f);
        float x = hiddenX + (shownX - hiddenX) * anim;
        float baseY = bottom ? (qY2 - yOffset - height) : (qY1 + yOffset);
        float y = bottom ? (baseY - i * spacing) : (baseY + i * spacing);

        if (i == 0) {
            updateNotificationDrag(chatOpen && editPosition, corner, qX1, qX2, qY1, qY2, shownX, baseY, width, height, stackHeight);
            xOffset = clamp(getNotificationOffsetX(corner), 2.0f, Math.max(2.0f, (qX2 - qX1) - width - 2.0f));
            yOffset = clamp(getNotificationOffsetY(corner), 2.0f, Math.max(2.0f, (qY2 - qY1) - stackHeight - 2.0f));
            setNotificationOffsets(corner, xOffset, yOffset);
            shownX = right ? (qX2 - width - xOffset) : (qX1 + xOffset);
            x = hiddenX + (shownX - hiddenX) * anim;
            baseY = bottom ? (qY2 - yOffset - height) : (qY1 + yOffset);
            y = bottom ? baseY : baseY;
        }

        float tagX = x + padding;
        float tagY = y + (height - tagHeight) / 2.0f;
        float textX = tagX + tagWidth + innerGap;
        float textY = y + (height - fontHeight) / 2.0f + 0.45f * uiScale;
        float tagTextX = tagX + (tagWidth - tagTextWidth * tagScale) / 2.0f;
        float tagTextY = tagY + (tagHeight - tagFontHeight) / 2.0f;
        int accent = getThemeColor(enabledNotification ? resolveTheme() : resolveDisableTheme(), now);
        int pillColor = getPillColor(accent);

        renderNotificationBackground(x, y, width, height, height / 2.0f, anim);
        render.roundedRect(tagX, tagY, tagX + tagWidth, tagY + tagHeight, tagRadius, multiplyAlpha(pillColor, anim));
        render.text(tagText, tagTextX, tagTextY, tagScale, multiplyAlpha(accent, anim), true);
        render.text(messageText, textX, textY, scale, multiplyAlpha(0xFFD2D2D2, anim), true);
    }
}

void renderNotificationBackground(float x, float y, float width, float height, float radius, float anim) {
    render.roundedRect(x, y, x + width, y + height, radius, multiplyAlpha(0xF008080A, anim));
}

boolean isNotificationClosing(Map<String, Object> notification) {
    return notification != null && notification.containsKey("closingAt");
}

void startNotificationClose(Map<String, Object> notification) {
    if (notification == null || isNotificationClosing(notification)) return;
    notification.put("closingAt", Long.valueOf(client.time()));
}

String resolveTheme() {
    int idx = (int) modules.getSlider(scriptName, "Theme");
    if (idx == 0) {
        try {
            int settingsIdx = (int) modules.getSlider("Settings", "Default theme");
            if (settingsIdx >= 0 && settingsIdx < settingsThemeMap.length) {
                return settingsThemeMap[settingsIdx];
            }
        } catch (Exception ignored) {
        }
        return "white";
    }

    if (idx >= 1 && idx < themeOptions.length) return themeOptions[idx];
    return "white";
}

String resolveDisableTheme() {
    int idx = (int) modules.getSlider(scriptName, "Disable theme");
    if (idx <= 0) return resolveTheme();
    if (idx < disableThemeOptions.length) return disableThemeOptions[idx];
    return resolveTheme();
}

int getThemeColor(String name, long now) {
    String lo = name == null ? "white" : name.toLowerCase().trim();
    if (lo.equals("rainbow")) return getRainbowColor(now);

    double p = getWaveRatio(now);
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
    return 0xFFFF6A1A;
}

int getRainbowColor(long now) {
    float hue = (now % 5000L) / 5000.0f;
    return Color.HSBtoRGB(hue, 1.0f, 1.0f);
}

int getPillColor(int accent) {
    int r = (accent >> 16) & 0xFF;
    int g = (accent >> 8) & 0xFF;
    int b = accent & 0xFF;
    int darkR = clampInt((int) (r * 0.18), 0, 255);
    int darkG = clampInt((int) (g * 0.21), 0, 255);
    int darkB = clampInt((int) (b * 0.42), 0, 255);
    return withAlpha((darkR << 16) | (darkG << 8) | darkB, 85);
}

double getWaveRatio(long now) {
    float time = (now % 5000L) / 5000.0f;
    return time <= 0.5f ? time * 2.0 : 2.0 - time * 2.0;
}

int lerpColor(int c1, int c2, double t) {
    int r = clampInt((int)(((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t)), 0, 255);
    int g = clampInt((int)(((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t)), 0, 255);
    int b = clampInt((int)((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t)), 0, 255);
    return 0xFF000000 | (r << 16) | (g << 8) | b;
}

int clampInt(int value, int min, int max) {
    if (value < min) return min;
    if (value > max) return max;
    return value;
}

boolean shouldIgnoreModule(String moduleName) {
    if (moduleName == null || blacklistedModules == null || blacklistedModules.isEmpty()) return false;

    String[] parts = blacklistedModules.split(",");
    for (int i = 0; i < parts.length; i++) {
        if (parts[i].trim().equalsIgnoreCase(moduleName)) return true;
    }

    return false;
}

boolean isEditingPosition() {
    try {
        return modules.getButton(scriptName, "Edit position");
    } catch (Exception ignored) {
    }

    return false;
}

void printEditPositionWarningIfNeeded() {
    if (!isEditingPosition()) {
        lastEditPositionWarningMs = 0L;
        return;
    }

    long now = client.time();
    if (lastEditPositionWarningMs != 0L && now - lastEditPositionWarningMs < 5000L) return;

    lastEditPositionWarningMs = now;
    String cs = util.colorSymbol;
    client.print(cs + "7[" + cs + "dR" + cs + "7] " + cs + "bslinknotifs" + cs + "7: " + cs + "c\"Edit position\" is enabled" + cs + "7.");
}

float getNotificationOffsetX(int corner) {
    if (corner == 0) return brOffsetX;
    if (corner == 1) return blOffsetX;
    if (corner == 2) return trOffsetX;
    return tlOffsetX;
}

float getNotificationOffsetY(int corner) {
    if (corner == 0) return brOffsetY;
    if (corner == 1) return blOffsetY;
    if (corner == 2) return trOffsetY;
    return tlOffsetY;
}

void setNotificationOffsets(int corner, float x, float y) {
    if (corner == 0) {
        brOffsetX = x;
        brOffsetY = y;
    } else if (corner == 1) {
        blOffsetX = x;
        blOffsetY = y;
    } else if (corner == 2) {
        trOffsetX = x;
        trOffsetY = y;
    } else {
        tlOffsetX = x;
        tlOffsetY = y;
    }
}

void saveNotificationPosition() {
    try {
        config.set("slinknotifs_brOffsetX", String.valueOf(brOffsetX));
        config.set("slinknotifs_brOffsetY", String.valueOf(brOffsetY));
        config.set("slinknotifs_blOffsetX", String.valueOf(blOffsetX));
        config.set("slinknotifs_blOffsetY", String.valueOf(blOffsetY));
        config.set("slinknotifs_trOffsetX", String.valueOf(trOffsetX));
        config.set("slinknotifs_trOffsetY", String.valueOf(trOffsetY));
        config.set("slinknotifs_tlOffsetX", String.valueOf(tlOffsetX));
        config.set("slinknotifs_tlOffsetY", String.valueOf(tlOffsetY));
    } catch (Exception ignored) {
    }
}

void loadNotificationPosition() {
    try {
        String brX = config.get("slinknotifs_brOffsetX");
        String brY = config.get("slinknotifs_brOffsetY");
        String blX = config.get("slinknotifs_blOffsetX");
        String blY = config.get("slinknotifs_blOffsetY");
        String trX = config.get("slinknotifs_trOffsetX");
        String trY = config.get("slinknotifs_trOffsetY");
        String tlX = config.get("slinknotifs_tlOffsetX");
        String tlY = config.get("slinknotifs_tlOffsetY");

        if (brX != null && !brX.isEmpty()) brOffsetX = Float.parseFloat(brX);
        if (brY != null && !brY.isEmpty()) brOffsetY = Float.parseFloat(brY);
        if (blX != null && !blX.isEmpty()) blOffsetX = Float.parseFloat(blX);
        if (blY != null && !blY.isEmpty()) blOffsetY = Float.parseFloat(blY);
        if (trX != null && !trX.isEmpty()) trOffsetX = Float.parseFloat(trX);
        if (trY != null && !trY.isEmpty()) trOffsetY = Float.parseFloat(trY);
        if (tlX != null && !tlX.isEmpty()) tlOffsetX = Float.parseFloat(tlX);
        if (tlY != null && !tlY.isEmpty()) tlOffsetY = Float.parseFloat(tlY);
    } catch (Exception ignored) {
    }
}

void updateNotificationDrag(boolean chatOpen, int corner, float qX1, float qX2, float qY1, float qY2, float shownX, float shownY, float width, float height, float stackHeight) {
    if (!chatOpen || !mouseDown) {
        if (notificationDragging) {
            saveNotificationPosition();
        }
        notificationDragging = false;
        return;
    }

    boolean right = corner == 0 || corner == 2;
    boolean bottom = corner == 0 || corner == 1;

    if (!notificationDragging && mouseDown && !lastMouseDown && isMouseInside(shownX, shownY, shownX + width, shownY + height)) {
        notificationDragging = true;
        notificationDragX = mouseX - shownX;
        notificationDragY = mouseY - shownY;
    }

    if (!notificationDragging) return;

    float targetX = clamp(mouseX - notificationDragX, qX1 + 2.0f, qX2 - width - 2.0f);
    float targetY;
    if (bottom) {
        targetY = clamp(mouseY - notificationDragY, qY1 + stackHeight - height + 2.0f, qY2 - height - 2.0f);
    } else {
        targetY = clamp(mouseY - notificationDragY, qY1 + 2.0f, qY2 - stackHeight - 2.0f);
    }

    float nextXOffset = right ? (qX2 - width - targetX) : (targetX - qX1);
    float nextYOffset = bottom ? (qY2 - height - targetY) : (targetY - qY1);
    nextXOffset = clamp(nextXOffset, 2.0f, Math.max(2.0f, (qX2 - qX1) - width - 2.0f));
    nextYOffset = clamp(nextYOffset, 2.0f, Math.max(2.0f, (qY2 - qY1) - stackHeight - 2.0f));
    setNotificationOffsets(corner, nextXOffset, nextYOffset);
}

boolean isMouseInside(float x1, float y1, float x2, float y2) {
    return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
}

void drawRectOutline(float x1, float y1, float x2, float y2, float thickness, int color) {
    render.rect(x1, y1, x2, y1 + thickness, color);
    render.rect(x1, y2 - thickness, x2, y2, color);
    render.rect(x1, y1, x1 + thickness, y2, color);
    render.rect(x2 - thickness, y1, x2, y2, color);
}

boolean useFontPrefix() {
    try {
        return modules.getButton(scriptName, "Start with {f}");
    } catch (Exception ignored) {
    }

    return true;
}

String fontText(String text, boolean usePrefix) {
    String value = text == null ? "" : text;
    return usePrefix ? "{f}" + value : value;
}

int withAlpha(int color, int alpha) {
    return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
}

int multiplyAlpha(int color, float alpha) {
    int currentAlpha = (color >> 24) & 0xFF;
    int nextAlpha = (int) (currentAlpha * clamp(alpha, 0.0f, 1.0f));
    return withAlpha(color, nextAlpha);
}

float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
}

float easeOutCubic(float t) {
    float value = clamp(t, 0.0f, 1.0f);
    float inv = 1.0f - value;
    return 1.0f - inv * inv * inv;
}
