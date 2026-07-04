package com.lumentale.wiki.mechanics;

import com.lumentale.wiki.error.NotFoundException;
import com.lumentale.wiki.mechanics.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Assembles the Mechanics responses. The overview ties the four M9 layers into a
 * single page read; detail endpoints map "no such formula/curve" to a 404.
 */
@Service
public class MechanicsService {

    private final MechanicsRepository repo;

    public MechanicsService(MechanicsRepository repo) { this.repo = repo; }

    public MechanicsOverview overview() {
        return new MechanicsOverview(repo.formulas(), repo.xpCurves(), repo.difficulty(), repo.constantCount());
    }

    public List<Constant> constants() { return repo.constants(); }

    public FormulaDetail formula(String key) {
        return repo.formula(key).orElseThrow(() -> new NotFoundException("formula", key));
    }

    public XpCurveDetail xpCurve(int curveType) {
        return repo.xpCurve(curveType).orElseThrow(() -> new NotFoundException("xp curve", String.valueOf(curveType)));
    }
}
