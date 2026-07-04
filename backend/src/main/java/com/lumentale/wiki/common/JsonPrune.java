package com.lumentale.wiki.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Set;

/**
 * Strips Unity engine internals from an extracted {@code raw} record before it
 * reaches the API: the nested {@code raw} blob, any {@code m_*} key, and known
 * engine plumbing ({@code PathID}, {@code m_AssetGUID}, the {@code HoloTexture}
 * prefix, …). Curated/normalized fields the UI reads are kept untouched.
 *
 * Carried over verbatim from v2 — it was already correct.
 */
public final class JsonPrune {

    private static final Set<String> DROP_EXACT = Set.of(
        "raw", "PathID", "FileID", "fileID", "pathID",
        "CanBeKickstarter", "m_GameObject", "m_AssetGUID");

    private static final String[] DROP_PREFIX = { "m_", "HoloTexture" };

    private JsonPrune() {}

    /** Prune in place and return the same node (for chaining). */
    public static JsonNode prune(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> names = obj.fieldNames();
            while (names.hasNext()) {
                if (shouldDrop(names.next())) names.remove();
            }
            obj.fields().forEachRemaining(e -> prune(e.getValue()));
        } else if (node.isArray()) {
            for (JsonNode child : (ArrayNode) node) prune(child);
        }
        return node;
    }

    private static boolean shouldDrop(String key) {
        if (DROP_EXACT.contains(key)) return true;
        for (String p : DROP_PREFIX) if (key.startsWith(p)) return true;
        return false;
    }
}
