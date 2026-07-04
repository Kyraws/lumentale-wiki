package com.lumentale.wiki.creature;

import com.lumentale.wiki.creature.dto.StatGrade;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-stat S–F grades for a form, ranked against the whole population.
 *
 * The six stat distributions and the global cap are static post-seed, so they're
 * computed ONCE at startup; grading a form is then pure CPU. Unchanged from v2
 * except for {@code uuid} parameter binding (the form PK is now a uuid).
 */
@Service
public class StatGradeService {

    private static final String[] COLS   = { "stat_hp", "stat_atk", "stat_def", "stat_spa", "stat_spd", "stat_spe" };
    private static final String[] LABELS = { "HP", "ATK", "DEF", "SpA", "SpD", "Spe" };

    private final JdbcTemplate jdbc;
    private int[][] sorted;
    private int cap;

    public StatGradeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void build() {
        sorted = new int[COLS.length][];
        for (int i = 0; i < COLS.length; i++) {
            List<Integer> all = jdbc.queryForList(
                "SELECT " + COLS[i] + " FROM form WHERE " + COLS[i] + " IS NOT NULL", Integer.class);
            sorted[i] = all.stream().mapToInt(Integer::intValue).sorted().toArray();
        }
        Integer capObj = jdbc.queryForObject(
            "SELECT GREATEST(MAX(stat_hp),MAX(stat_atk),MAX(stat_def)," +
            "MAX(stat_spa),MAX(stat_spd),MAX(stat_spe)) FROM form", Integer.class);
        cap = (capObj == null || capObj == 0) ? 100 : capObj;
    }

    /** Grades for one form's six base stats, or an empty list if the form is unknown. */
    public List<StatGrade> gradesFor(UUID formGuid) {
        List<StatGrade> out = new ArrayList<>(COLS.length);
        Map<String, Object> cur;
        try {
            cur = jdbc.queryForMap(
                "SELECT stat_hp,stat_atk,stat_def,stat_spa,stat_spd,stat_spe FROM form WHERE guid=?", formGuid);
        } catch (Exception e) {
            return out;
        }
        for (int i = 0; i < COLS.length; i++) {
            Object raw = cur.get(COLS[i]);
            if (raw == null) { out.add(new StatGrade(LABELS[i], "–", 0, null)); continue; }
            int v = ((Number) raw).intValue();
            int n = sorted[i].length;
            long less = countLess(sorted[i], v);
            double p = n > 1 ? (double) less / (n - 1) : 1.0;
            out.add(new StatGrade(LABELS[i], grade(p), Math.round(v * 100f / cap), (int) Math.round(p * 100)));
        }
        return out;
    }

    private static long countLess(int[] s, int v) {
        int lo = 0, hi = s.length;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (s[mid] < v) lo = mid + 1; else hi = mid; }
        return lo;
    }

    /** Percentile → tier letter. S≈top 10%, F≈bottom 10%. */
    private static String grade(double p) {
        if (p >= 0.90) return "S";
        if (p >= 0.72) return "A";
        if (p >= 0.50) return "B";
        if (p >= 0.28) return "C";
        if (p >= 0.10) return "D";
        return "F";
    }
}
