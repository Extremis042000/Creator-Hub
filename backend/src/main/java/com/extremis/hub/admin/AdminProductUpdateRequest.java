package com.extremis.hub.admin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProductUpdateRequest extends AdminProductRequest {
    private boolean active;
}
