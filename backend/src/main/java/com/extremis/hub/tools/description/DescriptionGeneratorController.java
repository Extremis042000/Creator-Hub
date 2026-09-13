package com.extremis.hub.tools.description;

import com.extremis.hub.domain.ToolType;
import com.extremis.hub.results.SharedResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/gaming-description-generator")
@RequiredArgsConstructor
public class DescriptionGeneratorController {

    private final DescriptionGeneratorService descriptionGeneratorService;
    private final SharedResultService sharedResultService;

    @PostMapping
    public DescriptionGeneratorResponse generate(@Valid @RequestBody DescriptionGeneratorRequest request) {
        DescriptionGeneratorResponse response = descriptionGeneratorService.generate(request);

        if (request.isSave()) {
            String shareToken = sharedResultService.save(ToolType.DESCRIPTION_GENERATOR, request, response);
            return response.toBuilder().shareToken(shareToken).build();
        }

        return response;
    }
}
