package com.lumentale.wiki.trainer;

import com.lumentale.wiki.trainer.dto.TrainerDetail;
import com.lumentale.wiki.trainer.dto.TrainerSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Trainer endpoints (ported from v2 onto the redesigned schema).
 *
 *   GET /api/trainers        — trainer list (dev '%UNUSED%' placeholders hidden)
 *   GET /api/trainers/{guid} — trainer detail (+ party / maps / scenes / squadrons)
 */
@RestController
@RequestMapping("/api")
public class TrainerController {

    private final TrainerService trainers;

    public TrainerController(TrainerService trainers) { this.trainers = trainers; }

    @GetMapping("/trainers")
    public List<TrainerSummary> trainers(@RequestParam(required = false) String lang) {
        return trainers.list(lang);
    }

    @GetMapping("/trainers/{guid}")
    public TrainerDetail trainer(@PathVariable String guid, @RequestParam(required = false) String lang) {
        return trainers.detail(guid, lang);
    }
}
