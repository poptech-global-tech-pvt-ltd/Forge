package com.popclub.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressRequestDto {

    @JsonProperty("address_type")        private String  addressType;
    @JsonProperty("address_line_1")      private String  addressLine1;
    @JsonProperty("address_line_2")      private String  addressLine2;
    @JsonProperty("address_line_3")      private String  addressLine3;
    @JsonProperty("landmark")            private String  landmark;
    @JsonProperty("city")                private String  city;
    @JsonProperty("state")               private String  state;
    @JsonProperty("country")             private String  country;
    @JsonProperty("pin_code")            private String  pinCode;
    @JsonProperty("is_delivery_address") private Boolean isDeliveryAddress;
}
