package com.extremis.hub.tools.bgmi;

import com.extremis.hub.domain.ToolType;
import com.extremis.hub.results.SharedResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/bgmi-sensitivity-helper")
@RequiredArgsConstructor
public class BgmiSensitivityController {

    private final BgmiSensitivityService bgmiSensitivityService;
    private final SharedResultService sharedResultService;

    @PostMapping
    public BgmiSensitivityResponse recommend(@Valid @RequestBody BgmiSensitivityRequest request) {
        BgmiSensitivityResponse response = bgmiSensitivityService.recommend(request);

        if (request.isSave()) {
            String shareToken =
                sharedResultService.save(ToolType.BGMI_SENSITIVITY_HELPER, request, response);
            return response.toBuilder().shareToken(shareToken).build();
        }

        return response;
    }
}
