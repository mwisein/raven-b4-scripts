String hypixelApiKey = "";
String urchinApiKey = "";

String[] themeOptions = {
    "Default","Rainbow","Aurora","Cherry","Cotton Candy",
    "Flare","Flower","Forest","Frost","Gold",
    "Grayscale","Inferno","Royal","Sandstorm","Sky","Vine"
};

String[] settingsThemeMap = {
    "Rainbow","Aurora","Cherry","Cotton candy","Flare","Flower","Forest","Frost",
    "Gold","Grayscale","Inferno","Royal","Sandstorm","Sky","Vine"
};

String[] showBindOptions = {
    "Tab","Right Shift","Grave","Comma","Period","Left Ctrl","Left Alt",
    "Z","X","C","V","B","N","M","Q","E","R","T","Y","U","I","O","P",
    "A","S","D","F","G","H","J","K","L",
    "1","2","3","4","5","6","7","8","9","0",
    "Minus","Equals","Left Bracket","Right Bracket","Backslash",
    "Semicolon","Apostrophe","Slash",
    "F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12",
    "Escape","Backspace","Enter","Space",
    "Left Shift","Caps Lock",
    "Right Ctrl","Right Alt",
    "Up","Down","Left","Right",
    "Insert","Delete","Home","End","Page Up","Page Down"
};

Map<String, Map<String, Object>> statsCache = new ConcurrentHashMap<>();
Map<String, String> urchinCache = new ConcurrentHashMap<>();
Map<String, Map<String, String>> permanentTagCache = new ConcurrentHashMap<>();
Map<String, Boolean> pendingStatsRequests = new ConcurrentHashMap<>();
Map<String, Integer> statsFailureCounts = new ConcurrentHashMap<>();
Map<String, Boolean> pendingWhoResolves = new ConcurrentHashMap<>();
ArrayList<Long> statsRequestTimes = new ArrayList<>();
final Object statsRateLock = new Object();
final long statsRateWindowMs = 60000L;
final int statsRateLimit = 110;
final int statsRateBuffer = 8;
final long statsGlobalCooldownMs = 15000L;
final long statsRetryBaseMs = 7000L;
long statsGlobalCooldownUntil = 0L;

final String prismBaseUrl = "https://flashlight.prismoverlay.com";
final String prismPlayerdataUrl = prismBaseUrl + "/v1/playerdata";
final String prismAccountUrl = prismBaseUrl + "/v1/account/username/";
final String prismTagsUrl = prismBaseUrl + "/v1/tags/";
final String hypixelPlayerdataUrl = "https://api.hypixel.net/v2/player?uuid=";
String prismUserId = "";

final static String starKey = "star",
                    fkdrKey = "fkdr",
                    wlrKey = "wlr",
                    winsKey = "wins",
                    winstreakKey = "winstreaks",
                    sessionKey = "session";

final static String starValue = "starvalue",
                    fkdrValue = "fkdrvalue",
                    wlrValue = "wlrvalue",
                    winsValue = "winsvalue",
                    indexValue = "indexvalue",
                    sessionValue = "sessionvalue",
                    winstreakValue = "winstreakvalue";

final static String requestStateKey = "requeststate";

boolean overlayToggleVisible = false;
boolean showBindWasDown = false;

long getRetryDelay(int failures, long baseDelay, long maxDelay) {
    int clampedFailures = Math.max(0, Math.min(6, failures));
    long delay = baseDelay * (1L << clampedFailures);
    return Math.min(maxDelay, delay);
}

void markStatsFailure(String uuid, String state) {
    int failures = statsFailureCounts.getOrDefault(uuid, 0) + 1;
    statsFailureCounts.put(uuid, failures);
    long retryDelay = "rate".equals(state) ? Math.max(statsGlobalCooldownMs, getRetryDelay(failures, statsRetryBaseMs, 180000L)) : getRetryDelay(failures, statsRetryBaseMs, 180000L);
    Map<String, Object> failed = new ConcurrentHashMap<>();
    failed.put("error", true);
    failed.put(requestStateKey, state);
    failed.put("cachetime", client.time() + retryDelay);
    statsCache.put(uuid, failed);
}

void markStatsSuccess(String uuid) { statsFailureCounts.remove(uuid); }

String formatRetryCountdown(String prefix, char color, Map<String, Object> playerStats) {
    if (playerStats == null) return util.colorSymbol + color + prefix;
    long retryAt = playerStats.get("cachetime") instanceof Number ? ((Number) playerStats.get("cachetime")).longValue() : 0L;
    long remainingMs = retryAt - client.time();
    if (remainingMs <= 0L) return util.colorSymbol + color + prefix;
    long remainingSec = Math.max(1L, (remainingMs + 999L) / 1000L);
    if (remainingSec >= 60L) {
        long mins = remainingSec / 60L;
        long secs = remainingSec % 60L;
        return util.colorSymbol + color + prefix + mins + "m" + secs + "s";
    }
    return util.colorSymbol + color + prefix + remainingSec + "s";
}

String getPrismUserId() {
    if (prismUserId != null && !prismUserId.isEmpty()) return prismUserId;
    try {
        Entity self = client.getPlayer();
        if (self != null) {
            String selfUuid = self.getUUID();
            if (selfUuid != null && !selfUuid.isEmpty()) {
                prismUserId = selfUuid.replace("-", "");
                if (!prismUserId.isEmpty()) return prismUserId;
            }
        }
    } catch (Exception ignored) {}
    try {
        prismUserId = "lazify_" + Long.toHexString(client.time()) + "_" + Integer.toHexString(new Random().nextInt(Integer.MAX_VALUE));
    } catch (Exception ignored) {
        prismUserId = "lazify_" + client.time();
    }
    return prismUserId;
}

Map<String, String> createPrismHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("X-User-Id", getPrismUserId());
    return headers;
}

boolean useHypixelApi() { return hypixelApiKey != null && !hypixelApiKey.trim().isEmpty(); }
boolean hasApiKey(String key) { return key != null && !key.trim().isEmpty(); }

void printApiStatus() {
    String hypixelStatus = !useHypixelApi() ? "&8not set &7(Prism stats)"
        : hypixelKeyInvalid ? "&ckey invalid &8(Prism fallback)"
        : "&aconnected &8(Hypixel stats)";
    String urchinStatus = !hasApiKey(urchinApiKey) ? "&8not set"
        : urchinKeyInvalid ? "&ckey invalid"
        : "&aconnected";
    client.print(formatOverlayMessage("&8\u00bb &fAPI Status"));
    client.print(util.color("  &6Hypixel  &8\u2022 " + hypixelStatus));
    client.print(util.color("  &5Urchin   &8\u2022 " + urchinStatus));
}

Map<String, String> createHypixelHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put("API-Key", hypixelApiKey.trim());
    return headers;
}

String readConfigString(String key) {
    try { String value = config.get(key); return value == null ? "" : value.trim(); } catch (Exception ignored) {}
    return "";
}

void loadApiKeys() {
    hypixelApiKey = readConfigString("overlay_hypixel_key");
    urchinApiKey = readConfigString("overlay_urchin_key");
    urchinKeyInvalid = false;
    hypixelKeyInvalid = false;
}

void saveApiKey(String configKey, String value) {
    config.set(configKey, value == null ? "" : value.trim());
}

void onLoad() {
    loadApiKeys();
    loadPermanentTagCache();

    columns.clear();
    tags.clear();

    addColumn("Username", "Player", playerKey);
    addColumn("FKDR", "FKDR", fkdrKey);
    addColumn("Winstreaks", "WS", winstreakKey);
    addColumn("WLR", "WLR", wlrKey);
    addColumn("Wins", "Wins", winsKey);
    addColumn("Session", "Session", sessionKey);
    addColumn("Tags", "Tags", tagsKey);

    addTag("cheating");
    addTag("sniping");
    addTag("urchin");

    registerDefaultButtons();
    loadOverlayHud();
    defaultSettings();
    doColumns();

    client.print(formatOverlayMessage("&8> &fView commands using &f/overlay&7, &f/ov"));
}

void onPlayerAdd(String uuid) {
    handlePlayerStats(uuid, getLobbyId());
    handleUrchinTag(uuid, getLobbyId());
}

void onManualPlayerAdd(String uuid) {
    statsCache.remove(uuid);
    urchinCache.remove(uuid);
    handlePlayerStats(uuid, getLobbyId());
    handleUrchinTag(uuid, getLobbyId());
}

void handlePlayerStats(String uuid, String lobby) {
    Map<String, Object> cachedStats = statsCache.get(uuid);
    long cacheTime = cachedStats != null && cachedStats.get("cachetime") instanceof Number ? ((Number) cachedStats.get("cachetime")).longValue() : 0L;
    if (cachedStats == null || client.time() > cacheTime) {
        if (cachedStats != null) statsCache.remove(uuid);
        if (pendingStatsRequests.putIfAbsent(uuid, true) != null) return;
        client.async(() -> {
            Map<String, Object> playerStats = new ConcurrentHashMap<>();
            try {
                if (!awaitStatsRequestSlot(lobby)) return;
                boolean hypixelMode = useHypixelApi();
                String apiName = hypixelMode ? "Hypixel" : "Flashlight";
                String url = hypixelMode ? hypixelPlayerdataUrl + uuid.replace("-", "") : prismPlayerdataUrl + "?uuid=" + uuid;
                Object[] playerStatsRequest = hypixelMode ? get(url, 3500, createHypixelHeaders()) : get(url, 3500, createPrismHeaders());
                int statusCode = (int) playerStatsRequest[1];
                Json responseJson = (Json) playerStatsRequest[0];

                if (statusCode == 200) {
                    String successValue = responseJson.object().get("success", "false");
                    String causeValue = responseJson.object().get("cause", "");
                    boolean success = "true".equalsIgnoreCase(successValue);
                    boolean throttled = causeValue != null && causeValue.toLowerCase().contains("throttle");

                    if (success) {
                        String fallbackName = getCurrentOverlayName(uuid);
                        playerStats = parseStats(responseJson, uuid);
                        String resolvedUsername = playerStats.get("usernamewithrankcolor") != null ? util.strip(playerStats.get("usernamewithrankcolor").toString()).trim() : "";
                        Map<String, Object> mergedTags = getOrFetchTags(uuid, resolvedUsername);
                        playerStats.putAll(mergedTags);
                        ensurePlayerHead(playerStats, uuid);
                        String overlayName = resolveOverlayName(uuid, playerStats, fallbackName);
                        if (!overlayName.isEmpty()) playerStats.put(playerKey, overlayName);
                        markStatsSuccess(uuid);
                    } else if (throttled) {
                        statsGlobalCooldownUntil = Math.max(statsGlobalCooldownUntil, client.time() + statsGlobalCooldownMs);
                        markStatsFailure(uuid, "rate");
                        client.log(apiName + " throttle while getting stats on " + uuid);
                    } else {
                        client.log(apiName + " returned success=false while getting stats on " + uuid + " cause=" + causeValue);
                        markStatsFailure(uuid, "error");
                    }
                } else if (statusCode == 401 || statusCode == 403) {
                    if (hypixelMode && !hypixelKeyInvalid) {
                        hypixelKeyInvalid = true;
                        client.print(formatOverlayMessage("&8\u00bb &6Hypixel &cAPI key is invalid or expired. Use &f/overlay sethypixel <key> &cto update it."));
                    }
                    markStatsFailure(uuid, "error");
                    client.log(apiName + " key rejected (" + statusCode + ") for " + uuid);
                } else if (statusCode == 429 || statusCode == 503 || statusCode == 504) {
                    statsGlobalCooldownUntil = Math.max(statsGlobalCooldownUntil, client.time() + statsGlobalCooldownMs);
                    markStatsFailure(uuid, "rate");
                    client.log(apiName + " API rate limit/intermittent error (" + statusCode + ") while getting stats on " + uuid);
                } else {
                    client.log(apiName + " HTTP Error " + statusCode + " getting stats on " + uuid);
                    markStatsFailure(uuid, "error");
                }
            } catch (Exception e) {
                client.log("Runtime error getting stats on " + uuid + ": " + e);
                markStatsFailure(uuid, "error");
            } finally {
                pendingStatsRequests.remove(uuid);
            }

            Map<String, Object> overlayUpdate = !playerStats.isEmpty() ? playerStats : statsCache.get(uuid);
            if (overlayUpdate == null) overlayUpdate = playerStats;
            if (isInOverlay(uuid) && !hasChangedLobby(lobby)) addToOverlay(uuid, overlayUpdate);
        });
    } else {
        Map<String, Object> dereference = new ConcurrentHashMap<>(cachedStats);
        ensurePlayerHead(dereference, uuid);
        String overlayName = resolveOverlayName(uuid, dereference, getCurrentOverlayName(uuid));
        if (!overlayName.isEmpty()) dereference.put(playerKey, overlayName);

        long lastLogin = dereference.get("login") instanceof Number ? ((Number) dereference.get("login")).longValue() : 0L;
        long lastLogout = dereference.get("logout") instanceof Number ? ((Number) dereference.get("logout")).longValue() : 0L;
        boolean statusOn = lastLogin != 0;
        String coloredSession = util.colorSymbol + "c--";
        if (statusOn) {
            if (lastLogin - lastLogout > -2000 && client.time() - lastLogin < 43200000) {
                lastLogout = client.time();
                coloredSession = calculateRelativeTimestamp(lastLogin, lastLogout);
                coloredSession = getSessionColor(lastLogin, lastLogout, coloredSession);
            } else {
                coloredSession = util.colorSymbol + "cOFFLINE";
            }
        }
        dereference.put(sessionKey, coloredSession);
        addToOverlay(uuid, dereference);
    }
}

