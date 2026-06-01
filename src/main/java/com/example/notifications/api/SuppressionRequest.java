package com.example.notifications.api;

import com.example.notifications.domain.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SuppressionRequest {

    @NotBlank(message = "recipientExternalId is required")
    private String recipientExternalId;

    @NotNull(message = "channel is required")
    private Channel channel;
}
