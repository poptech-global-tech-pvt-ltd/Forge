package com.popclub.cardapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class YblAddressRequestDto {

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

    public YblAddressRequestDto(String addressType, String addressLine1, String addressLine2,
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

    public String getAddressType()  { return addressType; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getAddressLine3() { return addressLine3; }
    public String getLandmark()     { return landmark; }
    public String getCity()         { return city; }
    public String getState()        { return state; }
    public String getCountry()      { return country; }
    public String getPinCode()      { return pinCode; }
}