Map<String, Object> parseStats(Json jsonData, String uuid) {
    Map<String, Object> stats = new ConcurrentHashMap<>();
    try {
        Json data = jsonData.object();
        if (jsonData.string().equals("{\"success\":true,\"player\":null}") || !data.object("player").exists()) {
            stats.put("nicked", true);
            stats.put(requestStateKey, "nicked");
            stats.put("cachetime", client.time() + 300000);
            statsCache.put(uuid, stats);
            statsFailureCounts.remove(uuid);
            return stats;
        }

        Json playerObject = data.object("player");

        String username = playerObject.get("displayname", "");
        String formattedRank = getFormattedRank(jsonData);
        String rankColor = formattedRank.length() >= 2 ? formattedRank.substring(0, 2) : util.colorSymbol + "7";
        String formattedUsername = formattedRank.length() == 2 ? formattedRank + username : formattedRank + " " + username;
        String coloredUsername = rankColor + username;
        stats.put("usernamewithrank", formattedUsername);
        stats.put("usernamewithrankcolor", coloredUsername);

        boolean hasBedwarsStats = false;
        if (playerObject.object("stats").exists() && playerObject.object("stats").object("Bedwars").exists()) hasBedwarsStats = true;
        Json bedwarsObject = hasBedwarsStats ? playerObject.object("stats").object("Bedwars") : new Json("{}");

        int star = (int) Math.floor(expToStars((int)Double.parseDouble(bedwarsObject.get("Experience", "0"))));
        String coloredStar = getPrestigeColor(star);

        String fkdrMode = "";
        String fkdrKillsKey = fkdrMode.isEmpty() ? "final_kills_bedwars" : fkdrMode + "final_kills_bedwars";
        String fkdrDeathsKey = fkdrMode.isEmpty() ? "final_deaths_bedwars" : fkdrMode + "final_deaths_bedwars";
        double finalKills = Double.parseDouble(bedwarsObject.get(fkdrKillsKey, "0"));
        double finalDeaths = Double.parseDouble(bedwarsObject.get(fkdrDeathsKey, "0"));
        double fkdr = finalDeaths == 0 ? finalKills : finalKills / finalDeaths < 10 ? util.round(finalKills / finalDeaths, 2) : util.round(finalKills / finalDeaths, 1);
        double index = star * Math.pow(fkdr, 2);
        String coloredFkdr = getFkdrColor(formatDoubleStr(fkdr));

        double wins = Double.parseDouble(bedwarsObject.get("wins_bedwars", "0"));
        double losses = Double.parseDouble(bedwarsObject.get("losses_bedwars", "0"));
        double wlr = losses == 0 ? wins : wins / losses < 10 ? util.round(wins / losses, 2) : util.round(wins / losses, 1);
        String coloredWlr = getFkdrColor(formatDoubleStr(wlr));
        String coloredWins = util.colorSymbol + (wins >= 1000 ? "b" : wins >= 500 ? "a" : wins >= 100 ? "f" : "7") + String.valueOf((int) wins);

        long lastLogin = Long.parseLong(playerObject.get("lastLogin", "0"));
        long lastLogout = Long.parseLong(playerObject.get("lastLogout", "0"));
        stats.put("login", lastLogin);
        stats.put("logout", lastLogout);
        boolean statusOn = lastLogin != 0;
        String coloredSession = util.colorSymbol + "c--";
        if (statusOn) {
            if (lastLogin - lastLogout > -2000) {
                lastLogout = client.time();
                coloredSession = calculateRelativeTimestamp(lastLogin, lastLogout);
                coloredSession = getSessionColor(lastLogin, lastLogout, coloredSession);
            } else {
                coloredSession = util.colorSymbol + "cOFFLINE";
            }
        }

        String winstreakMode = "";
        boolean winstreaksDisabled = Integer.parseInt(bedwarsObject.get("games_played_bedwars_1", "0")) > 0 && bedwarsObject.get(winstreakMode + "winstreak", "").isEmpty();
        int winstreak = 0;
        String coloredWinstreak;
        if (winstreaksDisabled) {
            coloredWinstreak = util.colorSymbol + "c--";
        } else {
            winstreak = Integer.parseInt(bedwarsObject.get(winstreakMode + "winstreak", "0"));
            coloredWinstreak = getWinstreakColor(String.valueOf(winstreak));
        }

        boolean highWinstreak = winstreak > 50;

        stats.put(starKey, coloredStar);
        stats.put(starValue, star);
        stats.put(fkdrKey, coloredFkdr);
        stats.put(fkdrValue, fkdr);
        stats.put(wlrKey, coloredWlr);
        stats.put(wlrValue, wlr);
        stats.put(winsKey, coloredWins);
        stats.put(winsValue, wins);
        stats.put(winstreakKey, coloredWinstreak);
        stats.put(winstreakValue, winstreak);
        stats.put(sessionKey, coloredSession);
        stats.put(sessionValue, lastLogin * -1);
        stats.put(indexValue, index);
        stats.put(tagsKey, "");
        stats.put("cheating", "");
        stats.put("sniping", "");
        stats.put("urchin", "");
        stats.put(requestStateKey, "ready");
        long CACHE_DURATION = highWinstreak ? 600000 : Math.max(300, Math.min(86400, 60 * (60 * ((int) finalDeaths / 120)))) * 1000L;
        stats.put("cachetime", client.time() + CACHE_DURATION);
        statsCache.put(uuid, stats);
        statsFailureCounts.remove(uuid);
    } catch (Exception e) {
        client.log("Error in parseStats function on " + uuid + ": " + e);
        stats.put("error", true);
        stats.put(requestStateKey, "error");
    }
    return stats;
}

String renderPrismTag(String tagChar, String severity) {
    if (severity == null) return "";
    String lowered = severity.toLowerCase();
    if (lowered.equals("none")) return "";
    if (lowered.equals("medium")) return util.colorSymbol + "6" + tagChar;
    if (lowered.equals("high")) return util.colorSymbol + "c" + tagChar;
    return "";
}

Map<String, Object> fetchPrismTags(String uuid, String username) {
    Map<String, Object> tagData = new ConcurrentHashMap<>();
    tagData.put("cheating", "");
    tagData.put("sniping", "");
    if (uuid == null || uuid.isEmpty()) return tagData;
    try {
        Object[] response = get(prismTagsUrl + uuid, 3000, createPrismHeaders());
        int statusCode = (int) response[1];
        if (statusCode != 200) return tagData;
        Json json = ((Json) response[0]).object();
        Json tagsObject = json.object("tags");
        if (!tagsObject.exists()) return tagData;
        String cheatingSeverity = tagsObject.get("cheating", "none");
        String snipingSeverity = tagsObject.get("sniping", "none");
        String cheatingTag = renderPrismTag("C", cheatingSeverity);
        String snipingTag = snipingSeverity != null && !snipingSeverity.toLowerCase().equals("none") && !snipingSeverity.isEmpty()
            ? util.colorSymbol + "e" + "S" : "";
        if (!cheatingTag.isEmpty() && !snipingTag.isEmpty()) cheatingTag += " ";
        tagData.put("cheating", cheatingTag);
        tagData.put("sniping", snipingTag);
    } catch (Exception e) {
        client.log("Runtime error getting prism tags on " + uuid + ": " + e);
    }
    return tagData;
}

void handleUrchinTag(String uuid, String lobby) {
    if (urchinCache.containsKey(uuid)) return;
    if (urchinApiKey == null || urchinApiKey.trim().isEmpty()) return;
    urchinCache.put(uuid, "pending");

    client.async(() -> {
        try {
            String url = "https://urchin.ws/player/" + uuid + "?key=" + urchinApiKey.trim() + "&sources=GAME,MANUAL,PARTY,CHAT";
            Object[] response = get(url, 3500);
            int statusCode = (int) response[1];

            if (statusCode == 200) {
                Json jsonData = (Json) response[0];
                List<Json> tagsArray = jsonData.object().array("tags");

                if (tagsArray != null && tagsArray.size() > 0) {
                    Json firstTag = tagsArray.get(0);
                    String tagType = firstTag.object().get("type", "");
                    String reason = firstTag.object().get("reason", "");

                    if (!tagType.isEmpty()) {
                        String username = resolveUsernameForUuid(uuid);
                        String coloredTag = getUrchinTagColor(tagType);
                        urchinCache.put(uuid, tagType);

                        Map<String, Object> tagData = new ConcurrentHashMap<>();
                        tagData.put("urchin", coloredTag);

                        if (isInOverlay(uuid) && !hasChangedLobby(lobby)) addToOverlay(uuid, tagData);

                        if (modules.getButton(scriptName, "Print urchin reason")) {
                            String teamDisplay = teams.getOrDefault(uuid, "");
                            char teamColor = teamDisplay.isEmpty() ? 'f' : getTeamColorCode(teamDisplay);
                            if (teamColor == ' ') teamColor = 'f';
                            String coloredName = util.colorSymbol + teamColor + (username.isEmpty() ? uuid : username);
                            String msg = "&5&lUrchin &8\u00bb " + coloredName + " &8\u2022 &5" + tagType;
                            if (!reason.isEmpty()) msg += " &7" + reason;
                            client.print(util.color(msg));
                        }
                    }
                } else {
                    urchinCache.put(uuid, "none");
                }
            } else if (statusCode == 401 || statusCode == 403) {
                if (!urchinKeyInvalid) {
                    urchinKeyInvalid = true;
                    client.print(formatOverlayMessage("&8\u00bb &5Urchin &cAPI key is invalid. Use &f/overlay seturchin <key> &cto update it."));
                }
                urchinCache.remove(uuid);
            } else {
                urchinCache.remove(uuid);
            }
        } catch (Exception e) {
            client.log("Runtime error getting urchin tag on " + uuid + ": " + e);
            urchinCache.remove(uuid);
        }
    });
}

String resolveUsernameForUuid(String uuid) {
    Map<String, Object> playerStats = overlayPlayers.get(uuid);
    if (playerStats != null) {
        Object ranked = playerStats.get("usernamewithrankcolor");
        if (ranked instanceof String) {
            String stripped = util.strip((String) ranked).trim();
            if (!stripped.isEmpty()) return stripped;
        }
    }
    try {
        for (NetworkPlayer np : world.getNetworkPlayers()) {
            if (np.getUUID().replace("-", "").equalsIgnoreCase(uuid)) return np.getName();
        }
    } catch (Exception ignored) {}
    return "";
}

String getUrchinTagColor(String tagType) {
    switch (tagType) {
        case "blatant_cheater": return util.colorSymbol + "c[BC]";
        case "confirmed_cheater": return util.colorSymbol + "c[C]";
        case "closet_cheater": return util.colorSymbol + "e[CC]";
        case "sniper": return util.colorSymbol + "6[S]";
        default: return util.colorSymbol + "7[?]";
    }
}

String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
}

String encodeColors(String s) {
    if (s == null) return "";
    return s.replace(util.colorSymbol + "", "&");
}

String decodeColors(String s) {
    if (s == null || s.isEmpty()) return "";
    return util.color(s);
}

void loadPermanentTagCache() {
    try {
        String tagJson = config.get("overlay_tag_cache");
        if (tagJson == null || tagJson.isEmpty()) return;
        Json json = new Json(tagJson);
        Map<String, Json> entries = json.map();
        if (entries == null) return;
        for (String uuid : entries.keySet()) {
            Json entry = entries.get(uuid).object();
            Map<String, String> data = new ConcurrentHashMap<>();
            data.put("c", entry.get("c", ""));
            data.put("s", entry.get("s", ""));
            permanentTagCache.put(uuid, data);
        }
        client.log("Loaded tag cache: " + permanentTagCache.size() + " entries");
    } catch (Exception e) {
        client.log("Error loading tag cache: " + e);
    }
}

void savePermanentTagCache() {
    try {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String uuid : permanentTagCache.keySet()) {
            Map<String, String> data = permanentTagCache.get(uuid);
            if (data == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(uuid)).append("\":{");
            sb.append("\"c\":\"").append(escapeJson(data.getOrDefault("c", ""))).append("\",");
            sb.append("\"s\":\"").append(escapeJson(data.getOrDefault("s", ""))).append("\"");
            sb.append("}");
        }
        sb.append("}");
        config.set("overlay_tag_cache", sb.toString());
    } catch (Exception e) {
        client.log("Error saving tag cache: " + e);
    }
}

void cachePlayerTags(String uuid, String cheatingDisplay, String snipingDisplay) {
    if (uuid == null || uuid.isEmpty()) return;
    Map<String, String> data = new ConcurrentHashMap<>();
    data.put("c", encodeColors(cheatingDisplay));
    data.put("s", encodeColors(snipingDisplay));
    permanentTagCache.put(uuid, data);
    savePermanentTagCache();
}

Map<String, Object> getCachedTags(String uuid) {
    Map<String, String> cached = permanentTagCache.get(uuid);
    if (cached == null) return null;
    Map<String, Object> result = new ConcurrentHashMap<>();
    result.put("cheating", decodeColors(cached.getOrDefault("c", "")));
    result.put("sniping", decodeColors(cached.getOrDefault("s", "")));
    return result;
}

Map<String, Object> getOrFetchTags(String uuid, String username) {
    Map<String, Object> cached = getCachedTags(uuid);
    if (cached != null) return cached;
    Map<String, Object> mergedTags = fetchPrismTags(uuid, username);
    cachePlayerTags(uuid,
        mergedTags.getOrDefault("cheating", "").toString(),
        mergedTags.getOrDefault("sniping", "").toString());
    return mergedTags;
}

boolean shouldRefreshStats(String uuid) {
    if (pendingStatsRequests.containsKey(uuid)) return false;
    Map<String, Object> cachedStats = statsCache.get(uuid);
    if (cachedStats == null) return true;
    long cacheTime = cachedStats.get("cachetime") instanceof Number ? ((Number) cachedStats.get("cachetime")).longValue() : 0L;
    return client.time() > cacheTime;
}

double expToStars(int exp) {
    int levelBase = (exp / 487000) * 100;
    int expMod = exp % 487000;
    int[][] levels = {{7000,4,5000},{3500,3,3500},{1500,2,2000},{500,1,1000},{0,0,500}};
    for (int[] lvl : levels) {
        if (expMod < lvl[0]) continue;
        return levelBase + lvl[1] + ((double)(expMod - lvl[0]) / lvl[2]);
    }
    return 0;
}

String getRank(Json playerData) {
    if (playerData == null) return null;
    if (!playerData.object("player").exists()) return null;
    Json player = playerData.object("player");
    String prefix = player.get("prefix", null);
    if (prefix != null) return prefix.replace("\u00A7", util.colorSymbol);
    String rank = player.get("rank", null);
    if (rank != null) {
        if (rank.equals("GAME_MASTER")) return "GM";
        if (rank.equals("YOUTUBER")) return "YOUTUBE";
        if (!rank.equals("NORMAL")) return rank;
    }
    String packageRank = player.get("newPackageRank", player.get("packageRank", null));
    if (packageRank == null || packageRank.equals("NONE")) return null;
    if (packageRank.startsWith("MVP")) return player.get("monthlyPackageRank", "").equals("SUPERSTAR") ? "MVP++" : packageRank.length() == 3 ? packageRank : "MVP+";
    if (packageRank.startsWith("VIP")) return packageRank.length() == 3 ? packageRank : "VIP+";
    return null;
}

String getFormattedRank(Json playerData) {
    String colorCode = util.colorSymbol + "7";
    if (playerData == null) return colorCode;
    String rank = getRank(playerData);
    if (rank == null) return colorCode;
    Json player = playerData.object("player");
    String plusColor = player.get("rankPlusColor", "RED");
    switch (plusColor) {
        case "BLACK": colorCode = util.colorSymbol + "0"; break;
        case "DARK_BLUE": colorCode = util.colorSymbol + "1"; break;
        case "DARK_GREEN": colorCode = util.colorSymbol + "2"; break;
        case "DARK_AQUA": colorCode = util.colorSymbol + "3"; break;
        case "DARK_RED": colorCode = util.colorSymbol + "4"; break;
        case "DARK_PURPLE": colorCode = util.colorSymbol + "5"; break;
        case "GOLD": colorCode = util.colorSymbol + "6"; break;
        case "GRAY": colorCode = util.colorSymbol + "7"; break;
        case "DARK_GRAY": colorCode = util.colorSymbol + "8"; break;
        case "BLUE": colorCode = util.colorSymbol + "9"; break;
        case "GREEN": colorCode = util.colorSymbol + "a"; break;
        case "AQUA": colorCode = util.colorSymbol + "b"; break;
        case "RED": colorCode = util.colorSymbol + "c"; break;
        case "LIGHT_PURPLE": colorCode = util.colorSymbol + "d"; break;
        case "YELLOW": colorCode = util.colorSymbol + "e"; break;
        case "WHITE": colorCode = util.colorSymbol + "f"; break;
    }
    switch (rank) {
        case "VIP": return util.color("&4" + "VIP");
        case "VIP+": return util.color("&a" + "VIP" + util.colorSymbol + "6+" + util.colorSymbol + "a]");
        case "MVP": return util.color("&b" + "MVP");
        case "MVP+": return util.color("&b" + "MVP" + colorCode + "+" + util.colorSymbol + "b]");
        case "MVP++": return util.color("&6" + "MVP" + colorCode + "++" + util.colorSymbol + "6]");
        case "GM": return util.color("&2" + "GM");
        case "YOUTUBE": return util.color("&c" + "YOUTUBE" + util.colorSymbol + "c]");
        case "ADMIN": return util.color("&c" + "ADMIN");
        default: return rank;
    }
}

