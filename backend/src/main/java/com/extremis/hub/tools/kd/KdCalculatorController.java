package com.extremis.hub.tools.kd;

import com.extremis.hub.domain.ToolType;
import com.extremis.hub.premium.PremiumAccessService;
import com.extremis.hub.results.SharedResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools/kd-calculator")
@RequiredArgsConstructor
public class KdCalculatorController {

    private final KdCalculatorService kdCalculatorService;
    private final SharedResultService sharedResultService;
    private final PremiumAccessService premiumAccessService;

    @PostMapping
    public KdCalculatorResponse calculate(@Valid @RequestBody KdCalculatorRequest request, Authentication authentication) {
        premiumAccessService.requireAccessIfPremium(ToolType.KD_CALCULATOR, authentication);
        KdCalculatorResponse response = kdCalculatorService.calculate(request);

        if (request.isSave()) {
            String shareToken =
                sharedResultService.save(ToolType.KD_CALCULATOR, request, response);
            return response.toBuilder().shareToken(shareToken).build();
        }

        return response;
    }
}
