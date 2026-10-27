package com.popclub.api.dto.tuition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data @JsonIgnoreProperties(ignoreUnknown = true)
public class PayeeListResponse {

    @JsonProperty("is_success") private boolean success;
    private String  message;
    private int     page;
    private int     limit;
    @JsonProperty("next_page") private boolean nextPage;
    private Data    data;

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<Payee> payee;
        private List<Payee> favorites;
    }

    @lombok.Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payee {
        private String  mobile;
        private String  name;
        private boolean favorite;
        @JsonProperty("onbaording_status")          private String onboardingStatus;
        @JsonProperty("last_transaction_message")   private String lastTransactionMessage;
        @JsonProperty("expire_in")                  private String expireIn;
    }
}