String getWinstreakColor(String winstreak) {
    if (winstreak == null || winstreak.isEmpty()) return util.colorSymbol + "c--";
    int ws = Integer.parseInt(winstreak);
    if (ws == 0)    return util.colorSymbol + "70";
    if (ws >= 1000) return util.colorSymbol + '5' + ws;
    if (ws >= 500)  return util.colorSymbol + 'd' + ws;
    if (ws >= 300)  return util.colorSymbol + '4' + ws;
    if (ws >= 150)  return util.colorSymbol + 'c' + ws;
    if (ws >= 100)  return util.colorSymbol + '6' + ws;
    if (ws >= 75)   return util.colorSymbol + 'e' + ws;
    if (ws >= 50)   return util.colorSymbol + '2' + ws;
    if (ws >= 25)   return util.colorSymbol + 'a' + ws;
    return util.colorSymbol + '7' + ws;
}

String getSessionColor(long lastLogin, long lastLogout, String sessionFormatted) {
    long session = lastLogout - lastLogin;
    if (session > 21600000) return util.colorSymbol + '4' + sessionFormatted;
    if (session > 14400000) return util.colorSymbol + 'c' + sessionFormatted;
    if (session > 9000000)  return util.colorSymbol + '6' + sessionFormatted;
    if (session > 7200000)  return util.colorSymbol + 'e' + sessionFormatted;
    if (session > 1200000)  return util.colorSymbol + 'a' + sessionFormatted;
    if (session > 600000)   return util.colorSymbol + 'e' + sessionFormatted;
    if (session > 300000)   return util.colorSymbol + 'e' + sessionFormatted;
    if (session > 150000)   return util.colorSymbol + 'c' + sessionFormatted;
    return util.colorSymbol + '4' + sessionFormatted;
}

String getFkdrColor(String fkdr) {
    double v = Double.parseDouble(fkdr);
    if (v > 1000) return util.colorSymbol + '5' + fkdr;
    if (v > 100)  return util.colorSymbol + '4' + fkdr;
    if (v > 10)   return util.colorSymbol + 'c' + fkdr;
    if (v > 5)    return util.colorSymbol + '6' + fkdr;
    if (v > 2.4)  return util.colorSymbol + 'e' + fkdr;
    if (v > 1.4)  return util.colorSymbol + 'f' + fkdr;
    return util.colorSymbol + '7' + fkdr;
}

String formatDoubleStr(double val) { return val % 1 == 0 ? String.valueOf((int) val) : String.valueOf(val); }

String calculateRelativeTimestamp(long lastLogin, long lastLogout) {
    long timeSince = (lastLogout - lastLogin) / 1000L;
    long rem = timeSince;
    long years = rem / 31557600L; rem %= 31557600L;
    long months = rem / 2629800L; rem %= 2629800L;
    long days = rem / 86400L; rem %= 86400L;
    long hours = rem / 3600L; rem %= 3600L;
    long minutes = rem / 60L;
    long seconds = rem % 60L;
    StringBuilder msg = new StringBuilder();
    int added = 0;
    if (years > 0 && added < 2)   { msg.append(years).append("y");  added++; }
    if (months > 0 && added < 2)  { msg.append(months).append("mo"); added++; }
    if (days > 0 && added < 2)    { msg.append(days).append("d");   added++; }
    if (hours > 0 && added < 2)   { msg.append(hours).append("h");  added++; }
    if (minutes > 0 && added < 2) { msg.append(minutes).append("m"); added++; }
    if ((seconds > 0 && added < 2) || timeSince == 0) msg.append(seconds).append("s");
    return msg.toString();
}

String grad(int number, String[] colors) {
    String digits = String.valueOf(number);
    int len = digits.length();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < len; i++) {
        int ci = (colors.length <= 1 || len <= 1) ? 0 : (int)((float)i / (len - 1) * (colors.length - 1) + 0.5f);
        if (ci >= colors.length) ci = colors.length - 1;
        sb.append(colors[ci]);
        sb.append(digits.charAt(i));
    }
    return sb.toString();
}

String getNickBotPrestigeBracket(int number) {
    if (number < 100)  return "&7[" + grad(number, new String[]{"&7"}) + "&7\u272B]";
    if (number < 200)  return "&f[" + grad(number, new String[]{"&f"}) + "&f\u272B]";
    if (number < 300)  return "&6[" + grad(number, new String[]{"&6"}) + "&6\u272B]";
    if (number < 400)  return "&b[" + grad(number, new String[]{"&b"}) + "&b\u272B]";
    if (number < 500)  return "&2[" + grad(number, new String[]{"&2"}) + "&2\u272B]";
    if (number < 600)  return "&3[" + grad(number, new String[]{"&3"}) + "&3\u272B]";
    if (number < 700)  return "&4[" + grad(number, new String[]{"&4"}) + "&4\u272B]";
    if (number < 800)  return "&d[" + grad(number, new String[]{"&d"}) + "&d\u272B]";
    if (number < 900)  return "&9[" + grad(number, new String[]{"&9"}) + "&9\u272B]";
    if (number < 1000) return "&5[" + grad(number, new String[]{"&5"}) + "&5\u272B]";
    if (number < 1100) return "&c[" + grad(number, new String[]{"&c","&6","&e","&a","&b","&d","&5"}) + "&5\u272B]";
    if (number < 1200) return "&7[" + grad(number, new String[]{"&f","&7"}) + "&7\u272A]";
    if (number < 1300) return "&7[" + grad(number, new String[]{"&e","&6"}) + "&7\u272A]";
    if (number < 1400) return "&7[" + grad(number, new String[]{"&b","&3"}) + "&7\u272A]";
    if (number < 1500) return "&7[" + grad(number, new String[]{"&a","&2"}) + "&7\u272A]";
    if (number < 1600) return "&7[" + grad(number, new String[]{"&3","&9"}) + "&7\u272A]";
    if (number < 1700) return "&7[" + grad(number, new String[]{"&c","&4"}) + "&7\u272A]";
    if (number < 1800) return "&7[" + grad(number, new String[]{"&d","&5"}) + "&7\u272A]";
    if (number < 1900) return "&7[" + grad(number, new String[]{"&9","&1"}) + "&7\u272A]";
    if (number < 2000) return "&7[" + grad(number, new String[]{"&5","&8"}) + "&7\u272A]";
    if (number < 2100) return "&8[" + grad(number, new String[]{"&7","&f","&7"}) + "&8\u272A]";
    if (number < 2200) return "&f[" + grad(number, new String[]{"&f","&e","&6"}) + "&6\u2740]";
    if (number < 2300) return "&6[" + grad(number, new String[]{"&6","&f","&b","&3"}) + "&3\u2740]";
    if (number < 2400) return "&5[" + grad(number, new String[]{"&5","&d","&6","&e"}) + "&e\u2740]";
    if (number < 2500) return "&b[" + grad(number, new String[]{"&b","&f","&7"}) + "&8\u2740]";
    if (number < 2600) return "&f[" + grad(number, new String[]{"&f","&a","&2"}) + "&2\u2740]";
    if (number < 2700) return "&4[" + grad(number, new String[]{"&4","&c","&d","&5"}) + "&5\u2740]";
    if (number < 2800) return "&e[" + grad(number, new String[]{"&e","&f","&8"}) + "&8\u2740]";
    if (number < 2900) return "&a[" + grad(number, new String[]{"&a","&2","&6","&e"}) + "&e\u2740]";
    if (number < 3000) return "&b[" + grad(number, new String[]{"&b","&3","&9","&1"}) + "&1\u2740]";
    if (number < 3100) return "&e[" + grad(number, new String[]{"&e","&6","&c","&4"}) + "&4\u2740]";
    if (number < 3200) return "&5[" + grad(number, new String[]{"&5","&d","&f","&d","&5"}) + "&5\u2740]";
    if (number < 3300) return "&1[" + grad(number, new String[]{"&1","&9","&3","&b","&f"}) + "&f\u2740]";
    if (number < 3400) return "&f[" + grad(number, new String[]{"&f","&e","&6","&c","&4"}) + "&4\u2740]";
    if (number < 3500) return "&f[" + grad(number, new String[]{"&f","&b","&3","&9","&1"}) + "&1\u2740]";
    if (number < 3600) return "&6[" + grad(number, new String[]{"&6","&e","&f","&e","&6"}) + "&6\u2740]";
    if (number < 3700) return "&3[" + grad(number, new String[]{"&3","&b","&f","&e","&6"}) + "&6\u2740]";
    if (number < 3800) return "&8[" + grad(number, new String[]{"&8","&7","&f","&7","&8"}) + "&8\u2740]";
    if (number < 3900) return "&2[" + grad(number, new String[]{"&2","&a","&e","&6"}) + "&6\u2740]";
    if (number < 4000) return "&4[" + grad(number, new String[]{"&4","&c","&6","&e","&f"}) + "&f\u2740]";
    if (number < 4100) return "&5[" + grad(number, new String[]{"&5","&d","&1","&9","&b"}) + "&b\u2740]";
    if (number < 4200) return "&4[" + grad(number, new String[]{"&4","&c","&6","&e","&f","&e","&6"}) + "&6\u2740]";
    if (number < 4300) return "&d[" + grad(number, new String[]{"&d","&5","&1","&9","&b","&f"}) + "&f\u2740]";
    if (number < 4400) return "&3[" + grad(number, new String[]{"&3","&b","&f","&b","&3"}) + "&3\u2740]";
    if (number < 4500) return "&8[" + grad(number, new String[]{"&8","&7","&f","&e","&c","&4"}) + "&4\u2740]";
    if (number < 4600) return "&c[" + grad(number, new String[]{"&c","&6","&e","&a","&b","&9","&5","&d"}) + "&d\u2740]";
    return "&f[" + grad(number, new String[]{"&f","&e","&6","&c","&4","&5","&d","&b","&f"}) + "&f\u2740]";
}

String removePrestigeSymbols(String text) {
    if (text == null) return "";
    return text.replace("\u272B","").replace("\u272A","").replace("\u2740","").replace("\u269D","").replace("\u2725","");
}

String removePrestigeBrackets(String text) {
    if (text == null) return "";
    return text.replace("[","").replace("]","");
}

String getPrestigeColor(int number) {
    return util.color(removePrestigeBrackets(removePrestigeSymbols(getNickBotPrestigeBracket(number))));
}

Image defaultHead = new Image("https://mc-heads.net/avatar/c06f89064c8a49119c29ea1dbd1aab82/8", true);

float fontHeight = (float) render.getFontHeight();
float startX = 12;
float startY = 12;
float offsetY = 3;
float lineHeight = fontHeight + offsetY;
float textScale = 1;
float endY;
float endX;

final String chromeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
final String chatPrefix = "&7[&dR&7]&r ";

boolean dowho = true;
boolean didwho = false;
boolean listeningForChat = false;
boolean urchinKeyInvalid = false;
boolean hypixelKeyInvalid = false;
boolean ascending = true;
boolean replayMode = false;
String sortBy;
int lastSortMode = -1;
int status = 0;
int overlayTicks = 5;
long overlayLastAnimAt = 0L;
long lastEditPositionWarningMs = 0L;
float overlayAlpha = 0.0f;
float brOffsetX = 12.0f;
float brOffsetY = 12.0f;
float blOffsetX = 12.0f;
float blOffsetY = 12.0f;
float trOffsetX = 12.0f;
float trOffsetY = 12.0f;
float tlOffsetX = 12.0f;
float tlOffsetY = 12.0f;
float overlayScale = 1.0f;
boolean overlayDragging = false;
boolean overlayScaling = false;
boolean overlayPrevLeft = false;
float overlayDragX = 0.0f;
float overlayDragY = 0.0f;
float overlayScaleStart = 1.0f;
float overlayScaleStartX = 0.0f;
float overlayScaleStartY = 0.0f;
float headsSize = 10f;
long lastHeadEnsureAt = 0L;

boolean _rtStartWithF = false;
boolean _rtColourByTeam = false;
boolean _rtAddStarBrackets = false;
Map<String, Float> textWidthCache = new HashMap<>();
Map<String, String> textRenderCache = new HashMap<>();
Map<String, Integer> textColorCache = new HashMap<>();

Map<String, Map<String, Object>> overlayPlayers = new ConcurrentHashMap<>();
Map<String, String> ignoredPlayers = new HashMap<>();
Map<String, Boolean> deadPlayers = new ConcurrentHashMap<>();
ArrayList<String> currentPlayers = new ArrayList<>();
String currentLobby = "";
String lastLobby = "";

List<Map<String, Object>> columns = new ArrayList<>();
List<String> tags = new ArrayList<>();
Map<String, String> teams = new HashMap<>();
Map<String, Image> headImageCache = new ConcurrentHashMap<>();
Map<String, Double> teamSortValues = new ConcurrentHashMap<>();
boolean overlayNeedsSort = false;
boolean overlayNeedsLayout = false;

final static String playerKey = "player",
                    tagsKey = "tags",
                    headsKey = "playerhead";

void registerDefaultButtons() {
    modules.registerDescription("BW Overlay.");
    modules.registerSlider("Show bind", "", 0, showBindOptions);
    modules.registerButton("Hold", true);
    modules.registerSlider("Sort by", "", 0, new String[] {"Team", "Star", "FKDR", "Winstreak"});
    modules.registerButton("Print urchin reason", false);
    modules.registerButton("Collapse final deaths", true);
    modules.registerDescription("=> Visual");
    modules.registerButton("Bloom", true);
    modules.registerButton("Animate", false);
    modules.registerButton("Start with {f}", false);
    modules.registerButton("Add brackets to star", false);
    modules.registerSlider("Theme", "", 0, themeOptions);
    modules.registerSlider("Team colour", "", 0, new String[] {"Dot", "Name"});
    modules.registerSlider("Position", "", 0, new String[] {"Bottom Right", "Bottom Left", "Top Right", "Top Left"});
    modules.registerButton("Edit", false);
}

int getShowBindKeyCode() {
    int idx = 0;
    try { idx = (int) modules.getSlider(scriptName, "Show bind"); } catch (Exception ignored) {}
    switch (idx) {
        case 0:  return 15;
        case 1:  return 54;
        case 2:  return 41;
        case 3:  return 51;
        case 4:  return 52;
        case 5:  return 29;
        case 6:  return 56;
        case 7:  return 44;
        case 8:  return 45;
        case 9:  return 46;
        case 10: return 47;
        case 11: return 48;
        case 12: return 49;
        case 13: return 50;
        case 14: return 16;
        case 15: return 18;
        case 16: return 19;
        case 17: return 20;
        case 18: return 21;
        case 19: return 22;
        case 20: return 23;
        case 21: return 24;
        case 22: return 25;
        case 23: return 30;
        case 24: return 31;
        case 25: return 32;
        case 26: return 33;
        case 27: return 34;
        case 28: return 35;
        case 29: return 36;
        case 30: return 37;
        case 31: return 38;
        case 32: return 2;
        case 33: return 3;
        case 34: return 4;
        case 35: return 5;
        case 36: return 6;
        case 37: return 7;
        case 38: return 8;
        case 39: return 9;
        case 40: return 10;
        case 41: return 11;
        case 42: return 12;
        case 43: return 13;
        case 44: return 26;
        case 45: return 27;
        case 46: return 43;
        case 47: return 39;
        case 48: return 40;
        case 49: return 53;
        case 50: return 59;
        case 51: return 60;
        case 52: return 61;
        case 53: return 62;
        case 54: return 63;
        case 55: return 64;
        case 56: return 65;
        case 57: return 66;
        case 58: return 67;
        case 59: return 68;
        case 60: return 87;
        case 61: return 88;
        case 62: return 1;
        case 63: return 14;
        case 64: return 28;
        case 65: return 57;
        case 66: return 42;
        case 67: return 58;
        case 68: return 157;
        case 69: return 184;
        case 70: return 200;
        case 71: return 208;
        case 72: return 203;
        case 73: return 205;
        case 74: return 210;
        case 75: return 211;
        case 76: return 199;
        case 77: return 207;
        case 78: return 201;
        case 79: return 209;
        default: return 15;
    }
}

