package com.extremis.hub.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** toolType/slug are structural (routing, SEO) and deliberately not admin-editable here. */
@Getter
@Setter
public class AdminToolUpdateRequest {

    @NotBlank(message = "must not be blank")
    @Size(max = 255, message = "must be at most 255 characters")
    private String name;

    @Size(max = 64, message = "must be at most 64 characters")
    private String category;

    private boolean premiumOnly;
}
