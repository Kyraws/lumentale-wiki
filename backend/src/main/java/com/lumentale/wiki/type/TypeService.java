package com.lumentale.wiki.type;

import com.lumentale.wiki.common.LocalizationResolver;
import com.lumentale.wiki.type.dto.Defender;
import com.lumentale.wiki.type.dto.Offense;
import com.lumentale.wiki.type.dto.Quirk;
import com.lumentale.wiki.type.dto.TypeCoverage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Type analytics + quirks + meta. The effectiveness computations run through the
 * pure {@link TypeAnalytics} (the repository supplies the forms and the
 * attacking-type list, resolved from the reference index); quirk names/
 * descriptions resolve via the shared {@link LocalizationResolver}.
 *
 * Read-only over already-seeded tables — no seeder participates in this slice.
 */
@Service
public class TypeService {

    private final TypeRepository repo;
    private final LocalizationResolver loc;

    public TypeService(TypeRepository repo, LocalizationResolver loc) {
        this.repo = repo;
        this.loc = loc;
    }

    public List<TypeCoverage> coverage() {
        return TypeAnalytics.coverage(repo.eleTypes(), repo.loadForms());
    }

    public List<Defender> defenders(int limit) {
        return TypeAnalytics.defenders(repo.eleTypes(), repo.loadForms(), limit);
    }

    public List<Offense> offense() {
        return TypeAnalytics.offense(repo.eleTypes(), repo.loadForms());
    }

    public Map<String, Integer> meta() {
        return repo.metaCounts();
    }

    /**
     * Quirks with owners + localized name/description. The {@code quirk.name_key}/
     * {@code desc_key} columns are unpopulated, so names resolve through the
     * {@code QUIRK_NAMES}/{@code QUIRK_DESC} loc tables keyed by the UPPERCASE quirk
     * class (e.g. {@code ARCANEPOWER} → "Arcane Power"; desc key
     * {@code <CLASS>_DESCRIPTION}). Falls back to the class when unlocalized. null
     * name/description are omitted by the non_null policy.
     */
    public List<Quirk> quirks(String langParam) {
        String lang = loc.normalize(langParam);
        Map<String, String> names = loc.table("QUIRK_NAMES", lang);  // UPPER(class) → text
        Map<String, String> descs = loc.table("QUIRK_DESC", lang);   // UPPER(class)_DESCRIPTION → text

        List<Quirk> out = new ArrayList<>();
        for (var e : repo.quirkOwners().entrySet()) {
            String cls = e.getKey();
            String upper = cls.toUpperCase();
            String name = names.get(upper);
            String desc = descs.get(upper + "_DESCRIPTION");
            if (name == null) name = cls;                 // fall back to class
            out.add(new Quirk(cls, name, desc, e.getValue()));
        }
        return out;
    }
}
