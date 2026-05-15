package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressRequestDto {

    @JsonProperty("address_type")
    private String addressType;

    @JsonProperty("address_line_1")
    private String addressLine1;

    @JsonProperty("address_line_2")
    private String addressLine2;

    @JsonProperty("address_line_3")
    private String addressLine3;

    @JsonProperty("landmark")
    private String landmark;

    @JsonProperty("city")
    private String city;

    @JsonProperty("state")
    private String state;

    @JsonProperty("country")
    private String country;

    @JsonProperty("pin_code")
    private String pinCode;

    @JsonProperty("is_delivery_address")
    private Boolean isDeliveryAddress;

    public AddressRequestDto(String addressType, String addressLine1, String addressLine2,
                          String addressLine3, String landmark, String city,
                          String state, String country, String pinCode) {
        this.addressType = addressType;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressLine3 = addressLine3;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.country = country;
        this.pinCode = pinCode;
    }

    public AddressRequestDto(String addressType, boolean isDeliveryAddress) {
        this.addressType = addressType;
        this.isDeliveryAddress = isDeliveryAddress;
    }
}
