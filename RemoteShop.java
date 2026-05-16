List<SPacket> buffered = new ArrayList<>();

boolean listening = false;
boolean waitingForOpen = false;
boolean buffering = false;
boolean failed = false;

long waitStartMs = 0L;
long enableMs = 0L;

float hudAlpha = 0.0f;
String lastColoredText = "";
String lastPlainText = "";

void onLoad() {
    modules.registerDescription("Buy upgrades remotely!");
    modules.registerButton("Disable chests", false);
    modules.registerSlider("Disable on fail after", "s", 5, 5, 20, 5);
}

void onEnable() {
    buffered.clear();
    listening = true;
    waitingForOpen = false;
    buffering = false;
    failed = false;
    waitStartMs = 0L;
    enableMs = client.time();
    hudAlpha = 0.0f;
    lastColoredText = "";
    lastPlainText = "";
}

void onDisable() {
    flush();
    waitingForOpen = false;
    failed = false;
}

boolean isBufferedContainerPacket(String name) {
    return name.contains("OpenWindow")
        || name.contains("S2D")
        || name.contains("WindowItems")
        || name.contains("S30");
}

boolean isChestBlock(Block block) {
    if (block == null || block.name == null) {
        return false;
    }

    String name = block.name.toLowerCase();
    return name.contains("chest") || name.contains("ender_chest") || name.contains("enderchest");
}

boolean onMouse(int button, boolean state) {
    if (!state) {
        return true;
    }

    if (button == 1 && modules.getButton(scriptName, "Disable chests")) {
        Object[] hit = client.raycastBlock(5.0);
        if (hit != null) {
            Vec3 blockPos = (Vec3) hit[0];
            Block block = world.getBlockAt(blockPos);
            if (isChestBlock(block)) {
                return false;
            }
        }
    }

    if (!listening || buffering) {
        return true;
    }

    if (button == 1) {
        waitingForOpen = true;
        waitStartMs = client.time();
    }

    return true;
}

boolean onPacketReceived(SPacket packet) {
    if (packet == null || packet.name == null) {
        return true;
    }

    if (!waitingForOpen && !buffering) {
        return true;
    }

    if (isBufferedContainerPacket(packet.name)) {
        buffering = true;
        listening = false;
        waitingForOpen = false;
        buffered.add(packet);
        return false;
    }

    return true;
}

void drawCenteredStatus(int centerX, int centerY, String coloredText, String plainText, float alpha) {
    float x = centerX - (render.getFontWidth(plainText) / 2.0f);
    float y = centerY + 12.0f;
    int argb = ((int) (alpha * 255.0f) << 24) | 0xFFFFFF;
    render.text(util.color(coloredText), x, y, 1.0f, argb, true);
}

void onRenderTick(float partialTicks) {
    long now = client.time();

    if (listening && !buffering) {
        long failAfterSeconds = (long) modules.getSlider(scriptName, "Disable on fail after");
        long failAfterMs = failAfterSeconds * 1000L;

        if (now - enableMs >= failAfterMs) {
            failed = true;
            client.print(util.color("&7[&dRS&7] &cFailed &7to &efind &7container after &b" + failAfterSeconds + "&7 seconds."));
            modules.disable(scriptName);
            return;
        }
    }

    if (waitingForOpen && now - waitStartMs > 1000L) {
        waitingForOpen = false;
    }

    int[] display = client.getDisplaySize();
    int centerX = display[0] / 2;
    int centerY = display[1] / 2;

    String coloredText = "";
    String plainText = "";
    boolean showHud = false;

    if (listening && !buffering) {
        coloredText = "&7[&dRS&7] &eListening...";
        plainText = "[RS] Listening...";
        showHud = true;
    } else if (buffering) {
        coloredText = "&7[&dRS&7] &cShop hidden";
        plainText = "[RS] Shop hidden";
        showHud = true;
    }

    if (showHud) {
        lastColoredText = coloredText;
        lastPlainText = plainText;
        hudAlpha = Math.min(1.0f, hudAlpha + 0.05f);
        drawCenteredStatus(centerX, centerY, coloredText, plainText, hudAlpha);
    } else {
        hudAlpha = Math.max(0.0f, hudAlpha - 0.05f);
        if (hudAlpha > 0.01f && !lastPlainText.isEmpty()) {
            drawCenteredStatus(centerX, centerY, lastColoredText, lastPlainText, hudAlpha);
        }
    }
}

void flush() {
    for (int i = 0; i < buffered.size(); i++) {
        client.processPacket(buffered.get(i));
    }

    buffered.clear();
    listening = false;
    waitingForOpen = false;
    buffering = false;
}
