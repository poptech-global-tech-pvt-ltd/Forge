package com.popclub.api.rcbp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfirmPaymentRequestDto {

    private String orderNumber;
    private String paymentReferenceId;
    private String paymentMode;
    private Double paymentAmount;
    private Double cashAmountPaid;
    private int    coinsUsed;
    private String mobileNumber;

    public static ConfirmPaymentRequestDto forMobilePostpaid(
            String orderNumber, String paymentReferenceId,
            double billAmount, String mobileNumber) {
        ConfirmPaymentRequestDto dto = new ConfirmPaymentRequestDto();
        dto.orderNumber        = orderNumber;
        dto.paymentReferenceId = paymentReferenceId;
        dto.paymentMode        = "upi";
        dto.paymentAmount      = billAmount;
        dto.cashAmountPaid     = billAmount;
        dto.coinsUsed          = 0;
        dto.mobileNumber       = mobileNumber;
        return dto;
    }

    public static ConfirmPaymentRequestDto forMobilePrepaid(
            String orderNumber, String paymentReferenceId,
            double billAmount, String mobileNumber) {
        ConfirmPaymentRequestDto dto = new ConfirmPaymentRequestDto();
        dto.orderNumber        = orderNumber;
        dto.paymentReferenceId = paymentReferenceId;
        dto.paymentMode        = "upi";
        dto.paymentAmount      = billAmount;
        dto.cashAmountPaid     = billAmount;
        dto.coinsUsed          = 0;
        dto.mobileNumber       = mobileNumber;
        return dto;
    }

    public static ConfirmPaymentRequestDto forCreditCard(
            String orderNumber, String paymentReferenceId,
            double paymentAmount, double cashAmountPaid,
            int coinsUsed, String mobileNumber) {
        ConfirmPaymentRequestDto dto = new ConfirmPaymentRequestDto();
        dto.orderNumber        = orderNumber;
        dto.paymentReferenceId = paymentReferenceId;
        dto.paymentMode        = "RAZORPAY_UPI";
        dto.paymentAmount      = paymentAmount;
        dto.cashAmountPaid     = cashAmountPaid;
        dto.coinsUsed          = coinsUsed;
        dto.mobileNumber       = mobileNumber;
        return dto;
    }

    public String getOrderNumber()                { return orderNumber; }
    public void   setOrderNumber(String v)        { this.orderNumber = v; }

    public String getPaymentReferenceId()         { return paymentReferenceId; }
    public void   setPaymentReferenceId(String v) { this.paymentReferenceId = v; }

    public String getPaymentMode()                { return paymentMode; }
    public void   setPaymentMode(String v)        { this.paymentMode = v; }

    public Double getPaymentAmount()              { return paymentAmount; }
    public void   setPaymentAmount(Double v)      { this.paymentAmount = v; }

    public Double getCashAmountPaid()             { return cashAmountPaid; }
    public void   setCashAmountPaid(Double v)     { this.cashAmountPaid = v; }

    public int    getCoinsUsed()                  { return coinsUsed; }
    public void   setCoinsUsed(int v)             { this.coinsUsed = v; }

    public String getMobileNumber()               { return mobileNumber; }
    public void   setMobileNumber(String v)       { this.mobileNumber = v; }
}
