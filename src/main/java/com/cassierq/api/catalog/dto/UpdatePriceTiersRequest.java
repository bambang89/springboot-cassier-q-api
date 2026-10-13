package com.cassierq.api.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Replaces all wholesale price steps of a product in the caller's store (empty = no wholesale price). */
public record UpdatePriceTiersRequest(@NotNull @Valid List<PriceTierDto> tiers) {
}
