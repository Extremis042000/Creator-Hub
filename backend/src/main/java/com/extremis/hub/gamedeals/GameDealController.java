package com.extremis.hub.gamedeals;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game-deals")
@RequiredArgsConstructor
public class GameDealController {

    private final GameDealService gameDealService;

    @GetMapping
    public List<GameDealResponse> list() {
        return gameDealService.getCurrentDeals();
    }
}
