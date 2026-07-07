package com.popclub.api.rcbp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchBillRequestDto {

    private String billerId;
    private String category;
    private Long   planId;
    private java.util.List<CustomerParam> customerParams;

    public FetchBillRequestDto() {}

    public static FetchBillRequestDto forCreditCard(
            String billerId,
            java.util.List<CustomerParam> customerParams) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.billerId       = billerId;
        dto.category       = "creditcard";
        dto.customerParams = customerParams;
        return dto;
    }

    public static FetchBillRequestDto forMobilePostpaid(
            String billerId,
            java.util.List<CustomerParam> customerParams) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.billerId       = billerId;
        dto.category       = "mobilepostpaid";
        dto.customerParams = customerParams;
        return dto;
    }

    public static FetchBillRequestDto forMobilePrepaid(long planId) {
        FetchBillRequestDto dto = new FetchBillRequestDto();
        dto.category = "mobileprepaid";
        dto.planId   = planId;
        return dto;
    }

    public String getBillerId()                            { return billerId; }
    public void   setBillerId(String billerId)             { this.billerId = billerId; }

    public String getCategory()                            { return category; }
    public void   setCategory(String category)             { this.category = category; }

    public Long getPlanId()                                { return planId; }
    public void setPlanId(Long planId)                     { this.planId = planId; }

    public java.util.List<CustomerParam> getCustomerParams() { return customerParams; }
    public void setCustomerParams(java.util.List<CustomerParam> cp) { this.customerParams = cp; }

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
