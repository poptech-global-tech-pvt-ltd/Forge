package com.popclub.api.dto;

import com.google.gson.annotations.SerializedName;

public class SearchRequestDto {

    @SerializedName("query")
    private final String query;

    /** Optional — filters results to products deliverable to this pincode. */
    @SerializedName("pincode")
    private final String pincode;

    /** Query only — no pincode filter. */
    public SearchRequestDto(String query) {
        this.query   = query;
        this.pincode = null;
    }

    /** Query + pincode delivery filter. Pass null or blank to omit the field. */
    public SearchRequestDto(String query, String pincode) {
        this.query   = query;
        this.pincode = (pincode != null && !pincode.isBlank()) ? pincode : null;
    }

    public String getQuery()   { return query; }
    public String getPincode() { return pincode; }
}
