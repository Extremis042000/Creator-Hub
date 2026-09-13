package com.extremis.hub.results;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-level resource, not tool-scoped — see docs/06-prd.md §4.2. */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultsController {

    private final SharedResultService sharedResultService;

    @GetMapping("/{shareToken}")
    public SharedResultResponse getResult(@PathVariable String shareToken) {
        return sharedResultService.getByShareToken(shareToken);
    }
}
