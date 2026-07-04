package com.lumentale.wiki.mechanics;

import com.lumentale.wiki.mechanics.dto.Constant;
import com.lumentale.wiki.mechanics.dto.FormulaDetail;
import com.lumentale.wiki.mechanics.dto.MechanicsOverview;
import com.lumentale.wiki.mechanics.dto.XpCurveDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mechanics endpoints — the third slice, surfacing the fully-new M9 layer
 * (formulas/constants/XP curves/difficulty) as real data, replacing v2's
 * hardcoded {@code Guides}.
 *
 *   GET /api/mechanics                  — overview (formulas + curves + difficulty)
 *   GET /api/mechanics/constants        — all named tuning constants
 *   GET /api/mechanics/formulas/{key}   — one formula + its constants + raw
 *   GET /api/mechanics/xp-curves/{type} — one curve + its level→exp table
 */
@RestController
@RequestMapping("/api/mechanics")
public class MechanicsController {

    private final MechanicsService mechanics;

    public MechanicsController(MechanicsService mechanics) { this.mechanics = mechanics; }

    @GetMapping
    public MechanicsOverview overview() {
        return mechanics.overview();
    }

    @GetMapping("/constants")
    public List<Constant> constants() {
        return mechanics.constants();
    }

    @GetMapping("/formulas/{key}")
    public FormulaDetail formula(@PathVariable String key) {
        return mechanics.formula(key);
    }

    @GetMapping("/xp-curves/{curveType}")
    public XpCurveDetail xpCurve(@PathVariable int curveType) {
        return mechanics.xpCurve(curveType);
    }
}
