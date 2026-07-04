package com.lumentale.wiki.mechanics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumentale.wiki.mechanics.dto.*;
import com.lumentale.wiki.mechanics.dto.XpCurveDetail.LevelExp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the M9 mechanics layer — formulas, named constants, XP curves
 * (+ precomputed level table), difficulty scalars. All keys here are natural
 * text/int (no uuid). The level→exp milestones are sliced by the pure
 * {@link XpCurves} helper.
 */
@Repository
public class MechanicsRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MechanicsRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<FormulaSummary> formulas() {
        return jdbc.query(
            "SELECT key, name, signature, confidence FROM formula ORDER BY name",
            (rs, i) -> new FormulaSummary(rs.getString("key"), rs.getString("name"),
                rs.getString("signature"), rs.getString("confidence")));
    }

    public Optional<FormulaDetail> formula(String key) {
        List<FormulaDetail> found = jdbc.query(
            "SELECT key, name, signature, expression, description, confidence, source_file, raw::text AS raw " +
            "FROM formula WHERE key = ?",
            (rs, i) -> new FormulaDetail(
                rs.getString("key"), rs.getString("name"), rs.getString("signature"),
                rs.getString("expression"), rs.getString("description"), rs.getString("confidence"),
                rs.getString("source_file"), constantsFor(key), parse(rs.getString("raw"))),
            key);
        return found.stream().findFirst();
    }

    public List<Constant> constants() {
        return jdbc.query(
            "SELECT name, value, kind, formula_key, description FROM game_constant ORDER BY name",
            (rs, i) -> new Constant(rs.getString("name"), (Double) rs.getObject("value"),
                rs.getString("kind"), rs.getString("formula_key"), rs.getString("description")));
    }

    public int constantCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM game_constant", Integer.class);
        return n == null ? 0 : n;
    }

    private List<Constant> constantsFor(String formulaKey) {
        return jdbc.query(
            "SELECT name, value, kind, formula_key, description FROM game_constant " +
            "WHERE formula_key = ? ORDER BY name",
            (rs, i) -> new Constant(rs.getString("name"), (Double) rs.getObject("value"),
                rs.getString("kind"), rs.getString("formula_key"), rs.getString("description")),
            formulaKey);
    }

    public List<DifficultyScalar> difficulty() {
        return jdbc.query(
            "SELECT difficulty, direction, multiplier FROM difficulty_scalar ORDER BY difficulty, direction",
            (rs, i) -> new DifficultyScalar(rs.getString("difficulty"),
                rs.getString("direction"), rs.getDouble("multiplier")));
    }

    /** XP-curve summaries with milestone exp (50/100) pulled from the level table. */
    public List<XpCurveSummary> xpCurves() {
        return jdbc.query(
            "SELECT curve_type, name, kind FROM xp_curve ORDER BY curve_type",
            (rs, i) -> {
                int ct = rs.getInt("curve_type");
                List<LevelExp> lv = levels(ct);
                return new XpCurveSummary(ct, rs.getString("name"), rs.getString("kind"),
                    XpCurves.expAt(lv, 50), XpCurves.expAt(lv, 100));
            });
    }

    public Optional<XpCurveDetail> xpCurve(int curveType) {
        List<XpCurveDetail> found = jdbc.query(
            "SELECT curve_type, name, kind, expression, source_file, keyframes::text AS keyframes " +
            "FROM xp_curve WHERE curve_type = ?",
            (rs, i) -> new XpCurveDetail(
                rs.getInt("curve_type"), rs.getString("name"), rs.getString("kind"),
                rs.getString("expression"), rs.getString("source_file"),
                parse(rs.getString("keyframes")), levels(curveType)),
            curveType);
        return found.stream().findFirst();
    }

    private List<LevelExp> levels(int curveType) {
        return jdbc.query(
            "SELECT level, exp FROM xp_level_exp WHERE curve_type = ? ORDER BY level",
            (rs, i) -> new LevelExp(rs.getInt("level"), rs.getLong("exp")),
            curveType);
    }

    private JsonNode parse(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Corrupt mechanics jsonb", e); }
    }
}
