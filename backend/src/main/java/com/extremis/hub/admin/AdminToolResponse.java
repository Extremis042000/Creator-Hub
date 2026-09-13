package com.extremis.hub.admin;

import com.extremis.hub.domain.ToolType;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminToolResponse {
    UUID id;
    ToolType toolType;
    String slug;
    String name;
    String category;
    boolean premiumOnly;
}
