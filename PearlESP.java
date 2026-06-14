Map<Integer, Vec3>       cachedLanding    = new HashMap<>();
Map<Integer, Vec3>       pendingLanding   = new HashMap<>();
Map<Integer, Integer>    landingAgreement = new HashMap<>();
Map<Integer, Integer>    missingLandingAgreement = new HashMap<>();
Map<Integer, List<Vec3>> cachedTrajectory = new HashMap<>();
Map<Integer, List<Vec3>> pearlBreadcrumbs = new HashMap<>();
Map<Integer, Float> pearlAlpha = new HashMap<>();
Map<Integer, Vec3> predictedVelocity = new HashMap<>();
Map<Integer, Vec3> lastPredictedPosition = new HashMap<>();
int LANDING_CONFIRMATIONS = 3;
int MISSING_LANDING_CONFIRMATIONS = 5;

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
    resetPearls();
}

void onDisable() {
    resetPearls();
}

void onWorldJoin(Entity entity) {
    if (entity != null && entity.isUser) resetPearls();
}

void resetPearls() {
    cachedLanding.clear();
    pendingLanding.clear();
    landingAgreement.clear();
    missingLandingAgreement.clear();
    cachedTrajectory.clear();
    pearlBreadcrumbs.clear();
    pearlAlpha.clear();
    predictedVelocity.clear();
    lastPredictedPosition.clear();
}

