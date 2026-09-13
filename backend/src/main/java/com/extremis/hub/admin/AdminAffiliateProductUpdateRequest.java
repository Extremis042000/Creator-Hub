package com.extremis.hub.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminAffiliateProductUpdateRequest extends AdminAffiliateProductRequest {
    private boolean active;
}