boolean isHoldMode() {
    try { return modules.getButton(scriptName, "Hold"); } catch (Exception ignored) {}
    return true;
}

boolean isShowBindHeld() {
    try { return keybinds.isKeyDown(getShowBindKeyCode()); } catch (Exception ignored) {}
    return false;
}

void updateBindToggle() {
    if (isHoldMode()) {
        showBindWasDown = isShowBindHeld();
        return;
    }
    boolean currentlyDown = isShowBindHeld();
    if (currentlyDown && !showBindWasDown) {
        overlayToggleVisible = !overlayToggleVisible;
    }
    showBindWasDown = currentlyDown;
}

boolean isOverlayVisible() {
    if (isHoldMode()) return isShowBindHeld();
    return overlayToggleVisible;
}

int getSortMode() {
    try { return (int) modules.getSlider(scriptName, "Sort by"); } catch (Exception ignored) {}
    return 0;
}

String getSortKeyForMode(int mode) {
    if (mode == 1) return starValue;
    if (mode == 2) return fkdrValue;
    if (mode == 3) return winstreakValue;
    return indexValue;
}

boolean shouldSortByTeam() { return getSortMode() == 0; }

String getSortedColumnKey() {
    int mode = getSortMode();
    if (mode == 0) return "";
    if (mode == 1) return playerKey;
    if (mode == 2) return fkdrKey;
    if (mode == 3) return winstreakKey;
    return playerKey;
}

void defaultSettings() {
    int sortMode = getSortMode();
    String nextSortBy = getSortKeyForMode(sortMode);
    boolean shouldResort = overlayPlayers.size() > 1 && (!ascending || sortBy == null || !sortBy.equals(nextSortBy) || lastSortMode != sortMode);
    ascending = true;
    sortBy = nextSortBy;
    lastSortMode = sortMode;
    if (shouldResort) sortOverlay();
    applyOverlayScale();
}

void applyOverlayScale() {
    float prev = textScale;
    textScale = (9.0f / (float) render.getFontHeight()) * overlayScale;
    fontHeight = (float) render.getFontHeight() * textScale;
    headsSize = Math.max(8f * textScale, fontHeight - 1f * textScale);
    offsetY = 3f * textScale;
    lineHeight = fontHeight + offsetY;
    if (Math.abs(prev - textScale) > 0.001f) overlayNeedsLayout = true;
}

void loadOverlayHud() {
    brOffsetX = 12.0f; brOffsetY = 12.0f;
    blOffsetX = 12.0f; blOffsetY = 12.0f;
    trOffsetX = 12.0f; trOffsetY = 12.0f;
    tlOffsetX = 12.0f; tlOffsetY = 12.0f;
    overlayScale = 1.0f;
    try {
        String brx = config.get("overlayBrX"); String bry = config.get("overlayBrY");
        String blx = config.get("overlayBlX"); String bly = config.get("overlayBlY");
        String trx = config.get("overlayTrX"); String tryV = config.get("overlayTrY");
        String tlx = config.get("overlayTlX"); String tly = config.get("overlayTlY");
        String ss  = config.get("overlayScale");
        if (brx != null && !brx.isEmpty()) brOffsetX = Float.parseFloat(brx);
        if (bry != null && !bry.isEmpty()) brOffsetY = Float.parseFloat(bry);
        if (blx != null && !blx.isEmpty()) blOffsetX = Float.parseFloat(blx);
        if (bly != null && !bly.isEmpty()) blOffsetY = Float.parseFloat(bly);
        if (trx != null && !trx.isEmpty()) trOffsetX = Float.parseFloat(trx);
        if (tryV != null && !tryV.isEmpty()) trOffsetY = Float.parseFloat(tryV);
        if (tlx != null && !tlx.isEmpty()) tlOffsetX = Float.parseFloat(tlx);
        if (tly != null && !tly.isEmpty()) tlOffsetY = Float.parseFloat(tly);
        if (ss  != null && !ss.isEmpty())  overlayScale = clamp(Float.parseFloat(ss), 0.55f, 1.8f);
    } catch (Exception ignored) {}
    applyOverlayScale();
}

void saveOverlayHud() {
    config.set("overlayBrX", String.valueOf(brOffsetX));
    config.set("overlayBrY", String.valueOf(brOffsetY));
    config.set("overlayBlX", String.valueOf(blOffsetX));
    config.set("overlayBlY", String.valueOf(blOffsetY));
    config.set("overlayTrX", String.valueOf(trOffsetX));
    config.set("overlayTrY", String.valueOf(trOffsetY));
    config.set("overlayTlX", String.valueOf(tlOffsetX));
    config.set("overlayTlY", String.valueOf(tlOffsetY));
    config.set("overlayScale", String.valueOf(overlayScale));
}

boolean isInOverlay(String uuid) { return overlayPlayers.containsKey(uuid); }
String getChatPrefix() { return chatPrefix; }

void clearOverlayTagDisplays() {
    for (String uuid : overlayPlayers.keySet()) {
        Map<String, Object> playerData = overlayPlayers.get(uuid);
        if (playerData == null) continue;
        playerData.put(tagsKey, "");
        playerData.put("cheating", "");
        playerData.put("sniping", "");
        playerData.put("urchin", "");
    }
    overlayNeedsLayout = true;
}

boolean canRefreshStatsUuid(String uuid) {
    return uuid != null && uuid.length() > 14 && (uuid.charAt(12) == '4' || uuid.charAt(14) == '4');
}

void refreshOverlayApiData() {
    permanentTagCache.clear();
    savePermanentTagCache();
    clearOverlayTagDisplays();
    statsFailureCounts.clear();
    synchronized (statsRateLock) {
        statsRequestTimes.clear();
        statsGlobalCooldownUntil = 0L;
    }
    ArrayList<String> uuids = new ArrayList<>(overlayPlayers.keySet());
    for (String uuid : uuids) {
        statsCache.remove(uuid);
        urchinCache.remove(uuid);
        pendingStatsRequests.remove(uuid);
        if (canRefreshStatsUuid(uuid)) {
            handlePlayerStats(uuid, getLobbyId());
            handleUrchinTag(uuid, getLobbyId());
        }
    }
}

void setOverlayApiKey(String apiName, String configKey, String value) {
    String keyValue = value == null ? "" : value.trim();
    boolean removing = keyValue.isEmpty();
    if (apiName.equalsIgnoreCase("Hypixel")) {
        hypixelApiKey = keyValue;
        hypixelKeyInvalid = false;
    } else if (apiName.equalsIgnoreCase("Urchin")) {
        urchinApiKey = keyValue;
        urchinKeyInvalid = false;
    }
    saveApiKey(configKey, keyValue);
    refreshOverlayApiData();
    String coloredName = apiName.equalsIgnoreCase("Hypixel") ? "&6" + apiName : "&5" + apiName;
    client.print(formatOverlayMessage(removing
        ? "&8\u00bb " + coloredName + " &ckey removed."
        : "&8\u00bb " + coloredName + " &akey set."));
}

boolean handleOverlayApiCommand(String message) {
    if (message == null) return false;
    String trimmed = message.trim();
    if (trimmed.isEmpty()) return false;
    String[] parts = trimmed.split(" ", 3);
    String root = parts[0].toLowerCase();
    if (!root.equals("/overlay") && !root.equals("/ov")) return false;
    String sub = parts.length > 1 ? parts[1].toLowerCase() : "";
    String arg = parts.length > 2 ? parts[2].trim() : "";
    if (sub.isEmpty()) {
        client.print(formatOverlayMessage("&8\u00bb &fCommands"));
        client.print(util.color("&8&m--------------------------------"));
        client.print(util.color("&8| &f/overlay&7, &f/ov &8- &7Show this panel"));
        client.print(util.color("&8| &f/overlay sethypixel &8<key> &8- &7Set Hypixel API key"));
        client.print(util.color("&8| &f/overlay seturchin &8<key> &8- &7Set Urchin API key"));
        client.print(util.color("&8| &f/overlay status &8- &7View API key status"));
        client.print(util.color("&8&m--------------------------------"));
        return true;
    }
    if (sub.equals("sethypixel")) { setOverlayApiKey("Hypixel", "overlay_hypixel_key", arg); return true; }
    if (sub.equals("seturchin"))  { setOverlayApiKey("Urchin",  "overlay_urchin_key",  arg); return true; }
    if (sub.equals("status"))     { printApiStatus(); return true; }
    return true;
}

boolean onPacketSent(CPacket packet) {
    if (packet instanceof C01) {
        C01 chatPacket = (C01) packet;
        if (handleOverlayApiCommand(chatPacket.message)) return false;
    }
    return true;
}

String getLobbyId() { return currentLobby; }
boolean hasChangedLobby(String inputLobby) { return !inputLobby.equals(getLobbyId()); }

void removePlayerFromOverlay(String uuid) {
    if (uuid == null || uuid.isEmpty()) return;
    overlayPlayers.remove(uuid);
    synchronized (currentPlayers) { currentPlayers.remove(uuid); }
    overlayNeedsLayout = true;
    overlayNeedsSort = true;
}

void removeOverlayByIgn(String username) {
    if (username == null || username.isEmpty()) return;
    String uuid = findOverlayUuidByName(username);
    if (uuid != null && !uuid.isEmpty()) removePlayerFromOverlay(uuid);
}

boolean shouldCollapseDeadPlayers() {
    try { return modules.getButton(scriptName, "Collapse final deaths"); } catch (Exception ignored) {}
    return true;
}

void updateDeadStateFromChat(String msg) {
    if (msg == null || msg.trim().isEmpty() || msg.startsWith("Party >")) return;
    if (msg.endsWith("FINAL KILL!") || msg.endsWith("FINAL KILL")) {
        String deadPlayer = extractLeadingIgn(msg);
        if (isValidIgn(deadPlayer)) {
            deadPlayers.put(deadPlayer.toLowerCase(), true);
            if (shouldCollapseDeadPlayers()) removeOverlayByIgn(deadPlayer);
        }
        return;
    }
    if (msg.endsWith(" reconnected.") || msg.endsWith(" reconnected")) {
        String reconnected = extractLeadingIgn(msg);
        if (isValidIgn(reconnected)) deadPlayers.remove(reconnected.toLowerCase());
    }
}

String extractLeadingIgn(String message) {
    String[] words = message.split(" ");
    for (int i = 0; i < words.length && i < 3; i++) {
        String candidate = sanitizeIgnToken(words[i]);
        if (candidate.isEmpty()) continue;
        String upper = candidate.toUpperCase();
        if (upper.equals("PARTY") || upper.equals("FINAL") || upper.equals("KILL")) continue;
        if (upper.equals("R") || upper.equals("B") || upper.equals("G") || upper.equals("Y")
            || upper.equals("A") || upper.equals("W") || upper.equals("P")) continue;
        if (upper.equals("VIP") || upper.equals("MVP") || upper.equals("GM") || upper.equals("YOUTUBE")) continue;
        if (isValidIgn(candidate)) return candidate;
    }
    return "";
}

double getSortableNumber(Map<String, Object> stats, String key) {
    if (stats == null || key == null || key.isEmpty()) return Double.NEGATIVE_INFINITY;
    Object raw = stats.get(key);
    if (raw == null) return Double.NEGATIVE_INFINITY;
    if (raw instanceof Number) return ((Number) raw).doubleValue();
    try {
        String value = raw.toString().replaceAll(util.colorSymbol + ".", "").trim();
        if (value.isEmpty() || !containsDigit(value)) return Double.NEGATIVE_INFINITY;
        return Double.parseDouble(value);
    } catch (Exception ignored) { return Double.NEGATIVE_INFINITY; }
}

void refreshTeamSortValues() {
    teamSortValues.clear();
    if (status != 3) return;
    for (String uuid : overlayPlayers.keySet()) {
        String displayName = teams.getOrDefault(uuid, "");
        if (displayName == null || displayName.isEmpty()) continue;
        String teamToken = getTeamToken(displayName).toLowerCase();
        if (teamToken.isEmpty()) continue;
        double value = getSortableNumber(overlayPlayers.get(uuid), sortBy);
        double currentBest = teamSortValues.getOrDefault(teamToken, Double.NEGATIVE_INFINITY);
        if (value > currentBest) teamSortValues.put(teamToken, value);
    }
}

String getHeadUrl(String uuid) { return "https://mc-heads.net/avatar/" + uuid.replace("-", "") + "/8"; }

Image getHeadImage(String uuid) {
    if (uuid == null || uuid.isEmpty()) return defaultHead;
    String normalized = uuid.replace("-", "");
    if (normalized.length() != 32) return defaultHead;
    Image cached = headImageCache.get(normalized);
    if (cached != null) return cached;
    Image image = new Image(getHeadUrl(normalized), true);
    headImageCache.put(normalized, image);
    return image;
}

void ensurePlayerHead(Map<String, Object> playerStats, String uuid) {
    if (playerStats == null || uuid == null || uuid.isEmpty()) return;
    if (!(playerStats.get(headsKey) instanceof Image)) playerStats.put(headsKey, getHeadImage(uuid));
}

String getCurrentOverlayName(String uuid) {
    Map<String, Object> playerData = overlayPlayers.get(uuid);
    if (playerData == null) return "";
    Object currentName = playerData.get(playerKey);
    return currentName != null ? currentName.toString() : "";
}

boolean shouldUseTeamDisplay(String uuid) { return status == 3 && teams.containsKey(uuid); }

String resolveOverlayName(String uuid, Map<String, Object> playerStats, String fallbackName) {
    if (shouldUseTeamDisplay(uuid)) {
        String teamDisplay = teams.getOrDefault(uuid, fallbackName);
        return teamDisplay.contains(" ") ? teamDisplay.split(" ", 2)[1] : teamDisplay;
    }
    String resolvedName = playerStats != null ? (String) playerStats.getOrDefault("usernamewithrank", "") : "";
    if (resolvedName.isEmpty()) {
        Map<String, Object> cachedStats = statsCache.get(uuid);
        if (cachedStats != null) resolvedName = (String) cachedStats.getOrDefault("usernamewithrank", "");
    }
    return resolvedName.isEmpty() ? fallbackName : resolvedName;
}

void updateTeamEntry(String uuid, String displayName) {
    if (status != 3 || displayName == null || !displayName.contains(" ")) return;
    String previousDisplay = teams.get(uuid);
    boolean teamChanged = previousDisplay == null || !previousDisplay.equals(displayName);
    teams.put(uuid, displayName);
    if (!isInOverlay(uuid)) return;
    String currentName = getCurrentOverlayName(uuid);
    String desiredName = resolveOverlayName(uuid, overlayPlayers.get(uuid), displayName);
    if (!desiredName.isEmpty() && !desiredName.equals(currentName)) {
        Map<String, Object> update = new HashMap<>();
        update.put(playerKey, desiredName);
        addToOverlay(uuid, update);
    } else if (teamChanged) {
        overlayNeedsSort = true;
        overlayNeedsLayout = true;
    }
}

void trimStatsRequests(long now) {
    synchronized (statsRateLock) {
        while (!statsRequestTimes.isEmpty() && now - statsRequestTimes.get(0) >= statsRateWindowMs)
            statsRequestTimes.remove(0);
    }
}

