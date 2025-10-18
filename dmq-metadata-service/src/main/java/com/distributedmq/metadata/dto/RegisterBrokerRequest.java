package com.distributedmq.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Request DTO for registering a broker
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterBrokerRequest {

    @NotNull(message = "Broker ID is required")
    @Min(value = 1, message = "Broker ID must be positive")
    private Integer id;

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be positive")
    private Integer port;

    private String rack;
}