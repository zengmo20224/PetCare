package com.petcare.store.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.petcare.common.serialization.SnowflakeIdSerializer;

/**
 * Public-facing store info for the mini-app.
 * Used by the order confirmation page to let the customer pick a pickup store.
 */
public record StoreResponse(
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long id,
        String name,
        String address,
        String phone,
        String businessHours,
        String description,
        String status
) {
}
