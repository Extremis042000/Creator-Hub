package com.extremis.hub.tools.title;

import com.extremis.hub.domain.ToolType;
import com.extremis.hub.results.SharedResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/gaming-title-generator")
@RequiredArgsConstructor
public class TitleGeneratorController {

    private final TitleGeneratorService titleGeneratorService;
    private final SharedResultService sharedResultService;

    @PostMapping
    public TitleGeneratorResponse generate(@Valid @RequestBody TitleGeneratorRequest request) {
        TitleGeneratorResponse response = titleGeneratorService.generate(request);

        if (request.isSave()) {
            String shareToken = sharedResultService.save(ToolType.TITLE_GENERATOR, request, response);
            return response.toBuilder().shareToken(shareToken).build();
        }

        return response;
    }
}
