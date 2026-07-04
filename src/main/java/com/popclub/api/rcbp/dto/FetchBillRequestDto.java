package com.popclub.api.rcbp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for POST /v2/bills/fetch.
 *
 * Two usages in the current Postman collection:
 *
 * 1. Credit card bill fetch:
 *    { "billerId": "ICIC00000NATSI", "category": "creditCard",
 *      "amount": 1, "billId": 458001 }
 *
 * 2. Mobile postpaid bill fetch (chained — values from input fields response):
 *    { "billerId": "{{biller_id}}", "category": "mobilePostpaid",
 *      "customerMobile": "{{phone}}",
 *      "customerParams": [{ "name": "{{customer_param_name}}", "value": "{{phone}}" }] }
 *
 * NOTE: The recharge "select plan" POST is NOT present in the current collection.
 * Fields absent from a particular request should be left null;
 * @JsonInclude(NON_NULL) ensures they are omitted from serialisation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchBillRequestDto {

    private String billerId;
    private String category;
    private Integer amount;
    private Long billId;
    private String customerMobile;
    private java.util.List<CustomerParam> customerParams;

    // ── Constructors ──────────────────────────────────────────────────────────

    public FetchBillRequestDto() {}

    /** Credit card bill fetch */
    public static FetchBillRequestDto forCreditCard(String billerId, int amount, long billId) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.billerId  = billerId;
        dto.category  = "creditCard";
        dto.amount    = amount;
        dto.billId    = billId;
        return dto;
    }

    /** Mobile postpaid bill fetch */
    public static FetchBillRequestDto forMobilePostpaid(
            String billerId,
            String customerMobile,
            java.util.List<CustomerParam> customerParams) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.billerId        = billerId;
        dto.category        = "mobilePostpaid";
        dto.customerMobile  = customerMobile;
        dto.customerParams  = customerParams;
        return dto;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getBillerId()                           { return billerId; }
    public void setBillerId(String billerId)              { this.billerId = billerId; }

    public String getCategory()                           { return category; }
    public void setCategory(String category)              { this.category = category; }

    public Integer getAmount()                            { return amount; }
    public void setAmount(Integer amount)                 { this.amount = amount; }

    public Long getBillId()                               { return billId; }
    public void setBillId(Long billId)                    { this.billId = billId; }

    public String getCustomerMobile()                     { return customerMobile; }
    public void setCustomerMobile(String customerMobile)  { this.customerMobile = customerMobile; }

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
