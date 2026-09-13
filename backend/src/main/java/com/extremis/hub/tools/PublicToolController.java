package com.extremis.hub.tools;

import com.extremis.hub.domain.Tool;
import com.extremis.hub.repository.ToolRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slug + premiumOnly only -- the public /tools page keeps its own
 * hardcoded name/description/category copy (lib/tools.ts) and merges
 * this in by slug, so an admin's "Premium only" edit is reflected on
 * the site without exposing internal tool ids.
 */
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
public class PublicToolController {

    private final ToolRepository toolRepository;

    @GetMapping
    public List<PublicToolResponse> listTools() {
        return toolRepository.findAll().stream()
                .map(t -> new PublicToolResponse(t.getSlug(), t.isPremiumOnly()))
                .toList();
    }

    @Value
    public static class PublicToolResponse {
        String slug;
        boolean premiumOnly;
    }
}
