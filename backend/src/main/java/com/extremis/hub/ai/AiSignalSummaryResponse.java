package com.extremis.hub.ai;

import java.util.Map;

/** Phase 38: all-time, keyed by ToolType name (e.g. "TITLE_GENERATOR") -- see AiUsageService.getSignalSummary. */
public record AiSignalSummaryResponse(Map<String, ToolSignalBreakdown> byTool) {
}