void updateStableLanding(int id, Vec3 landing) {
    if (landing == null) {
        pendingLanding.remove(id);
        landingAgreement.remove(id);
        Integer previousMissing = missingLandingAgreement.get(id);
        int missing = previousMissing == null ? 1 : previousMissing + 1;
        if (missing >= MISSING_LANDING_CONFIRMATIONS) {
            cachedLanding.remove(id);
            missingLandingAgreement.remove(id);
        } else {
            missingLandingAgreement.put(id, missing);
        }
        return;
    }

    missingLandingAgreement.remove(id);

    Vec3 displayed = cachedLanding.get(id);
    if (displayed != null && displayed.equals(landing)) {
        pendingLanding.remove(id);
        landingAgreement.remove(id);
        return;
    }

    Vec3 pending = pendingLanding.get(id);
    int agreements = 1;
    if (pending != null && pending.equals(landing)) {
        Integer previous = landingAgreement.get(id);
        agreements = (previous == null ? 1 : previous) + 1;
    } else {
        pendingLanding.put(id, landing);
    }

    if (agreements >= LANDING_CONFIRMATIONS) {
        cachedLanding.put(id, landing);
        pendingLanding.remove(id);
        landingAgreement.remove(id);
    } else {
        landingAgreement.put(id, agreements);
    }
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
int MAX_PREDICTION_TICKS = 240;
int MAX_COLLISION_SUBSTEPS = 12;

boolean isSolid(int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    if (block == null) return false;
    String n  = block.name == null ? "" : block.name.toLowerCase();
    String tp = block.type == null ? "" : block.type.toLowerCase();
    if (n.contains("air") || tp.contains("air")) return false;
    if (n.equals("") || n.equals("minecraft:air")) return false;
    if (n.contains("water") || n.contains("lava") || tp.contains("liquid")) return false;
    if (n.contains("tallgrass") || n.contains("double_plant") || n.contains("flower")
        || n.contains("deadbush") || n.contains("vine") || n.contains("fire")) return false;
    return true;
}

boolean collidesAt(double x, double y, double z) {
    int blockX = (int)Math.floor(x);
    int blockY = (int)Math.floor(y);
    int blockZ = (int)Math.floor(z);
    if (!isSolid(blockX, blockY, blockZ)) return false;

    Block block = world.getBlockAt(blockX, blockY, blockZ);
    if (block == null) return false;

    double width = block.width > 0.0 ? Math.min(1.0, block.width) : 1.0;
    double length = block.length > 0.0 ? Math.min(1.0, block.length) : 1.0;
    double height = block.height > 0.0 ? Math.min(1.0, block.height) : 1.0;
    double localX = x - blockX;
    double localY = y - blockY;
    double localZ = z - blockZ;
    double minX = (1.0 - width) * 0.5;
    double minZ = (1.0 - length) * 0.5;

    return localX >= minX && localX <= minX + width
        && localY >= 0.0 && localY <= height
        && localZ >= minZ && localZ <= minZ + length;
}

boolean isWaterAt(Vec3 position) {
    Block block = world.getBlockAt(
        (int)Math.floor(position.x),
        (int)Math.floor(position.y),
        (int)Math.floor(position.z)
    );
    if (block == null) return false;
    String name = block.name == null ? "" : block.name.toLowerCase();
    String type = block.type == null ? "" : block.type.toLowerCase();
    return name.contains("water") || type.contains("water");
}

Vec3 advancePearlVelocity(Vec3 velocity, Vec3 position) {
    double drag = isWaterAt(position) ? 0.8 : DRAG;
    return new Vec3(
        velocity.x * drag,
        velocity.y * drag - GRAVITY,
        velocity.z * drag
    );
}

Object[] predictTrajectory(Vec3 pos, Vec3 vel) {
    List<Vec3> pts = new ArrayList<>();
    double px = pos.x, py = pos.y, pz = pos.z;
    double vx = vel.x, vy = vel.y, vz = vel.z;
    pts.add(new Vec3(px, py, pz));

    for (int step = 0; step < MAX_PREDICTION_TICKS; step++) {
        double sx = px, sy = py, sz = pz;
        double nx = px + vx, ny = py + vy, nz = pz + vz;
        double largestAxis = Math.max(Math.abs(vx), Math.max(Math.abs(vy), Math.abs(vz)));
        int substeps = Math.max(2, Math.min(MAX_COLLISION_SUBSTEPS, (int)Math.ceil(largestAxis / 0.18)));

        for (int sub = 1; sub <= substeps; sub++) {
            double t = sub / (double) substeps;
            double cx = sx + (nx - sx) * t;
            double cy = sy + (ny - sy) * t;
            double cz = sz + (nz - sz) * t;
            pts.add(new Vec3(cx, cy, cz));
            if (collidesAt(cx, cy, cz)) {
                Vec3 landing = new Vec3(
                    (int)Math.floor(cx),
                    (int)Math.floor(cy),
                    (int)Math.floor(cz)
                );
                return new Object[]{landing, pts};
            }
            if (cy < -64) return new Object[]{null, pts};
        }

        px = nx; py = ny; pz = nz;
        double drag = isWaterAt(new Vec3(px, py, pz)) ? 0.8 : DRAG;
        vx *= drag;
        vy = vy * drag - GRAVITY;
        vz *= drag;
        if (ny < -64) return new Object[]{null, pts};
    }
    return new Object[]{null, pts};
}

void appendBreadcrumb(int id, Vec3 previous, Vec3 current) {
    List<Vec3> breadcrumbs = pearlBreadcrumbs.get(id);
    if (breadcrumbs == null) {
        breadcrumbs = new ArrayList<>();
        pearlBreadcrumbs.put(id, breadcrumbs);
        breadcrumbs.add(new Vec3(previous.x, previous.y, previous.z));
    }

    Vec3 lastPoint = breadcrumbs.get(breadcrumbs.size() - 1);
    double dx = current.x - lastPoint.x;
    double dy = current.y - lastPoint.y;
    double dz = current.z - lastPoint.z;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance < 0.001) return;

    int segments = Math.max(1, Math.min(12, (int)Math.ceil(distance / 0.12)));
    for (int i = 1; i <= segments; i++) {
        double t = i / (double)segments;
        breadcrumbs.add(new Vec3(
            lastPoint.x + dx * t,
            lastPoint.y + dy * t,
            lastPoint.z + dz * t
        ));
    }
}

