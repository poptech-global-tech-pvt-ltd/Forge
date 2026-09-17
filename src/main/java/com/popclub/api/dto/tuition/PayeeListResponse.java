package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PayeeListResponse {
    private String status;
    private Data data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<Payee> payees;
        private int total;
        private int page;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payee {
        @JsonProperty("payee_id")    private String payeeId;
        @JsonProperty("mobile_number") private String mobileNumber;
        private String name;
        private String status;
    }
}