boolean awaitStatsRequestSlot(String lobby) {
    while (!hasChangedLobby(lobby)) {
        long now = client.time();
        long sleepMs = 250L;
        synchronized (statsRateLock) {
            trimStatsRequests(now);
            if (now < statsGlobalCooldownUntil) {
                sleepMs = Math.max(200L, statsGlobalCooldownUntil - now);
            } else if (statsRequestTimes.size() < statsRateLimit - statsRateBuffer) {
                statsRequestTimes.add(now);
                return true;
            }
        }
        client.sleep(sleepMs);
    }
    return false;
}

void addPlaceholderStats(String player, String username, boolean doName) {
    Map<String, Object> placeholderStats = new ConcurrentHashMap<>();
    for (Map<String, Object> column : columns) {
        String key = column.get("key").toString();
        if (key.equals(playerKey)) {
            if (doName) placeholderStats.put(key, util.colorSymbol + "7" + username);
            continue;
        } else if (key.equals(headsKey)) {
            placeholderStats.put(headsKey, getHeadImage(player));
            continue;
        }
        placeholderStats.put(key, util.colorSymbol + "8...");
    }
    placeholderStats.put(headsKey, getHeadImage(player));
    placeholderStats.put("cheating", "");
    placeholderStats.put("sniping", "");
    placeholderStats.put("urchin", "");
    placeholderStats.put(requestStateKey, "loading");
    if (doName) overlayPlayers.put(player, placeholderStats);
    else addToOverlay(player, placeholderStats);
}

void onEnable() {
    loadOverlayHud();
    overlayLastAnimAt = 0L;
    lastEditPositionWarningMs = 0L;
    overlayAlpha = 0.0f;
    overlayDragging = false;
    overlayScaling = false;
    overlayPrevLeft = false;
    overlayTicks = 5;
    overlayToggleVisible = false;
    showBindWasDown = false;
    defaultSettings();
    updateStatus();

    if (status == 3 && !overlayPlayers.isEmpty()) {
        HashSet<String> activeUuids = new HashSet<>();
        try {
            for (NetworkPlayer pla : world.getNetworkPlayers()) {
                String uuid = pla.getUUID();
                if (uuid != null && !uuid.isEmpty()) activeUuids.add(uuid.replace("-", ""));
            }
        } catch (Exception ignored) {}
        ArrayList<String> toRemove = new ArrayList<>();
        for (String uuid : overlayPlayers.keySet()) {
            if (!activeUuids.contains(uuid)) toRemove.add(uuid);
        }
        if (!toRemove.isEmpty()) {
            synchronized (currentPlayers) {
                for (String uuid : toRemove) {
                    overlayPlayers.remove(uuid);
                    currentPlayers.remove(uuid);
                }
            }
            overlayNeedsLayout = true;
            overlayNeedsSort = true;
        }
    }

    overlayNeedsSort = true;
    overlayNeedsLayout = true;
    doColumns();
}

void onDisable() {
    saveOverlayHud();
    saveApiKey("overlay_hypixel_key", hypixelApiKey);
    saveApiKey("overlay_urchin_key", urchinApiKey);
    lastEditPositionWarningMs = 0L;
    overlayDragging = false;
    overlayScaling = false;
}

void onPreUpdate() {
    printEditPositionWarningIfNeeded();
    updateStatus();
    updateBindToggle();

    if (overlayTicks++ % 5 != 0) return;

    defaultSettings();

    if (client.time() - lastHeadEnsureAt > 1500L) {
        for (String overlayUuid : overlayPlayers.keySet()) {
            Map<String, Object> overlayData = overlayPlayers.get(overlayUuid);
            if (overlayData == null) continue;
            if (overlayData.get(headsKey) instanceof Image) continue;
            overlayData.put(headsKey, getHeadImage(overlayUuid));
            overlayNeedsLayout = true;
        }
        lastHeadEnsureAt = client.time();
    }

    if (status > 1) {
        HashSet<String> currentEntityUUIDs = new HashSet<>();

        for (NetworkPlayer pla : world.getNetworkPlayers()) {
            final String uuid = pla.getUUID().replace("-", "");
            final String displayName = pla.getDisplayName();
            final String username = pla.getName();

            if (ignoredPlayers.containsKey(username.toLowerCase())) {
                if (isInOverlay(uuid)) removePlayerFromOverlay(uuid);
                continue;
            }

            currentEntityUUIDs.add(uuid);

            String botReason = getBotReason(pla);
            if (!botReason.isEmpty()) continue;

            if (isInOverlay(uuid)) {
                updateTeamEntry(uuid, displayName);
                if (shouldRefreshStats(uuid)) handlePlayerStats(uuid, getLobbyId());
                continue;
            }

            Map<String, Object> placeholderStats = new ConcurrentHashMap<>();
            placeholderStats.put(playerKey, displayName);

            if (uuid.charAt(12) != '4') {
                String nickDisplay = getNickedDisplayName(displayName, username);
                placeholderStats.put("nicked", true);
                placeholderStats.put(requestStateKey, "nicked");
                placeholderStats.put(playerKey, util.colorSymbol + "e" + nickDisplay);
                placeholderStats.put(headsKey, defaultHead);
                overlayPlayers.put(uuid, placeholderStats);
                overlayNeedsSort = true;
                overlayNeedsLayout = true;
                continue;
            }

            overlayPlayers.put(uuid, placeholderStats);
            updateTeamEntry(uuid, displayName);
            addPlaceholderStats(uuid, displayName, false);
            onPlayerAdd(uuid);
        }

        if (status == 2) {
            Iterator<String> overlayIterator = overlayPlayers.keySet().iterator();
            while (overlayIterator.hasNext()) {
                String overlayUUID = overlayIterator.next();
                if (currentEntityUUIDs.contains(overlayUUID)) continue;
                if (overlayPlayers.get(overlayUUID).containsKey("manual")) continue;
                if (overlayPlayers.get(overlayUUID).containsKey("chatadded")) continue;
                overlayIterator.remove();
                overlayNeedsLayout = true;
                overlayNeedsSort = true;
            }
        }
    }

    synchronized(currentPlayers) {
        if (status != 3) {
            Iterator<String> iterator = currentPlayers.iterator();
            while (iterator.hasNext()) {
                String uuid = iterator.next();
                if (isInOverlay(uuid)) continue;
                iterator.remove();
                overlayNeedsLayout = true;
                overlayNeedsSort = true;
            }
        }
        for (String uuid : overlayPlayers.keySet()) {
            if (currentPlayers.contains(uuid)) continue;
            currentPlayers.add(uuid.charAt(12) == '4' ? (ascending ? currentPlayers.size() : 0) : (ascending ? 0 : currentPlayers.size()), uuid);
            overlayNeedsLayout = true;
            overlayNeedsSort = true;
        }
    }

    if (overlayNeedsSort) { sortOverlay(); overlayNeedsSort = false; }
    if (overlayNeedsLayout) { doColumns(); overlayNeedsLayout = false; }
}

String fontText(String text) {
    String value = text == null ? "" : text;
    return _rtStartWithF ? "{f}" + value : plainRenderText(value);
}

float textWidth(String text) {
    String rendered = fontText(text);
    String key = (_rtStartWithF ? "f:" : "m:") + rendered;
    Float cached = textWidthCache.get(key);
    if (cached != null) return cached;
    float width = (float) render.getFontWidth(rendered);
    if (textWidthCache.size() > 512) textWidthCache.clear();
    textWidthCache.put(key, width);
    return width;
}

boolean isFormattingCode(char c) {
    char lower = Character.toLowerCase(c);
    return lower == 'r' || lower == 'k' || lower == 'l' || lower == 'm' || lower == 'n' || lower == 'o'
        || (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f');
}

String plainRenderText(String text) {
    if (text == null || text.isEmpty()) return "";
    String cached = textRenderCache.get(text);
    if (cached != null) return cached;

    char colorChar = util.colorSymbol != null && !util.colorSymbol.isEmpty() ? util.colorSymbol.charAt(0) : '\0';
    StringBuilder stripped = null;
    for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if ((c == colorChar || c == '&') && i + 1 < text.length() && isFormattingCode(text.charAt(i + 1))) {
            if (stripped == null) {
                stripped = new StringBuilder(text.length());
                stripped.append(text, 0, i);
            }
            i++;
            continue;
        }
        if (stripped != null) stripped.append(c);
    }

    String result = stripped == null ? text : stripped.toString();
    if (textRenderCache.size() > 512) textRenderCache.clear();
    textRenderCache.put(text, result);
    return result;
}

int resolveNormalTextColor(String text, int fallback) {
    if (_rtStartWithF || (fallback & 0x00FFFFFF) != 0x00FFFFFF) return fallback;
    Integer cached = textColorCache.get(text);
    if (cached != null) return (fallback & 0xFF000000) | (cached & 0x00FFFFFF);
    char code = getFirstColorCode(text);
    if (code == ' ') return fallback;
    int color = getCodeColor(code);
    if (textColorCache.size() > 512) textColorCache.clear();
    textColorCache.put(text, color);
    return (fallback & 0xFF000000) | (color & 0x00FFFFFF);
}

void drawOverlayText(String text, float x, float y, float scale, int color) {
    render.text(fontText(text), x, y, scale, resolveNormalTextColor(text, color), _rtStartWithF);
}
float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
int clampInt(int value, int min, int max) { return value < min ? min : value > max ? max : value; }
int withAlpha(int color, int alpha) { return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF); }

int multiplyAlpha(int color, float alpha) {
    int currentAlpha = (color >> 24) & 0xFF;
    int nextAlpha = (int) (currentAlpha * clamp(alpha, 0.0f, 1.0f));
    return withAlpha(color, nextAlpha);
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
    return 0xFFB8BEC4;
}

String resolveTheme() {
    int idx = 0;
    try { idx = (int) modules.getSlider(scriptName, "Theme"); } catch (Exception ignored) {}
    if (idx == 0) {
        try {
            int si = (int) modules.getSlider("Settings", "Default theme");
            if (si >= 0 && si < settingsThemeMap.length) return settingsThemeMap[si];
        } catch (Exception ignored) {}
        return "white";
    }
    if (idx >= 1 && idx < themeOptions.length) return themeOptions[idx];
    return "white";
}

int getOverlayThemeColor() { return getThemeColor(resolveTheme()); }

