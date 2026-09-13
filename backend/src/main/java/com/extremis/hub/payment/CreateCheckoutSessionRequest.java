package com.extremis.hub.payment;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCheckoutSessionRequest {

    @NotNull(message = "must not be null")
    private UUID productId;
}
