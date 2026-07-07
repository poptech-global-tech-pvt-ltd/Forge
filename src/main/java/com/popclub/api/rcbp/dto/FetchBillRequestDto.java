package com.popclub.api.rcbp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for POST /v2/bills/fetch.
 *
 * Three usages across collections:
 *
 * 1. Credit card (new collection):
 *    { "billerId": "{{biller_id}}", "category": "creditcard",
 *      "customerParams": [{"name": "Registered Mobile Number", "value": "9936122907"},
 *                         {"name": "Last 4 digits of Primary Credit Card Number", "value": "4562"}] }
 *
 * 2. Mobile postpaid (new collection):
 *    { "category": "mobilepostpaid", "billerId": "{{biller_id}}",
 *      "customerParams": [{extracted from inputFields step}] }
 *
 * 3. Mobile prepaid (new collection):
 *    { "category": "mobileprepaid", "planId": {{plan_id}} }
 *
 * Fields absent from a request should be left null;
 * @JsonInclude(NON_NULL) ensures they are omitted from serialisation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchBillRequestDto {

    private String billerId;
    private String category;
    private Long   planId;
    private java.util.List<CustomerParam> customerParams;

    // ── Constructors ──────────────────────────────────────────────────────────

    public FetchBillRequestDto() {}

    /**
     * Credit card bill fetch.
     * customerParams are hardcoded in the collection (mobile number + last 4 digits).
     */
    public static FetchBillRequestDto forCreditCard(
            String billerId,
            java.util.List<CustomerParam> customerParams) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.billerId       = billerId;
        dto.category       = "creditcard";
        dto.customerParams = customerParams;
        return dto;
    }

    /**
     * Mobile postpaid bill fetch.
     * customerParams are built from inputFields extracted in the previous step.
     */
    public static FetchBillRequestDto forMobilePostpaid(
            String billerId,
            java.util.List<CustomerParam> customerParams) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.billerId       = billerId;
        dto.category       = "mobilepostpaid";
        dto.customerParams = customerParams;
        return dto;
    }

    /**
     * Mobile prepaid bill fetch.
     * Uses planId extracted from the fetch plans response.
     */
    public static FetchBillRequestDto forMobilePrepaid(long planId) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.category = "mobileprepaid";
        dto.planId   = planId;
        return dto;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getBillerId()                            { return billerId; }
    public void   setBillerId(String billerId)             { this.billerId = billerId; }

    public String getCategory()                            { return category; }
    public void   setCategory(String category)             { this.category = category; }

    public Long getPlanId()                                { return planId; }
    public void setPlanId(Long planId)                     { this.planId = planId; }

    public java.util.List<CustomerParam> getCustomerParams() { return customerParams; }
    public void setCustomerParams(java.util.List<CustomerParam> cp) { this.customerParams = cp; }

    // ── Inner type ────────────────────────────────────────────────────────────

    public static class CustomerParam {
        private String name;
        private String value;

        public CustomerParam(String name, String value) {
            this.name  = name;
            this.value = value;
        }

        public String getName()  { return name; }
        public String getValue() { return value; }
    }
}
