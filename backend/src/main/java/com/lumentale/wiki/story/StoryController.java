package com.lumentale.wiki.story;

import com.lumentale.wiki.story.dto.SceneDetail;
import com.lumentale.wiki.story.dto.StoryCity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Story endpoints — the quest/story slice's scene side. Pure routing; the service
 * groups cities and reads scenes.
 *
 *   GET /api/story/cities?path=both|north|south — cities grouped by track, each
 *                                                 with its dialogue scenes
 *   GET /api/story/scene?id=&lt;sceneId&gt;          — one scene as an enriched flow
 */
@RestController
@RequestMapping("/api/story")
public class StoryController {

    private final StoryService story;

    public StoryController(StoryService story) { this.story = story; }

    @GetMapping("/cities")
    public List<StoryCity> cities(@RequestParam(defaultValue = "both") String path) {
        return story.cities(path);
    }

    @GetMapping("/scene")
    public SceneDetail scene(@RequestParam String id, @RequestParam(defaultValue = "en") String lang) {
        return story.scene(id, lang);
    }

    /**
     * GET /api/story/scene/quests?id=&lt;sceneId&gt; — quests this scene starts /
     * completes / relates to (matched by shared flag). Powers the scene-view quest badge.
     */
    @GetMapping("/scene/quests")
    public List<com.lumentale.wiki.story.dto.SceneQuestLink> sceneQuests(
            @RequestParam String id, @RequestParam(defaultValue = "en") String lang) {
        return story.sceneQuests(id, lang);
    }
}
