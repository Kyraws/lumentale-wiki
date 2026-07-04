package com.lumentale.wiki.camp;

import com.lumentale.wiki.camp.CampRepository.Base;
import com.lumentale.wiki.camp.dto.CampDetail;
import com.lumentale.wiki.camp.dto.CampSummary;
import com.lumentale.wiki.common.Guids;
import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.common.RawRecordService;
import com.lumentale.wiki.common.RawRecordService.RawTable;
import com.lumentale.wiki.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assembles camp responses: the curated base + its raw record + the two
 * cross-link sections (target forms, tasks), composed into one
 * {@link CampDetail}. Guid validation → 400; missing camp → 404.
 */
@Service
public class CampService {

    private final CampRepository repo;
    private final RawRecordService raw;
    private final LocalizationResolver loc;

    public CampService(CampRepository repo, RawRecordService raw, LocalizationResolver loc) {
        this.repo = repo;
        this.raw = raw;
        this.loc = loc;
    }

    public List<CampSummary> list() {
        return repo.summaries();
    }

    public CampDetail detail(String guidStr, String lang) {
        UUID guid = Guids.require(guidStr);
        Base b = repo.base(guid).orElseThrow(() -> new NotFoundException("camp", guidStr));
        CampNaming.Naming nm = CampNaming.of(b.name());
        return new CampDetail(
            b.guid(), b.name(), nm.displayName(), nm.region(), nm.area(),
            b.effectClass(), CampNaming.effectLabel(b.effectClass()),
            b.effectDescription(),
            effectText(lang, b.effectDescription(), b.effectIncrement()),
            b.effectDuration(),
            b.effectIncrement(), b.influence(), b.lumenAmount(),
            raw.find(RawTable.CAMP, guid).orElse(null),
            repo.targets(guid),
            repo.tasks(guid, loc.normalize(lang)));
    }

    /**
     * Resolve the camp effect's loc key (e.g. {@code EFFECT_EXP_BOOST}) to localized
     * text and substitute the {@code {0}} placeholder with the increment rendered as
     * a percentage (0.1 → "10"). Returns null when there is no key/text.
     */
    private String effectText(String lang, String effectKey, Double increment) {
        if (effectKey == null || effectKey.isBlank()) return null;
        String t = loc.display(loc.normalize(lang), effectKey, null);
        if (t == null) return null;
        if (t.contains("{0}") && increment != null) {
            t = t.replace("{0}", formatPercent(increment * 100.0));
        }
        return t;
    }

    /**
     * Render a percentage cleanly. {@code effect_increment} is a {@code real} (float4),
     * so widening it to {@code double} introduces noise (0.1f → 0.10000000149…, ×100 →
     * "10.000000149011612%"). Round to 3 decimals — enough for any genuine fractional
     * percentage in the data while dropping the float artifact — then trim trailing
     * zeros ("10.000" → "10", "12.500" → "12.5").
     */
    static String formatPercent(double pct) {
        return new java.math.BigDecimal(pct)
            .setScale(3, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }
}
