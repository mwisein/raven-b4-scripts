Map<Integer, Vec3>       cachedLanding    = new HashMap<>();
Map<Integer, List<Vec3>> cachedTrajectory = new HashMap<>();
Map<Integer, Float> pearlAlpha = new HashMap<>();

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
    modules.registerDescription("Trajectories for pearls.");
    modules.registerSlider("Theme",           "", 0, themeOptions);
    modules.registerSlider("Line width",      "px", 1.5, 0.5, 3.0, 0.5);
    modules.registerButton("Outline block",   true);
    modules.registerButton("Shade block",   true);
    modules.registerButton("Trajectory line", true);
}

void onEnable() {
    cachedLanding.clear();
    cachedTrajectory.clear();
    pearlAlpha.clear();
}

void onDisable() {
    cachedLanding.clear();
    cachedTrajectory.clear();
    pearlAlpha.clear();
}


int clampInt(int v, int lo, int hi) {
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

int themeColor(int alpha) {
    int base = getThemeColor(resolveTheme());
    return withAlpha(base, alpha);
}

int themeColorDim(int alpha) {
    int base = getThemeColor(resolveTheme());
    int r = (base >> 16) & 0xFF;
    int g = (base >> 8) & 0xFF;
    int b = base & 0xFF;
    return withAlpha(0xFF000000 | ((int)(r*0.6) << 16) | ((int)(g*0.6) << 8) | (int)(b*0.6), alpha);
}

double DRAG    = 0.99;
double GRAVITY = 0.03;
int TRAJECTORY_SUBSTEPS = 8;

boolean isSolid(int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    if (block == null) return false;
    String n  = block.name == null ? "" : block.name.toLowerCase();
    String tp = block.type == null ? "" : block.type.toLowerCase();
    if (n.contains("air") || tp.contains("air")) return false;
    if (n.equals("") || n.equals("minecraft:air")) return false;
    return true;
}

Vec3 simulateLanding(Vec3 pos, Vec3 vel) {
    double px = pos.x, py = pos.y, pz = pos.z;
    double vx = vel.x, vy = vel.y, vz = vel.z;
    for (int step = 0; step < 300; step++) {
        double sx = px, sy = py, sz = pz;
        vx *= DRAG; vy *= DRAG; vz *= DRAG;
        vy -= GRAVITY;
        double nx = px+vx, ny = py+vy, nz = pz+vz;

        for (int sub = 1; sub <= TRAJECTORY_SUBSTEPS; sub++) {
            double t = sub / (double) TRAJECTORY_SUBSTEPS;
            double cx = sx + (nx - sx) * t;
            double cy = sy + (ny - sy) * t;
            double cz = sz + (nz - sz) * t;
            if (isSolid((int)Math.floor(cx), (int)Math.floor(cy), (int)Math.floor(cz)))
                return new Vec3((int)Math.floor(cx), (int)Math.floor(cy), (int)Math.floor(cz));
            if (cy < -64) return null;
        }

        if (ny < -64) return null;
        px = nx; py = ny; pz = nz;
    }
    return null;
}

List<Vec3> buildTrajectory(Vec3 pos, Vec3 vel) {
    List<Vec3> pts = new ArrayList<>();
    double px = pos.x, py = pos.y, pz = pos.z;
    double vx = vel.x, vy = vel.y, vz = vel.z;
    pts.add(new Vec3(px, py, pz));
    for (int step = 0; step < 300; step++) {
        double sx = px, sy = py, sz = pz;
        vx *= DRAG; vy *= DRAG; vz *= DRAG;
        vy -= GRAVITY;
        double nx = px+vx, ny = py+vy, nz = pz+vz;

        for (int sub = 1; sub <= TRAJECTORY_SUBSTEPS; sub++) {
            double t = sub / (double) TRAJECTORY_SUBSTEPS;
            double cx = sx + (nx - sx) * t;
            double cy = sy + (ny - sy) * t;
            double cz = sz + (nz - sz) * t;

            pts.add(new Vec3(cx, cy, cz));
            if (isSolid((int)Math.floor(cx), (int)Math.floor(cy), (int)Math.floor(cz))) {
                return pts;
            }
            if (cy < -64) return pts;
        }

        if (ny < -64) break;
        px = nx; py = ny; pz = nz;
    }
    return pts;
}

void drawSmoothTrajectory(List<Vec3> pts, float lineWidth, float alpha) {
    if (pts == null || pts.size() < 2) return;

    Vec3 cam = render.getPosition();
    if (cam == null) return;

    int base = getThemeColor(resolveTheme());
    int r = (base >> 16) & 0xFF;
    int g = (base >> 8) & 0xFF;
    int b = base & 0xFF;
    int sz = pts.size();

    gl.push();
    gl.blend(true);
    gl.texture2d(false);
    gl.lineSmooth(true);
    gl.depth(false);
    gl.lineWidth(lineWidth);
    gl.translate(-cam.x, -cam.y, -cam.z);
    gl.begin(3);

    for (int i = 0; i < sz; i++) {
        float frac = (float) i / Math.max(1, sz - 1);
        int a = clampInt((int)(alpha * (255 - frac * 180)), 0, 255);
        gl.color(r, g, b, a);
        Vec3 p = pts.get(i);
        gl.vertex3(p.x, p.y, p.z);
    }

    gl.end();
    gl.lineSmooth(false);
    gl.texture2d(true);
    gl.depth(true);
    gl.blend(false);
    gl.resetColor();
    gl.pop();
}


void onPreUpdate() {
    Vec3 camPos = render.getPosition();
    HashSet<Integer> live = new HashSet<>();
    List<Integer> remove = new ArrayList<>();
    for (Entity e : world.getEntities()) {
        if (e == null || e.isDead()) continue;
        String t = e.type;
        if (!t.equals("EntityEnderPearl") && !t.equals("EntityThrownEnderpearl")) continue;

        Vec3 pos = e.getPosition();
        if (camPos.distanceTo(pos) > 512.0) continue;

        int id = e.entityId;
        live.add(id);

        Float alpha = pearlAlpha.get(id);
        if (alpha == null) alpha = 0.0f;
        alpha = Math.min(1.0f, alpha + 0.12f);
        pearlAlpha.put(id, alpha);

        if (cachedLanding.containsKey(id)) continue;
        if (e.getTicksExisted() < 2) continue;

        Vec3 last = e.getLastPosition();
        Vec3 vel = new Vec3(pos.x - last.x, pos.y - last.y, pos.z - last.z);
        double speed = Math.sqrt(vel.x*vel.x + vel.y*vel.y + vel.z*vel.z);
        if (speed < 0.01) continue;

        Vec3 landing = simulateLanding(pos, vel);
        if (landing == null) continue;

        cachedLanding.put(id, landing);
        cachedTrajectory.put(id, buildTrajectory(pos, vel));
    }

    for (Integer id : cachedLanding.keySet()) {
        if (!live.contains(id)) remove.add(id);
    }
    for (Integer id : remove) {
        cachedLanding.remove(id);
        cachedTrajectory.remove(id);
        pearlAlpha.remove(id);
    }
}

void onRenderWorld(float partialTicks) {
    boolean showBlock = modules.getButton(scriptName, "Outline block");
    boolean showShade = modules.getButton(scriptName, "Shade block");
    boolean showTrail = modules.getButton(scriptName, "Trajectory line");
    float lineWidth = (float) modules.getSlider(scriptName, "Line width");

    for (Integer id : cachedLanding.keySet()) {
        Vec3 landing = cachedLanding.get(id);
        if (landing == null) continue;

        Float rawAlpha = pearlAlpha.get(id);
        float fa = rawAlpha != null ? rawAlpha : 1.0f;
        int a255 = (int)(fa * 255);

        int colBlock = themeColor((int)(fa * 180));
        int colShade = themeColorDim((int)(fa * 60));

        if (showTrail) {
            List<Vec3> pts = cachedTrajectory.get(id);
            drawSmoothTrajectory(pts, lineWidth, fa);
        }

        if (showBlock) {
            render.block(landing, colBlock, true, false);
        }

        if (showShade) {
            render.block(landing, colShade, false, true);
        }
    }
}
