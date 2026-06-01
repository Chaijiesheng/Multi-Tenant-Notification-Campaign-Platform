package com.example.notifications.api;

import com.example.notifications.domain.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    private String name;

    @NotNull(message = "Channel is required")
    private Channel channel;

    @NotBlank(message = "Message body is required")
    private String messageBody;
}
