Map<String, Integer> teamColours = new HashMap<String, Integer>();

void onLoad() {
    modules.registerDescription("Custom TargetESP colours.");
    modules.registerSlider("Red", "", 0, 0, 255, 1);
    modules.registerSlider("Green", "", 0, 0, 255, 1);
    modules.registerSlider("Blue", "", 0, 0, 255, 1);
    modules.registerButton("Second Color", false);
    modules.registerSlider("Secondary Red", "", 0, 0, 255, 1);
    modules.registerSlider("Secondary Green", "", 0, 0, 255, 1);
    modules.registerSlider("Secondary Blue", "", 0, 0, 255, 1);
    modules.registerDescription("Other");
    modules.registerSlider("Transition Speed", "ms", 2100, 100, 5000, 100);
    modules.registerSlider("Alpha", "", 36, 0, 255, 1);
    modules.registerButton("Team Colour", false);
    loadTeamColours();
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

int clampColor(double value) {
    if (value < 0) return 0;
    if (value > 255) return 255;
    return (int) value;
}

int getAlpha() {
    return clampColor(modules.getSlider(scriptName, "Alpha"));
}

int makeColor(int r, int g, int b) {
    return new Color(clampColor(r), clampColor(g), clampColor(b), getAlpha()).getRGB();
}

int withAlpha(int color) {
    return ((getAlpha() & 0xFF) << 24) | (color & 0x00FFFFFF);
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
        gl.vertex3(x1, y1, z1);
        gl.vertex3(x2, y1, z1);
        gl.vertex3(x2, y1, z2);
        gl.vertex3(x1, y1, z2);
    } else if (face == 1) {
        gl.vertex3(x1, y2, z1);
        gl.vertex3(x1, y2, z2);
        gl.vertex3(x2, y2, z2);
        gl.vertex3(x2, y2, z1);
    } else if (face == 2) {
        gl.vertex3(x1, y1, z1);
        gl.vertex3(x1, y2, z1);
        gl.vertex3(x2, y2, z1);
        gl.vertex3(x2, y1, z1);
    } else if (face == 3) {
        gl.vertex3(x1, y1, z2);
        gl.vertex3(x2, y1, z2);
        gl.vertex3(x2, y2, z2);
        gl.vertex3(x1, y2, z2);
    } else if (face == 4) {
        gl.vertex3(x1, y1, z1);
        gl.vertex3(x1, y1, z2);
        gl.vertex3(x1, y2, z2);
        gl.vertex3(x1, y2, z1);
    } else {
        gl.vertex3(x2, y1, z1);
        gl.vertex3(x2, y2, z1);
        gl.vertex3(x2, y2, z2);
        gl.vertex3(x2, y1, z2);
    }
}

void drawEntityShade(Entity entity, float partialTicks, int color) {
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
    applyGlColor(color);

    gl.begin(7);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 0);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 1);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 2);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 3);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 4);
    vertexBoxFace(x1, y1, z1, x2, y2, z2, 5);
    gl.end();

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

int customColor() {
    int r = clampColor(modules.getSlider(scriptName, "Red"));
    int g = clampColor(modules.getSlider(scriptName, "Green"));
    int b = clampColor(modules.getSlider(scriptName, "Blue"));

    return makeColor(r, g, b);
}

int gradientColor() {
    int r1 = clampColor(modules.getSlider(scriptName, "Red"));
    int g1 = clampColor(modules.getSlider(scriptName, "Green"));
    int b1 = clampColor(modules.getSlider(scriptName, "Blue"));
    int r2 = clampColor(modules.getSlider(scriptName, "Secondary Red"));
    int g2 = clampColor(modules.getSlider(scriptName, "Secondary Green"));
    int b2 = clampColor(modules.getSlider(scriptName, "Secondary Blue"));

    long speed = (long) modules.getSlider(scriptName, "Transition Speed");
    long cycle = speed * 2L;
    long time = client.time() % cycle;
    double t = time < speed ? (double) time / speed : (double) (cycle - time) / speed;

    int r = clampColor(r1 + (r2 - r1) * t);
    int g = clampColor(g1 + (g2 - g1) * t);
    int b = clampColor(b1 + (b2 - b1) * t);

    return makeColor(r, g, b);
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

void onRenderWorld(float partialTicks) {
    if (!modules.isEnabled("KillAura")) return;

    Entity target = modules.getKillAuraTarget();
    if (target == null) return;

    boolean teamColour = modules.getButton(scriptName, "Team Colour");
    boolean secondColor = modules.getButton(scriptName, "Second Color");

    int color;

    if (teamColour) {
        color = withAlpha(getPlayerColour(target));
    } else if (secondColor) {
        color = gradientColor();
    } else {
        color = customColor();
    }

    drawEntityShade(target, partialTicks, color);
}