List<Vec3> combineTrajectory(int id, List<Vec3> prediction) {
    List<Vec3> combined = new ArrayList<>();
    List<Vec3> breadcrumbs = pearlBreadcrumbs.get(id);
    if (breadcrumbs != null) combined.addAll(breadcrumbs);

    int start = combined.isEmpty() ? 0 : 1;
    for (int i = start; i < prediction.size(); i++) {
        combined.add(prediction.get(i));
    }
    return combined;
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

        if (e.getTicksExisted() < 2) continue;

        Vec3 last = e.getLastPosition();
        appendBreadcrumb(id, last, pos);
        Vec3 observedVelocity = new Vec3(pos.x - last.x, pos.y - last.y, pos.z - last.z);
        double speed = Math.sqrt(
            observedVelocity.x * observedVelocity.x
            + observedVelocity.y * observedVelocity.y
            + observedVelocity.z * observedVelocity.z
        );
        if (speed < 0.01) continue;

        Vec3 previousPosition = lastPredictedPosition.get(id);
        if (previousPosition != null && previousPosition.distanceToSq(pos) < 0.00000001) continue;

        Vec3 nextVelocity = advancePearlVelocity(observedVelocity, pos);
        Vec3 previousVelocity = predictedVelocity.get(id);
        if (previousVelocity != null) {
            Vec3 expectedVelocity = advancePearlVelocity(previousVelocity, pos);
            double errorX = nextVelocity.x - expectedVelocity.x;
            double errorY = nextVelocity.y - expectedVelocity.y;
            double errorZ = nextVelocity.z - expectedVelocity.z;
            double errorSq = errorX * errorX + errorY * errorY + errorZ * errorZ;

            if (errorSq < 0.09) {
                nextVelocity = new Vec3(
                    nextVelocity.x * 0.82 + expectedVelocity.x * 0.18,
                    nextVelocity.y * 0.82 + expectedVelocity.y * 0.18,
                    nextVelocity.z * 0.82 + expectedVelocity.z * 0.18
                );
            }
        }

        Object[] prediction = predictTrajectory(pos, nextVelocity);
        Vec3 landing = (Vec3) prediction[0];
        List<Vec3> trajectory = (List<Vec3>) prediction[1];

        predictedVelocity.put(id, nextVelocity);
        lastPredictedPosition.put(id, pos);
        cachedTrajectory.put(id, combineTrajectory(id, trajectory));

        updateStableLanding(id, landing);
    }

    for (Integer id : pearlBreadcrumbs.keySet()) {
        if (live.contains(id)) continue;
        Float alpha = pearlAlpha.get(id);
        float faded = (alpha == null ? 1.0f : alpha) - 0.14f;
        if (faded <= 0.0f) {
            remove.add(id);
        } else {
            pearlAlpha.put(id, faded);
        }
    }
    for (Integer id : remove) {
        cachedLanding.remove(id);
        pendingLanding.remove(id);
        landingAgreement.remove(id);
        missingLandingAgreement.remove(id);
        cachedTrajectory.remove(id);
        pearlBreadcrumbs.remove(id);
        pearlAlpha.remove(id);
        predictedVelocity.remove(id);
        lastPredictedPosition.remove(id);
    }
}

void onRenderWorld(float partialTicks) {
    boolean showBlock = modules.getButton(scriptName, "Outline block");
    boolean showShade = modules.getButton(scriptName, "Shade block");
    boolean showTrail = modules.getButton(scriptName, "Trajectory line");
    float lineWidth = (float) modules.getSlider(scriptName, "Line width");

    for (Integer id : cachedTrajectory.keySet()) {
        Vec3 landing = cachedLanding.get(id);

        Float rawAlpha = pearlAlpha.get(id);
        float fa = rawAlpha != null ? rawAlpha : 1.0f;

        int colBlock = themeColor((int)(fa * 180));
        int colShade = themeColorDim((int)(fa * 60));

        if (showTrail) {
            List<Vec3> pts = cachedTrajectory.get(id);
            drawSmoothTrajectory(pts, lineWidth, fa);
        }

        if (landing != null && showBlock) {
            render.block(landing, colBlock, true, false);
        }

        if (landing != null && showShade) {
            render.block(landing, colShade, false, true);
        }
    }
}
