package com.extremis.hub.tools.sensitivity;

import com.extremis.hub.domain.ToolType;
import com.extremis.hub.results.SharedResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/valorant-sensitivity-converter")
@RequiredArgsConstructor
public class SensitivityConverterController {

    private final SensitivityConverterService sensitivityConverterService;
    private final SharedResultService sharedResultService;

    @PostMapping
    public SensitivityConversionResponse convert(@Valid @RequestBody SensitivityConversionRequest request) {
        SensitivityConversionResponse response = sensitivityConverterService.convert(request);

        if (request.isSave()) {
            String shareToken = sharedResultService.save(
                ToolType.VALORANT_SENSITIVITY_CONVERTER, request, response);
            return response.toBuilder().shareToken(shareToken).build();
        }

        return response;
    }
}