char getNearestChatColorCode(int color) {
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    char[] codes = new char[] {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
    int[] colors = new int[] {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    int best = 15;
    int bestDistance = Integer.MAX_VALUE;
    for (int i = 0; i < colors.length; i++) {
        int cr = (colors[i] >> 16) & 0xFF;
        int cg = (colors[i] >> 8) & 0xFF;
        int cb = colors[i] & 0xFF;
        int dr = r - cr;
        int dg = g - cg;
        int db = b - cb;
        int distance = dr * dr + dg * dg + db * db;
        if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
        }
    }
    return codes[best];
}

String getOverlayChatTitle() {
    String cs = util.colorSymbol;
    return cs + "d" + cs + "lOverlay";
}

String formatOverlayMessage(String suffix) {
    return getOverlayChatTitle() + util.color(suffix);
}

float easeOutCubic(float value) {
    float t = clamp(value, 0.0f, 1.0f);
    return 1.0f - (float) Math.pow(1.0f - t, 3.0f);
}

float updateOverlayAnimation(boolean targetVisible) {
    boolean animate = false;
    try { animate = modules.getButton(scriptName, "Animate"); } catch (Exception ignored) {}
    if (!animate) {
        overlayAlpha = targetVisible ? 1.0f : 0.0f;
        overlayLastAnimAt = client.time();
        return overlayAlpha;
    }
    long now = client.time();
    if (overlayLastAnimAt == 0L) overlayLastAnimAt = now;
    long dt = Math.min(Math.max(now - overlayLastAnimAt, 0L), 100L);
    overlayLastAnimAt = now;
    float speed = targetVisible ? 1.0f / 180.0f : 1.0f / 140.0f;
    overlayAlpha += (targetVisible ? dt : -dt) * speed;
    overlayAlpha = clamp(overlayAlpha, 0.0f, 1.0f);
    return easeOutCubic(overlayAlpha);
}

void resetOverlayAnimation(boolean visible) {
    overlayAlpha = visible ? 1.0f : 0.0f;
    overlayLastAnimAt = client.time();
}

boolean isChatOpen(String screen) { return screen != null && screen.toLowerCase().contains("chat"); }

boolean isEditingPosition() {
    try { return modules.getButton(scriptName, "Edit"); } catch (Exception ignored) {}
    return false;
}

void printEditPositionWarningIfNeeded() {
    if (!isEditingPosition()) { lastEditPositionWarningMs = 0L; return; }
    long now = client.time();
    if (lastEditPositionWarningMs != 0L && now - lastEditPositionWarningMs < 5000L) return;
    lastEditPositionWarningMs = now;
    client.print(formatOverlayMessage("&8\u00bb &cEdit mode is active."));
}

int getOverlayCorner() {
    try { return (int) modules.getSlider(scriptName, "Position"); } catch (Exception ignored) {}
    return 0;
}

boolean isRightOverlayCorner(int corner)  { return corner == 0 || corner == 2; }
boolean isBottomOverlayCorner(int corner) { return corner == 0 || corner == 1; }

float[] getOverlayOffsets(int corner) {
    if (corner == 0) return new float[] {brOffsetX, brOffsetY};
    if (corner == 1) return new float[] {blOffsetX, blOffsetY};
    if (corner == 2) return new float[] {trOffsetX, trOffsetY};
    return new float[] {tlOffsetX, tlOffsetY};
}

void setOverlayOffsets(int corner, float x, float y) {
    if (corner == 0) { brOffsetX = x; brOffsetY = y; }
    else if (corner == 1) { blOffsetX = x; blOffsetY = y; }
    else if (corner == 2) { trOffsetX = x; trOffsetY = y; }
    else { tlOffsetX = x; tlOffsetY = y; }
}

float[] getOverlayQuadrant(int corner) {
    int[] display = client.getDisplaySize();
    float sw = display[0], sh = display[1];
    boolean right = isRightOverlayCorner(corner), bottom = isBottomOverlayCorner(corner);
    return new float[] {
        right ? sw / 2.0f : 0.0f,
        right ? sw : sw / 2.0f,
        bottom ? sh / 2.0f : 0.0f,
        bottom ? sh : sh / 2.0f
    };
}

void applyOverlayPosition(int corner, float panelWidth, float panelHeight) {
    float[] q = getOverlayQuadrant(corner);
    float[] offsets = getOverlayOffsets(corner);
    boolean right = isRightOverlayCorner(corner), bottom = isBottomOverlayCorner(corner);
    float xOffset = clamp(offsets[0], 2.0f, Math.max(2.0f, (q[1] - q[0]) - panelWidth - 2.0f));
    float yOffset = clamp(offsets[1], 2.0f, Math.max(2.0f, (q[3] - q[2]) - panelHeight - 2.0f));
    setOverlayOffsets(corner, xOffset, yOffset);
    startX = right ? (q[1] - panelWidth - xOffset) : (q[0] + xOffset);
    startY = bottom ? (q[3] - panelHeight - yOffset) : (q[2] + yOffset);
}

void drawRectOutline(float x1, float y1, float x2, float y2, float thickness, int color) {
    render.rect(x1, y1, x2, y1 + thickness, color);
    render.rect(x1, y2 - thickness, x2, y2, color);
    render.rect(x1, y1, x1 + thickness, y2, color);
    render.rect(x2 - thickness, y1, x2, y2, color);
}

void drawFastBloom(float x1, float y1, float x2, float y2, float anim) {
    float spread = Math.max(2.0f, 3.0f * textScale);
    render.rect(x1 - spread * 2.0f, y1 - spread * 2.0f, x2 + spread * 2.0f, y2 + spread * 2.0f, multiplyAlpha(0x08000000, anim));
    render.rect(x1 - spread, y1 - spread, x2 + spread, y2 + spread, multiplyAlpha(0x12000000, anim));
    render.rect(x1 - spread * 0.45f, y1 - spread * 0.45f, x2 + spread * 0.45f, y2 + spread * 0.45f, multiplyAlpha(0x1C000000, anim));
}

void drawOverlayQuadrant(int corner) {
    float[] q = getOverlayQuadrant(corner);
    render.rect(q[0], q[2], q[1], q[3], 0x22000000);
    drawRectOutline(q[0] + 1.0f, q[2] + 1.0f, q[1] - 1.0f, q[3] - 1.0f, 1.0f, 0x88FFFFFF);
}

boolean isMouseInside(float mouseX, float mouseY, float x1, float y1, float x2, float y2) {
    return mouseX >= x1 && mouseX <= x2 && mouseY >= y1 && mouseY <= y2;
}

boolean handleOverlayInteraction(boolean chatOpen, boolean editPosition, int corner, float panelWidth, float panelHeight) {
    int[] display = client.getDisplaySize();
    int scale = Math.max(1, display[2]);
    int[] mp = keybinds.getMousePosition();
    float mouseX = mp[0] / (float) scale;
    float mouseY = display[1] - (mp[1] / (float) scale);
    boolean leftDown = keybinds.isMouseDown(0);
    boolean leftJust = leftDown && !overlayPrevLeft;
    overlayPrevLeft = leftDown;

    if (!chatOpen || !editPosition) {
        if (overlayDragging || overlayScaling) saveOverlayHud();
        overlayDragging = false;
        overlayScaling = false;
        return false;
    }

    boolean changed = false;
    float[] q = getOverlayQuadrant(corner);
    float handleSize = Math.max(11.0f, 13.0f * overlayScale);
    float handleX1 = startX + panelWidth - handleSize - 4.0f;
    float handleY1 = startY + panelHeight - handleSize - 4.0f;
    float handleX2 = handleX1 + handleSize;
    float handleY2 = handleY1 + handleSize;
    boolean inside = isMouseInside(mouseX, mouseY, startX, startY, startX + panelWidth, startY + panelHeight);
    boolean insideHandle = isMouseInside(mouseX, mouseY, handleX1, handleY1, handleX2, handleY2);

    if (leftJust && insideHandle && !overlayDragging) {
        overlayScaling = true;
        overlayScaleStart = overlayScale;
        overlayScaleStartX = mouseX;
        overlayScaleStartY = mouseY;
    } else if (leftJust && inside && !overlayScaling) {
        overlayDragging = true;
        overlayDragX = mouseX - startX;
        overlayDragY = mouseY - startY;
    }

    if (!leftDown) {
        if (overlayDragging || overlayScaling) saveOverlayHud();
        overlayDragging = false;
        overlayScaling = false;
    }

    if (overlayDragging) {
        boolean right = isRightOverlayCorner(corner), bottom = isBottomOverlayCorner(corner);
        float targetX = clamp(mouseX - overlayDragX, q[0] + 2.0f, q[1] - panelWidth - 2.0f);
        float targetY = clamp(mouseY - overlayDragY, q[2] + 2.0f, q[3] - panelHeight - 2.0f);
        float nextXOffset = right ? (q[1] - panelWidth - targetX) : (targetX - q[0]);
        float nextYOffset = bottom ? (q[3] - panelHeight - targetY) : (targetY - q[2]);
        nextXOffset = clamp(nextXOffset, 2.0f, Math.max(2.0f, (q[1] - q[0]) - panelWidth - 2.0f));
        nextYOffset = clamp(nextYOffset, 2.0f, Math.max(2.0f, (q[3] - q[2]) - panelHeight - 2.0f));
        setOverlayOffsets(corner, nextXOffset, nextYOffset);
        applyOverlayPosition(corner, panelWidth, panelHeight);
        changed = true;
    }

    if (overlayScaling) {
        float delta = ((mouseX - overlayScaleStartX) + (overlayScaleStartY - mouseY)) / 220.0f;
        overlayScale = clamp(overlayScaleStart + delta, 0.55f, 1.8f);
        applyOverlayScale();
        changed = true;
    }

    return changed;
}

void drawOverlayScaleHandle(float panelWidth, float panelHeight, int accent, float anim) {
    float handleSize = Math.max(11.0f, 13.0f * overlayScale);
    float x1 = startX + panelWidth - handleSize - 4.0f;
    float y1 = startY + panelHeight - handleSize - 4.0f;
    float x2 = x1 + handleSize, y2 = y1 + handleSize;
    render.rect(x1, y1, x2, y2, multiplyAlpha(0xCC14161A, anim));
    drawRectOutline(x1, y1, x2, y2, Math.max(1.0f, 1.0f * overlayScale), multiplyAlpha(0xCCB8BEC4, anim));
    float thickness = Math.max(1.0f, 1.25f * overlayScale);
    int gripColor = multiplyAlpha(accent, anim);
    render.line2D(x1 + 3.0f, y2 - 3.0f, x2 - 3.0f, y1 + 3.0f, thickness, gripColor);
    render.line2D(x1 + 6.0f, y2 - 3.0f, x2 - 3.0f, y1 + 6.0f, thickness, gripColor);
    render.line2D(x1 + 3.0f, y2 - 6.0f, x2 - 6.0f, y1 + 3.0f, thickness, gripColor);
}

float getColumnMinimumWidth(String key) {
    if (key.equals(playerKey))   return 142.0f * textScale;
    if (key.equals(sessionKey))  return 58.0f  * textScale;
    if (key.equals(winstreakKey) || key.equals(winsKey)) return 42.0f * textScale;
    return 48.0f * textScale;
}

float getCellWidth(String key, String text) {
    if (key.equals(playerKey)) {
        String name = getPlayerRenderName(text);
        return headsSize + 9.0f * textScale + textWidth(name) * textScale + 12.0f * textScale;
    }
    return textWidth(text) * textScale;
}

boolean shouldAddStarBrackets() { return _rtAddStarBrackets; }

String getDisplayStarForData(Map<String, Object> playerData) {
    if (playerData == null || playerData.get(starKey) == null) return "";
    int starNumber = 0;
    Object rawStar = playerData.get(starValue);
    if (rawStar instanceof Number) {
        starNumber = ((Number) rawStar).intValue();
    } else {
        String star = playerData.get(starKey).toString();
        String digits = util.strip(star).replaceAll("[^0-9]", "");
        try { if (!digits.isEmpty()) starNumber = Integer.parseInt(digits); } catch (Exception ignored) {}
    }
    if (!shouldAddStarBrackets()) return getPrestigeColor(starNumber);
    return util.color(removePrestigeSymbols(getNickBotPrestigeBracket(starNumber)));
}

boolean shouldColourNameByTeam() { return _rtColourByTeam; }

float getPlayerCellWidth(Map<String, Object> playerData, String text) {
    float width = getCellWidth(playerKey, text);
    boolean nicked = playerData != null && Boolean.TRUE.equals(playerData.get("nicked"));
    if (!nicked && playerData != null && playerData.get(starKey) != null) {
        String star = getDisplayStarForData(playerData);
        if (!star.isEmpty()) width += textWidth(star) * textScale + 4.0f * textScale;
    }
    return width;
}

String getPlayerRenderName(String text) {
    String ign = extractOverlayIgn(text);
    if (ign != null && !ign.isEmpty()) return ign;
    return util.strip(text == null ? "" : text);
}

int getCodeColor(char code) {
    switch (Character.toLowerCase(code)) {
        case '0': return 0xFF000000; case '1': return 0xFF0000AA;
        case '2': return 0xFF00AA00; case '3': return 0xFF00AAAA;
        case '4': return 0xFFAA0000; case '5': return 0xFFAA00AA;
        case '6': return 0xFFFFAA00; case '7': return 0xFFAAAAAA;
        case '8': return 0xFF555555; case '9': return 0xFF5555FF;
        case 'a': return 0xFF55FF55; case 'b': return 0xFF55FFFF;
        case 'c': return 0xFFFF5555; case 'd': return 0xFFFF55FF;
        case 'e': return 0xFFFFFF55; case 'f': return 0xFFFFFFFF;
    }
    return 0xFFE6E6E6;
}

char getFirstColorCode(String text) {
    if (text == null) return ' ';
    char colorChar = util.colorSymbol != null && !util.colorSymbol.isEmpty() ? util.colorSymbol.charAt(0) : '\0';
    for (int i = 0; i + 1 < text.length(); i++) {
        char c = text.charAt(i);
        if ((c == colorChar || c == '&') && isFormattingCode(text.charAt(i + 1))) {
            return Character.toLowerCase(text.charAt(i + 1));
        }
    }
    return ' ';
}

int getPlayerDotColor(String uuid, Map<String, Object> playerStats) {
    String teamDisplay = teams.getOrDefault(uuid, "");
    char code = getTeamColorCode(teamDisplay);
    if (code == ' ' && playerStats != null) {
        Object name = playerStats.get(playerKey);
        if (name != null) code = getFirstColorCode(name.toString());
    }
    return getCodeColor(code);
}

long getLongStat(Map<String, Object> stats, String key, long fallback) {
    if (stats == null || key == null) return fallback;
    Object value = stats.get(key);
    if (value instanceof Number) return ((Number) value).longValue();
    if (value == null) return fallback;
    try { return Long.parseLong(value.toString()); } catch (Exception ignored) {}
    return fallback;
}

String getLiveOnlineText(Map<String, Object> playerStats, Object fallback) {
    if (playerStats != null && Boolean.TRUE.equals(playerStats.get("nicked"))) return util.colorSymbol + "eN";
    long lastLogin  = getLongStat(playerStats, "login", 0L);
    long lastLogout = getLongStat(playerStats, "logout", 0L);
    String rawText = fallback != null ? fallback.toString() : "";
    String text = util.strip(rawText).trim();
    if (text.startsWith("API")) return rawText.indexOf(util.colorSymbol) != -1 ? rawText : util.colorSymbol + "c" + text;
    if (lastLogin > 0L) {
        text = lastLogin - lastLogout > -2000L ? calculateRelativeTimestamp(lastLogin, client.time()) : "OFFLINE";
    }
    if (text.isEmpty() || text.equals("API")) text = "--";
    if (text.equals("..."))     return util.colorSymbol + "8...";
    if (text.equals("--"))      return util.colorSymbol + "c--";
    if (text.equals("OFFLINE")) return util.colorSymbol + "cOFFLINE";
    return util.colorSymbol + "7" + text;
}

String getSelfUuid() {
    try { Entity player = client.getPlayer(); return player != null ? player.getUUID().replace("-", "") : ""; }
    catch (Exception ignored) {}
    return "";
}

boolean isSelfUuid(String uuid) {
    String self = getSelfUuid();
    return uuid != null && !uuid.isEmpty() && self != null && uuid.equalsIgnoreCase(self);
}

String getTeamTokenForUuid(String uuid) {
    if (uuid == null || uuid.isEmpty()) return "";
    String display = teams.getOrDefault(uuid, "");
    if (display == null || display.isEmpty()) return "";
    return getTeamToken(display).toLowerCase();
}

boolean isOwnTeamUuid(String uuid) {
    if (status != 3 || uuid == null || uuid.isEmpty()) return false;
    if (isSelfUuid(uuid)) return false;
    String selfToken = getTeamTokenForUuid(getSelfUuid());
    String otherToken = getTeamTokenForUuid(uuid);
    return !selfToken.isEmpty() && selfToken.equals(otherToken);
}

int getOverlayRowColor(String uuid, boolean isNicked, int rowIndex) {
    if (isSelfUuid(uuid)) return withAlpha(getOverlayThemeColor(), 36);
    if (isOwnTeamUuid(uuid)) return rowIndex % 2 == 0 ? 0x55383C42 : 0x4B30343A;
    if (isNicked) return 0x55302C28;
    return rowIndex % 2 == 0 ? 0x6417191D : 0x4A22252A;
}

void drawTableShell(float x, float y, float width, float height, int accent, float anim) {
    render.rect(x, y, x + width, y + height, multiplyAlpha(0xD807080A, anim));
    render.rect(x + 1.0f, y + 1.0f, x + width - 1.0f, y + lineHeight + 1.0f, multiplyAlpha(0xAA101113, anim));
    drawRectOutline(x, y, x + width, y + height, Math.max(1.0f, 1.0f * textScale), multiplyAlpha(0x77324246, anim));
}

void drawColumnSections(float y, float height, int accent, float anim) {
    for (int i = 1; i < columns.size(); i++) {
        Map<String, Object> column = columns.get(i);
        float x = Float.parseFloat(column.get("position").toString()) - 6.0f * textScale;
        render.rect(x, y + 1.0f, x + Math.max(1.0f, 0.75f * textScale), y + height - 1.0f, multiplyAlpha(0x33393D42, anim));
    }
    render.rect(startX, y + lineHeight, endX, y + lineHeight + Math.max(1.0f, 0.75f * textScale), multiplyAlpha(0x55324246, anim));
}

void drawPlayerCell(String uuid, Map<String, Object> playerStats, Object statValue, float x, float y, float maxWidth, float anim) {
    Image head = playerStats != null && playerStats.get(headsKey) instanceof Image ? (Image) playerStats.get(headsKey) : defaultHead;
    float headSize = Math.max(8.0f * textScale, lineHeight - 3.0f * textScale);
    float headY = y + (lineHeight - headSize) / 2.0f;
    render.image((head != null && head.isLoaded()) ? head : defaultHead, x, headY, headSize, headSize);

    String name = getPlayerRenderName(statValue != null ? statValue.toString() : "");
    boolean nicked = playerStats != null && Boolean.TRUE.equals(playerStats.get("nicked"));
    String star = !nicked && playerStats != null && playerStats.get(starKey) != null ? getDisplayStarForData(playerStats) : "";
    float nameX = x + headSize + 7.0f * textScale;
    float nameY = y + (lineHeight - fontHeight) / 2.0f;
    if (!star.isEmpty()) {
        drawOverlayText(star, nameX, nameY, textScale, multiplyAlpha(0xFFFFFFFF, anim));
        nameX += textWidth(star) * textScale + 4.0f * textScale;
    }
    int teamColor = getPlayerDotColor(uuid, playerStats);
    int nameColor = shouldColourNameByTeam() ? teamColor : 0xFFE8ECEF;
    drawOverlayText(name, nameX, nameY, textScale, multiplyAlpha(nameColor, anim));

    if (!shouldColourNameByTeam()) {
        float dot = Math.max(3.0f, 4.0f * textScale);
        float dotX = Math.min(x + maxWidth - dot - 3.0f * textScale, nameX + textWidth(name) * textScale + 4.0f * textScale);
        float dotY = nameY + fontHeight / 2.0f - dot / 2.0f;
        render.roundedRect(dotX, dotY, dotX + dot, dotY + dot, dot / 2.0f, multiplyAlpha(teamColor, anim));
    }
}

void onRenderTick(float partialTicks) {
    try { _rtStartWithF      = modules.getButton(scriptName, "Start with {f}"); } catch (Exception ignored) { _rtStartWithF = false; }
    try { _rtColourByTeam    = modules.getSlider(scriptName, "Team colour") >= 1.0; } catch (Exception ignored) { _rtColourByTeam = false; }
    try { _rtAddStarBrackets = modules.getButton(scriptName, "Add brackets to star"); } catch (Exception ignored) { _rtAddStarBrackets = false; }

    String screen = client.getScreen();
    boolean chatOpen = isChatOpen(screen);
    boolean editPosition = isEditingPosition();
    boolean previewMode = chatOpen && editPosition;
    boolean otherScreenOpen = screen != null && !screen.isEmpty() && !chatOpen;
    if (otherScreenOpen) { resetOverlayAnimation(false); return; }

    boolean targetVisible = !otherScreenOpen && overlayTicks >= 5 && columns.size() > 0
        && ((currentPlayers.size() > 0 && isOverlayVisible()) || previewMode);

    float anim = updateOverlayAnimation(targetVisible);
    if (anim <= 0.0f) return;

    int corner = getOverlayCorner();
    int accent = 0xFFB8BEC4;

    if (previewMode) drawOverlayQuadrant(corner);

    float panelWidth  = Math.max(1.0f, endX - startX);
    float panelHeight = Math.max(1.0f, endY - startY);
    float beforeX = startX, beforeY = startY;
    applyOverlayPosition(corner, panelWidth, panelHeight);
    boolean positionChanged = Math.abs(beforeX - startX) > 0.05f || Math.abs(beforeY - startY) > 0.05f;

    if (overlayNeedsLayout || positionChanged) {
        doColumns();
        overlayNeedsLayout = false;
        panelWidth  = Math.max(1.0f, endX - startX);
        panelHeight = Math.max(1.0f, endY - startY);
        beforeX = startX; beforeY = startY;
        applyOverlayPosition(corner, panelWidth, panelHeight);
        if (Math.abs(beforeX - startX) > 0.05f || Math.abs(beforeY - startY) > 0.05f) {
            doColumns();
            panelWidth  = Math.max(1.0f, endX - startX);
            panelHeight = Math.max(1.0f, endY - startY);
        }
    }

    if (handleOverlayInteraction(chatOpen, editPosition, corner, panelWidth, panelHeight)) {
        applyOverlayPosition(corner, panelWidth, panelHeight);
        doColumns();
        overlayNeedsLayout = false;
        panelWidth  = Math.max(1.0f, endX - startX);
        panelHeight = Math.max(1.0f, endY - startY);
    }

    float drawStartY = startY - (1.0f - anim) * 8.0f * textScale;
    float drawEndY   = endY   - (1.0f - anim) * 8.0f * textScale;

    boolean bloomEnabled = false;
    try { bloomEnabled = modules.getButton(scriptName, "Bloom"); } catch (Exception ignored) {}
    if (bloomEnabled && anim > 0.02f) drawFastBloom(startX, drawStartY, endX, drawEndY, anim);

    drawTableShell(startX, drawStartY, panelWidth, panelHeight, accent, anim);
    drawColumnSections(drawStartY, panelHeight, accent, anim);
    int categoryColor = getOverlayThemeColor();
    String sortedColumnKey = getSortedColumnKey();

    for (Map<String, Object> column : columns) {
        String statKey = column.get("key").toString();
        if (statKey.equals(headsKey)) continue;
        String title = column.get("header").toString();
        float x = Float.parseFloat(column.get("position").toString());
        float headerTextY = drawStartY + offsetY;
        drawOverlayText(title, x, headerTextY, textScale, multiplyAlpha(categoryColor, anim));
        if (statKey.equals(sortedColumnKey)) {
            float arrowX = x + textWidth(title) * textScale + 3.0f * textScale;
            drawOverlayText("^", arrowX, headerTextY, textScale, multiplyAlpha(0xFF55FF55, anim));
        }
    }

    float y = drawStartY + lineHeight + 2f * textScale;
    int rowIndex = 0;

    synchronized (currentPlayers) { for (String uuid : currentPlayers) {
        Map<String, Object> playerStats = overlayPlayers.get(uuid);
        if (playerStats == null) { overlayPlayers.remove(uuid); continue; }

        boolean isNicked     = (Boolean) playerStats.getOrDefault("nicked", false);
        boolean isError      = (Boolean) playerStats.getOrDefault("error", false);
        String requestState  = String.valueOf(playerStats.getOrDefault(requestStateKey, ""));
        boolean isLoading    = "loading".equals(requestState);
        boolean isRateLimited = "rate".equals(requestState);
        render.rect(startX, y, endX, y + lineHeight, multiplyAlpha(getOverlayRowColor(uuid, isNicked, rowIndex), anim));

        for (Map<String, Object> column : columns) {
            String statKey = column.get("key").toString();
            float maxWidth = Float.parseFloat(column.get("maxwidth").toString());
            Object statValue = playerStats.get(statKey);
            String stringStatValue = String.valueOf(statValue);
            float x = Float.parseFloat(column.get("position").toString());

            if (isNicked) {
                if (statKey.equals(headsKey)) {
                    statValue = defaultHead;
                } else if (!statKey.equals(playerKey)) {
                    statValue = util.colorSymbol + "eN";
                } else if (!shouldUseTeamDisplay(uuid)) {
                    statValue = util.colorSymbol + 'e' + stringStatValue.replaceAll(util.colorSymbol + ".", "");
                }
            } else if (!statKey.equals(playerKey) && !statKey.equals(headsKey)) {
                if (isRateLimited && (statValue == null || stringStatValue.isEmpty() || stringStatValue.equals(util.colorSymbol + "8..."))) {
                    statValue = formatRetryCountdown("API", 'c', playerStats);
                } else if (isError && (statValue == null || stringStatValue.isEmpty() || stringStatValue.equals(util.colorSymbol + "8..."))) {
                    statValue = formatRetryCountdown("E", '4', playerStats);
                } else if (isLoading && (statValue == null || stringStatValue.isEmpty())) {
                    statValue = util.colorSymbol + "8...";
                }
            }

            switch (statKey) {
                case playerKey:
                    if (isNicked && !shouldUseTeamDisplay(uuid))
                        statValue = util.colorSymbol + 'e' + stringStatValue.replaceAll(util.colorSymbol + ".", "");
                    if (isError && (statValue == null || stringStatValue.isEmpty() || stringStatValue.equals(util.colorSymbol + "7-")))
                        statValue = util.colorSymbol + "4E";
                    if (statValue == null || stringStatValue.isEmpty()) { overlayPlayers.remove(uuid); continue; }
                    drawPlayerCell(uuid, playerStats, statValue, x, y, maxWidth, anim);
                    continue;
                case tagsKey:
                    if (stringStatValue.isEmpty()) {
                        StringBuilder statValueBuilder = new StringBuilder();
                        for (String tag : tags) {
                            if (!playerStats.containsKey(tag)) continue;
                            String realTag = String.valueOf(playerStats.get(tag));
                            if (realTag.isEmpty()) continue;
                            if (statValueBuilder.length() > 0 && !realTag.startsWith(" ")) statValueBuilder.append(" ");
                            statValueBuilder.append(realTag);
                        }
                        statValue = statValueBuilder.length() > 0 ? statValueBuilder.toString()
                                  : isNicked ? util.colorSymbol + "eN"
                                  : isLoading ? util.colorSymbol + "8..." : null;
                    }
                    break;
                case sessionKey:
                    statValue = getLiveOnlineText(playerStats, statValue);
                    if (statValue.toString().isEmpty()) statValue = util.colorSymbol + "c--";
                    break;
                case winstreakKey:
                    if (statValue == null || statValue.toString().isEmpty()) statValue = util.colorSymbol + "c--";
                    break;
                case headsKey:
                    Image head = statValue != null ? (Image) statValue : null;
                    render.image((head != null && head.isLoaded()) ? head : defaultHead, x, y, headsSize, headsSize);
                    continue;
            }

            String text = statValue != null ? statValue.toString() : "";
            if (text.isEmpty()) continue;
            float textY = y + (lineHeight - fontHeight) / 2.0f;
            drawOverlayText(text, x, textY, textScale, multiplyAlpha(0xFFFFFFFF, anim));
        }
        y += lineHeight;
        rowIndex++;
    }}

    drawRectOutline(startX, drawStartY, endX, drawEndY, Math.max(1.0f, 1.0f * textScale), multiplyAlpha(0x77324246, anim));

    if (previewMode) drawOverlayScaleHandle(panelWidth, panelHeight, accent, anim);
}

boolean onChat(String message, int type) {
    String msg = util.strip(message);
    if (status == 3) updateDeadStateFromChat(msg);

    if (dowho && ((msg.endsWith("!") && msg.contains("has joined")) || msg.startsWith("You will respawn in"))) {
        dowho = false;
        listeningForChat = true;
        client.async(() -> {
            client.sleep(500);
            if (status > 1 && timeUntilStart() > 5) client.chat("/who");
        });
        return true;
    }

    if (msg.equals("Couldn't find players, sorry!") || msg.equals("Could not find players, sorry!")) {
        listeningForChat = true;
        return true;
    }

    if (listeningForChat && status == 2) {
        String chatUsername = extractChatUsername(msg);
        if (chatUsername != null && !chatUsername.isEmpty()) {
            String existingUuid = findOverlayUuidByName(chatUsername);
            if (existingUuid == null || existingUuid.isEmpty()) {
                if (!ignoredPlayers.containsKey(chatUsername.toLowerCase())) {
                    final String lobbySnapshot = getLobbyId();
                    final String whoName = chatUsername;
                    final String whoKey = whoName.toLowerCase();
                    if (pendingWhoResolves.putIfAbsent(whoKey, true) == null) {
                        client.async(() -> {
                            try {
                                if (hasChangedLobby(lobbySnapshot)) return;
                                String[] conversion = convertPlayerFlashlight(whoName);
                                String uuid = conversion[0];
                                String resolvedName = conversion[1];
                                if (uuid == null || uuid.isEmpty()) { conversion = convertPlayer(whoName); uuid = conversion[0]; resolvedName = conversion[1]; }
                                if (uuid == null || uuid.isEmpty()) { conversion = convertPlayerPlayerdb(whoName); uuid = conversion[0]; resolvedName = conversion[1]; }
                                if (uuid == null || uuid.isEmpty() || hasChangedLobby(lobbySnapshot)) return;
                                if (resolvedName == null || resolvedName.isEmpty()) resolvedName = whoName;
                                synchronized(currentPlayers) {
                                    if (!overlayPlayers.containsKey(uuid)) {
                                        addPlaceholderStats(uuid, resolvedName, true);
                                        addToPlayers(uuid);
                                        Map<String, Object> chatFlag = new ConcurrentHashMap<>();
                                        chatFlag.put("chatadded", true);
                                        addToOverlay(uuid, chatFlag);
                                    }
                                }
                                onPlayerAdd(uuid);
                            } catch (Exception e) {
                                client.log("Error resolving chat player " + whoName + ": " + e);
                            } finally {
                                pendingWhoResolves.remove(whoKey);
                            }
                        });
                    }
                }
            }
        }
    }

    if (msg.startsWith("ONLINE: ")) {
        String[] players = msg.substring(8).split(", ");
        syncWhoPlayers(players);
        if (!didwho) { didwho = true; client.log("[CHAT] " + msg); return false; }
    }
    return true;
}

String sanitizeIgnToken(String token) {
    if (token == null || token.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (char c : token.toCharArray()) { if (Character.isLetterOrDigit(c) || c == '_') sb.append(c); }
    return sb.toString();
}

boolean isValidIgn(String username) {
    if (username == null) return false;
    int len = username.length();
    if (len < 1 || len > 25) return false;
    for (char c : username.toCharArray()) { if (!(Character.isLetterOrDigit(c) || c == '_')) return false; }
    return true;
}

String extractChatUsername(String msg) {
    if (msg == null || msg.isEmpty()) return null;
    int colonIndex = msg.indexOf(": ");
    if (colonIndex == -1) return null;
    String prefix = msg.substring(0, colonIndex).trim();
    if (prefix.isEmpty()) return null;
    if (prefix.startsWith("ONLINE") || prefix.startsWith("Party") || prefix.startsWith("Guild")) return null;
    String[] parts = prefix.split(" ");
    String candidate = parts[parts.length - 1].replaceAll("[\\[\\]]", "");
    candidate = sanitizeIgnToken(candidate);
    if (isValidIgn(candidate) && candidate.length() >= 2) return candidate;
    return null;
}

String extractOverlayIgn(String overlayName) {
    if (overlayName == null || overlayName.isEmpty()) return "";
    String stripped = util.strip(overlayName).trim();
    if (stripped.isEmpty()) return "";
    if (stripped.contains(" ")) stripped = stripped.split(" ")[stripped.split(" ").length - 1];
    return sanitizeIgnToken(stripped);
}

String getNickedDisplayName(String displayName, String username) {
    String display = util.strip(displayName == null ? "" : displayName).trim();
    if (display.contains(" ")) display = display.split(" ", 2)[1].trim();
    display = sanitizeIgnToken(display);
    if (isValidIgn(display)) return display;
    String fallback = sanitizeIgnToken(username == null ? "" : username);
    return isValidIgn(fallback) ? fallback : "Nick";
}

String findOverlayUuidByName(String username) {
    if (username == null || username.isEmpty()) return null;
    for (String uuidKey : overlayPlayers.keySet()) {
        Map<String, Object> playerData = overlayPlayers.get(uuidKey);
        if (playerData == null) continue;
        Object usernameObj = playerData.get(playerKey);
        if (!(usernameObj instanceof String)) continue;
        String overlayName = util.strip((String) usernameObj);
        if (overlayName.contains(" ")) overlayName = overlayName.split(" ", 2)[1];
        if (overlayName.equalsIgnoreCase(username)) return uuidKey;
    }
    return null;
}

void syncWhoPlayers(String[] players) {
    if (status <= 1) return;
    final String lobbySnapshot = getLobbyId();

    for (int i = 0; i < players.length; i++) {
        String rawName = players[i];
        if (rawName == null) continue;
        final String whoName = rawName.trim();
        if (whoName.isEmpty()) continue;
        if (ignoredPlayers.containsKey(whoName.toLowerCase())) continue;
        if (status == 3 && shouldCollapseDeadPlayers() && deadPlayers.containsKey(whoName.toLowerCase())) continue;

        String existingUuid = findOverlayUuidByName(whoName);
        if (existingUuid != null && !existingUuid.isEmpty()) continue;

        final String whoKey = whoName.toLowerCase();
        if (pendingWhoResolves.putIfAbsent(whoKey, true) != null) continue;

        client.async(() -> {
            try {
                if (hasChangedLobby(lobbySnapshot)) return;
                String[] conversion = convertPlayerFlashlight(whoName);
                String uuid = conversion[0];
                String resolvedName = conversion[1];
                if (uuid == null || uuid.isEmpty()) { conversion = convertPlayer(whoName); uuid = conversion[0]; resolvedName = conversion[1]; }
                if (uuid == null || uuid.isEmpty()) { conversion = convertPlayerPlayerdb(whoName); uuid = conversion[0]; resolvedName = conversion[1]; }
                if (uuid == null || uuid.isEmpty() || hasChangedLobby(lobbySnapshot)) return;
                if (resolvedName == null || resolvedName.isEmpty()) resolvedName = whoName;
                if (status == 3 && shouldCollapseDeadPlayers() && deadPlayers.containsKey(resolvedName.toLowerCase())) return;
                synchronized(currentPlayers) {
                    if (!overlayPlayers.containsKey(uuid)) {
                        addPlaceholderStats(uuid, resolvedName, true);
                        addToPlayers(uuid);
                    }
                }
                onPlayerAdd(uuid);
            } catch (Exception e) {
                client.log("Error syncing /who player " + whoName + ": " + e);
            } finally {
                pendingWhoResolves.remove(whoKey);
            }
        });
    }
}

void onWorldJoin(Entity entity) {
    if (client.getPlayer() == entity) {
        dowho = true;
        didwho = false;
        listeningForChat = false;
        overlayTicks = 0;
        overlayToggleVisible = false;
        showBindWasDown = false;
        clearMaps();
    }
}

void addToOverlay(String uuid, Map<String, Object> newData) {
    try {
        Map<String, Object> existingData = overlayPlayers.get(uuid);
        if (existingData == null) return;
        existingData.putAll(newData);
        overlayPlayers.put(uuid, existingData);
        overlayNeedsLayout = true;
        overlayNeedsSort = true;
    } catch (Exception e) {
        client.log("Error in addToOverlay: " + e);
    }
}

Comparator<String> comparator = (uuid1, uuid2) -> {
    try {
        Map<String, Object> stats1 = overlayPlayers.get(uuid1);
        Map<String, Object> stats2 = overlayPlayers.get(uuid2);

        if (shouldSortByTeam()) {
            int teamCompare = compareTeamOrder(uuid1, uuid2);
            if (teamCompare != 0) return teamCompare;
        }

        boolean isNicked1 = stats1 != null && Boolean.TRUE.equals(stats1.get("nicked"));
        boolean isNicked2 = stats2 != null && Boolean.TRUE.equals(stats2.get("nicked"));
        if (isNicked1 && !isNicked2) return ascending ? -1 : 1;
        if (!isNicked1 && isNicked2) return ascending ? 1 : -1;

        String val1 = (stats1 != null && stats1.get(sortBy) != null) ? stats1.get(sortBy).toString() : "-";
        String val2 = (stats2 != null && stats2.get(sortBy) != null) ? stats2.get(sortBy).toString() : "-";
        val1 = val1.replaceAll(util.colorSymbol + ".", "");
        val2 = val2.replaceAll(util.colorSymbol + ".", "");

        boolean n1 = containsDigit(val1), n2 = containsDigit(val2);
        if (!n1 && !n2) return 0;
        if (!n1) return ascending ? 1 : -1;
        if (!n2) return ascending ? -1 : 1;

        int cmp = ascending ? Double.compare(Double.parseDouble(val2), Double.parseDouble(val1))
                            : Double.compare(Double.parseDouble(val1), Double.parseDouble(val2));
        if (cmp != 0) return cmp;

        String name1 = stats1 != null && stats1.get(playerKey) != null ? util.strip(stats1.get(playerKey).toString()) : uuid1;
        String name2 = stats2 != null && stats2.get(playerKey) != null ? util.strip(stats2.get(playerKey).toString()) : uuid2;
        return name1.compareToIgnoreCase(name2);
    } catch (NumberFormatException e) {
        client.log("NumberFormatException for " + uuid1 + " or " + uuid2);
        return ascending ? -1 : 1;
    }
};

int compareTeamOrder(String uuid1, String uuid2) {
    if (status != 3) return 0;
    String team1 = teams.getOrDefault(uuid1, "");
    String team2 = teams.getOrDefault(uuid2, "");
    boolean hasTeam1 = !team1.isEmpty(), hasTeam2 = !team2.isEmpty();
    if (!hasTeam1 && !hasTeam2) return 0;
    if (hasTeam1 != hasTeam2) return hasTeam1 ? -1 : 1;
    String token1 = getTeamToken(team1), token2 = getTeamToken(team2);
    if (token1.equalsIgnoreCase(token2)) return 0;
    int p1 = getTeamPriority(team1), p2 = getTeamPriority(team2);
    if (p1 != p2) return Integer.compare(p1, p2);
    int statCmp = Double.compare(
        teamSortValues.getOrDefault(token2.toLowerCase(), Double.NEGATIVE_INFINITY),
        teamSortValues.getOrDefault(token1.toLowerCase(), Double.NEGATIVE_INFINITY));
    if (statCmp != 0) return statCmp;
    return token1.compareToIgnoreCase(token2);
}

String getTeamToken(String displayName) {
    String stripped = util.strip(displayName);
    int splitAt = stripped.indexOf(' ');
    return splitAt == -1 ? stripped : stripped.substring(0, splitAt);
}

int getTeamPriority(String displayName) {
    char colorCode = getTeamColorCode(displayName);
    String teamOrder = "c9aebfd7";
    int priority = teamOrder.indexOf(colorCode);
    if (priority != -1) return priority;
    String token = getTeamToken(displayName);
    if (token.startsWith("[R")) return 0;
    if (token.startsWith("[B")) return 1;
    if (token.startsWith("[G")) return 2;
    if (token.startsWith("[Y")) return 3;
    if (token.startsWith("[A")) return 4;
    if (token.startsWith("[W")) return 5;
    if (token.startsWith("[P")) return 6;
    return 7;
}

char getTeamColorCode(String displayName) {
    int index = displayName.indexOf(util.colorSymbol);
    if (index == -1 || index + 1 >= displayName.length()) return ' ';
    return Character.toLowerCase(displayName.charAt(index + 1));
}

boolean containsDigit(String s) {
    for (char c : s.toCharArray()) { if (Character.isDigit(c)) return true; }
    return false;
}

String[] convertPlayer(String player) {
    boolean isUUID = player.length() < 37 && (player.length() == 32 && player.charAt(12) == '4') || (player.length() == 36 && player.charAt(14) == '4');
    String url = isUUID ? "https://sessionserver.mojang.com/session/minecraft/profile/" + player : "https://api.mojang.com/users/profiles/minecraft/" + player;
    try {
        Object[] r = get(url, 3000);
        if ((int)r[1] == 200) { Json j = (Json)r[0]; return new String[] { j.get("id", ""), j.get("name", "") }; }
        client.log("HTTP Error " + r[1] + " getting uuid on " + player);
        return new String[] { "", "" };
    } catch (Exception e) {
        client.log("Runtime error getting uuid on " + player + ": " + e);
        return new String[] { "", "" };
    }
}

String[] convertPlayerFlashlight(String player) {
    if (player == null || player.isEmpty()) return new String[] { "", "" };
    boolean isUndashedUuid = player.length() == 32 && player.charAt(12) == '4';
    boolean isDashedUuid   = player.length() == 36 && player.charAt(14) == '4';
    if (isUndashedUuid || isDashedUuid) return new String[] { player.replace("-", ""), player };
    try {
        Object[] r = get(prismAccountUrl + player, 3000, createPrismHeaders());
        if ((int) r[1] == 200) {
            Json data = ((Json) r[0]).object();
            if (!"true".equalsIgnoreCase(data.get("success", "false"))) return new String[] { "", "" };
            String uuid = data.get("uuid", "");
            String username = data.get("username", "");
            if (uuid == null) uuid = "";
            if (username == null) username = "";
            if (!uuid.isEmpty()) return new String[] { uuid.replace("-", ""), username };
        }
        return new String[] { "", "" };
    } catch (Exception e) {
        client.log("Runtime error getting account on " + player + ": " + e);
        return new String[] { "", "" };
    }
}

String[] convertPlayerPlayerdb(String player) {
    try {
        Object[] r = get("https://playerdb.co/api/player/minecraft/" + player, 3000);
        if ((int)r[1] == 200) {
            Json thing = ((Json)r[0]).object().object("data").object("player");
            return new String[] { thing.get("raw_id", ""), thing.get("username", "") };
        }
        client.log("HTTP Error " + r[1] + " getting uuid on " + player);
        return new String[] { "", "" };
    } catch (Exception e) {
        client.log("Runtime error getting uuid on " + player + ": " + e);
        return new String[] { "", "" };
    }
}

Object[] get(String url, int timeout) { return get(url, timeout, null); }

Object[] get(String url, int timeout, Map<String, String> headers) {
    Json jsonData = new Json("{}");
    try {
        Request request = new Request("GET", url);
        request.setConnectTimeout(timeout);
        request.setReadTimeout(timeout);
        request.setUserAgent(chromeUserAgent);
        if (headers != null) {
            for (String key : headers.keySet()) {
                String value = headers.get(key);
                if (key == null || value == null || value.isEmpty()) continue;
                request.addHeader(key, value);
            }
        }
        Response response = request.fetch();
        int code = response != null ? response.code() : 404;
        if (response != null) { try { jsonData = response.json(); } catch (Exception ignored) {} }
        return new Object[] { jsonData, code };
    } catch (Exception e) {
        client.log("Error in get function: " + e);
        return new Object[] { jsonData, 500 };
    }
}

int getBedwarsStatus() {
    List<String> sidebar = world.getScoreboard();
    replayMode = false;
    if (sidebar == null) return world.getDimension().equals("The End") ? 0 : -1;
    int size = sidebar.size();
    if (size == 0) return -1;
    String title = util.strip(sidebar.get(0)).trim();
    replayMode = isReplayScoreboard(sidebar);
    if (!title.startsWith("BED WARS") && !replayMode) return -1;
    if (size < 7 && !replayMode) return -1;
    String lobbyId = extractLobbyId(sidebar);
    if (!lobbyId.isEmpty()) currentLobby = lobbyId;
    if (!lobbyId.isEmpty() && lobbyId.charAt(0) == 'L') return 1;
    if (containsInGameSidebar(sidebar)) return 3;
    if (replayMode) return 3;
    if (containsStartLine(sidebar)) return 2;
    return -1;
}

boolean isReplayScoreboard(List<String> sidebar) {
    for (String raw : sidebar) { if (util.strip(raw).trim().toUpperCase().contains("REPLAY")) return true; }
    return false;
}

String extractLobbyId(List<String> sidebar) {
    for (String line : sidebar) {
        String stripped = util.strip(line).trim();
        if (stripped.isEmpty()) continue;
        int index = stripped.indexOf("  ");
        if (index == -1 || index + 2 >= stripped.length()) continue;
        String suffix = stripped.substring(index + 2).trim();
        if (suffix.isEmpty()) continue;
        String candidate = suffix.split(" ")[0];
        if (candidate.endsWith("]")) candidate = candidate.substring(0, candidate.length() - 1);
        if (candidate.length() >= 2 && Character.isLetter(candidate.charAt(0))) return candidate;
    }
    return currentLobby;
}

boolean containsTeamLines(List<String> sidebar) {
    int teamLines = 0;
    for (String line : sidebar) { if (isBedwarsTeamLine(util.strip(line).trim())) { if (++teamLines >= 2) return true; } }
    return false;
}

boolean containsInGameSidebar(List<String> sidebar) {
    if (containsTeamLines(sidebar)) return true;
    for (String line : sidebar) {
        String stripped = util.strip(line).trim();
        if (stripped.isEmpty()) continue;
        if (stripped.contains("Diamond") || stripped.contains("Emerald")) return true;
        if (stripped.startsWith("Kills:") || stripped.startsWith("Final Kills:") || stripped.startsWith("Beds Broken:")) return true;
        if (stripped.contains(" YOU") || stripped.endsWith("YOU")) return true;
    }
    return false;
}

boolean containsStartLine(List<String> sidebar) {
    for (String line : sidebar) { String stripped = util.strip(line).trim(); if (stripped.equals("Waiting...") || stripped.startsWith("Starting in ")) return true; }
    return false;
}

boolean isBedwarsTeamLine(String line) {
    return line.startsWith("R Red:") || line.startsWith("B Blue:") || line.startsWith("G Green:")
        || line.startsWith("Y Yellow:") || line.startsWith("A Aqua:") || line.startsWith("W White:")
        || line.startsWith("P Pink:") || line.startsWith("G Gray:") || line.contains(" YOU");
}

void sortOverlay() {
    refreshTeamSortValues();
    synchronized(currentPlayers) { currentPlayers.sort(comparator); }
}

String getBotReason(NetworkPlayer pla) {
    final String uuid = pla.getUUID();
    if (pla.getPing() < 0) return "negative ping";
    if (pla.getName().length() < 2) return "name too short";
    if (uuid.charAt(14) != '4' && uuid.charAt(14) != '1') return "uuid version " + uuid.charAt(14);
    if (overlayTicks < 80 && pla.getDisplayName().startsWith(util.colorSymbol + "c") && !pla.getDisplayName().contains(" ")) return "startup red name";
    if (status == 3 && !replayMode && !pla.getDisplayName().contains(" ")) return "missing team prefix in-game";
    return "";
}

int timeUntilStart() {
    List<String> scoreboard = world.getScoreboard();
    if (scoreboard == null || scoreboard.size() < 7) return -1;
    for (String rawLine : scoreboard) {
        String line = util.strip(rawLine).trim();
        if (!line.startsWith("Starting in ")) { if (line.equals("Waiting...")) return 20; continue; }
        String[] parts = line.split(" ");
        String lastPart = parts[parts.length - 1];
        if (!lastPart.endsWith("s")) return -1;
        return Integer.parseInt(lastPart.substring(0, lastPart.length() - 1));
    }
    return -1;
}

void clearMaps() {
    teams.clear();
    teamSortValues.clear();
    overlayPlayers.clear();
    statsCache.clear();
    urchinCache.clear();
    deadPlayers.clear();
    overlayNeedsSort = false;
    overlayNeedsLayout = false;
    pendingStatsRequests.clear();
    statsFailureCounts.clear();
    pendingWhoResolves.clear();
    synchronized (statsRateLock) { statsRequestTimes.clear(); statsGlobalCooldownUntil = 0L; }
    synchronized(currentPlayers) { currentPlayers.clear(); }
}

void addColumn(String display, String header, String key) {
    Map<String, Object> columnData = new HashMap<>();
    columnData.put("display", display);
    columnData.put("header", header);
    columnData.put("key", key);
    columnData.put("width", textWidth(header));
    columnData.put("maxwidth", textWidth(header));
    columnData.put("position", 0);
    columns.add(columnData);
}

void addTag(String newTag) { tags.add(newTag); }

void doColumns() {
    float currentX = startX + 8f * textScale;
    for (Map<String, Object> column : columns) {
        String statKey = column.get("key").toString();
        String header = column.get("header").toString();
        float longest = Math.max(textWidth(header) * textScale, getColumnMinimumWidth(statKey));

        synchronized (currentPlayers) { for (String uuid : currentPlayers) {
            if (uuid == null) continue;
            Map<String, Object> playerData = overlayPlayers.get(uuid);
            if (playerData == null) continue;
            Object statValueObj = playerData.get(statKey);
            if (statValueObj == null) continue;
            String statValue;
            if (statKey.equals(tagsKey)) {
                StringBuilder statValueBuilder = new StringBuilder();
                for (String tag : tags) {
                    Object tagObj = playerData.get(tag);
                    if (tagObj == null) continue;
                    String tagStr = tagObj.toString();
                    if (tagStr.isEmpty()) continue;
                    if (statValueBuilder.length() > 0 && !tagStr.startsWith(" ")) statValueBuilder.append(" ");
                    statValueBuilder.append(tagStr);
                }
                statValue = statValueBuilder.length() > 0 ? statValueBuilder.toString() : "";
            } else {
                statValue = statValueObj.toString();
            }
            if (statKey.equals(sessionKey)) statValue = getLiveOnlineText(playerData, statValueObj);
            float width = statKey.equals(playerKey) ? getPlayerCellWidth(playerData, statValue) : getCellWidth(statKey, statValue);
            if (width > longest) longest = width;
        }};

        column.put("maxwidth", longest);
        column.put("position", currentX);
        currentX += longest + (12f * textScale);
    }
    endX = currentX - 4f * textScale;
    endY = startY + lineHeight + (currentPlayers.size() * lineHeight) + (currentPlayers.size() > 0 ? 4f * textScale : textScale);
}

void updateStatus() {
    lastLobby = getLobbyId();
    int previousStatus = status;
    status = getBedwarsStatus();
    if (!lastLobby.equals(getLobbyId())) { clearMaps(); listeningForChat = false; }
    if (status == 3 && previousStatus != 3) listeningForChat = false;
}

void addToPlayers(String uuid) { synchronized(currentPlayers) {
    if (ascending) {
        if (uuid.charAt(12) == '4') currentPlayers.add(uuid);
        else currentPlayers.add(0, uuid);
    } else {
        if (uuid.charAt(12) == '4') currentPlayers.add(0, uuid);
        else currentPlayers.add(uuid);
    }
    overlayNeedsLayout = true;
    overlayNeedsSort = true;
}}
