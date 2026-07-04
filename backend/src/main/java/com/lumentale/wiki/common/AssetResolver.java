package com.lumentale.wiki.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Hybrid asset URL resolution.
 *
 * v2 served only content-addressed files from {@code /data/<kind>/<guid>/<f>.png}.
 * The redesign (Module 11) additionally models the full Unity asset manifest with
 * a two-hop Addressables resolution: {@code 32-hex guid → asset_guid(bundle,
 * path_id) → asset.file}. v3 uses BOTH, in this order:
 *
 *   1. <b>Filesystem leg</b> — if the v2-style file exists on disk
 *      ({@code data/assets/<kind>/<guid>/<file>}), return its {@code /data} URL.
 *      This stays the fast path for the art that's already exported.
 *   2. <b>Manifest leg</b> — otherwise resolve the entity's Addressables GUID via
 *      {@code entity_asset → asset_guid → asset} and return the manifest file's
 *      {@code /data} URL. This reaches art the v2 export never laid down on disk.
 *   3. Neither → {@code null} (Jackson omits the field; see the null-omit rule).
 *
 * Both legs ultimately yield a relative {@code /data/...} string the frontend
 * uses as an {@code <img src>} — identical contract to v2, wider coverage.
 */
@Component
public class AssetResolver {

    private static final String PREFIX = "/data/";

    /** role → the v2 on-disk filename under {@code <kind>/<guid>/}. */
    private static final Map<String,String> ROLE_FILE = Map.ofEntries(
        Map.entry("menu_art",      "menu.png"),
        Map.entry("front_sprite",  "front.png"),
        Map.entry("lost_menu_art", "lost.png"),
        Map.entry("icon",          "icon.png"),
        Map.entry("card_art",      "art.png"),
        Map.entry("model",         "model.png"),
        Map.entry("idle_sprite",   "idle.png"),
        Map.entry("tile",          "tile.png"),
        Map.entry("logo",          "logo.png"),
        Map.entry("card_mask",     "mask.png"),
        Map.entry("card_holo",     "holo.png"));

    /** entity_type → the directory the v2 export used under data/assets/. */
    private static final Map<String,String> KIND_DIR = Map.of(
        "form", "forms", "item", "items", "card", "cards",
        "furniture", "furniture", "trainer", "trainers", "map", "maps",
        "boss", "bosses", "achievement", "achievements",
        "squadron", "squadrons", "tutorial", "tutorials");

    private final JdbcTemplate jdbc;
    private final Path dataRoot;

    // Field-level memoization. @Cacheable is bypassed on self-invocation
    // (art→manifestUrl→fileForAddressable all call `this`), which made list
    // endpoints re-query per row. These hold resolved URLs ("" = sentinel for null,
    // since ConcurrentHashMap forbids null values).
    private static final String NONE = "";
    private final java.util.concurrent.ConcurrentHashMap<String,String> manifestCache =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String,String> byGuidCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    public AssetResolver(JdbcTemplate jdbc, @Value("${lumentale.data-dir:data/assets}") String dataDir) {
        this.jdbc = jdbc;
        this.dataRoot = Paths.get(dataDir).toAbsolutePath().normalize();
    }

    /** Turn a stored relative path into a {@code /data} URL (null-safe, no double prefix). */
    public String of(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        return storedPath.startsWith(PREFIX) ? storedPath : PREFIX + storedPath;
    }

    /**
     * Resolve one art role for an entity, filesystem-first then manifest. Returns
     * a {@code /data} URL or {@code null} if neither leg finds the asset.
     */
    public String art(String entityType, UUID guid, String role) {
        String onDisk = diskUrl(entityType, guid, role);
        if (onDisk != null) return onDisk;
        return manifestUrl(entityType, guid, role);
    }

    /** Filesystem leg: {@code /data/<kind>/<guid>/<file>} if the file exists, else null. */
    public String diskUrl(String entityType, UUID guid, String role) {
        String dir = KIND_DIR.get(entityType);
        String file = ROLE_FILE.get(role);
        if (dir == null || file == null || guid == null) return null;
        Path p = dataRoot.resolve(dir).resolve(guid.toString()).resolve(file);
        return Files.isRegularFile(p) ? PREFIX + dir + "/" + guid + "/" + file : null;
    }

    /**
     * Manifest leg: the entity's Addressables GUID for {@code role} (from
     * {@code entity_asset}) resolved two hops to a manifest file path. Cached —
     * the asset tables are static post-seed.
     */
    public String manifestUrl(String entityType, UUID guid, String role) {
        if (guid == null) return null;
        String memo = manifestCache.computeIfAbsent(entityType + ':' + guid + ':' + role, k -> {
            String addrGuid = jdbc.query(
                "SELECT asset_guid FROM entity_asset " +
                "WHERE entity_type = ? AND entity_guid = ? AND role = ? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                entityType, guid, role);
            if (addrGuid == null) return NONE;
            String url = of(fileForAddressable(addrGuid));
            return url == null ? NONE : url;
        });
        return NONE.equals(memo) ? null : memo;
    }

    /**
     * The two-hop core: {@code 32-hex Addressables guid → (bundle, path_id) →
     * asset.file}. Public so any page that holds a typed art guid column directly
     * (item.icon_guid, card.art_guid, …) can resolve it without going through
     * entity_asset. Cached.
     */
    public String fileForAddressable(String addressableGuid) {
        if (addressableGuid == null || addressableGuid.isBlank()) return null;
        String memo = byGuidCache.computeIfAbsent(addressableGuid, g -> {
            String file = jdbc.query(
                "SELECT a.file FROM asset_guid ag " +
                "JOIN asset a ON a.bundle = ag.bundle AND a.path_id = ag.path_id " +
                "WHERE ag.guid = ? AND a.file IS NOT NULL LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                g);
            return file == null ? NONE : file;
        });
        return NONE.equals(memo) ? null : memo;
    }
}
