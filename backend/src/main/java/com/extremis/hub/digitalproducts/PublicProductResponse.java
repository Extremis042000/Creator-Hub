package com.extremis.hub.digitalproducts;

import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/** fileRef is deliberately never exposed -- downloads only ever happen through a signed token. */
@Value
@Builder
public class PublicProductResponse {
    UUID id;
    String categorySlug;
    String name;
    long priceCents;
    String currency;
}
