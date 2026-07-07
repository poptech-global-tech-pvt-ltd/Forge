package com.popclub.api.rcbp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for POST /v3/payment/confirm (postpaid, prepaid) and
 * POST /v3/payment/confirmation (credit card).
 *
 * Postpaid confirm body:
 *    { "orderNumber": "{{order_number}}", "paymentReferenceId": "pay_{{$timestamp}}",
 *      "paymentMode": "upi", "paymentAmount": {{bill_amount}},
 *      "cashAmountPaid": {{bill_amount}}, "coinsUsed": 0,
 *      "mobileNumber": "{{mobile_number}}" }
 *
 * Prepaid confirm body: same shape as postpaid (mobileNumber = {{phone}}).
 *
 * Credit card confirm body:
 *    { "orderNumber": ..., "paymentReferenceId": ..., "paymentMode": "RAZORPAY_UPI",
 *      "paymentAmount": {{bill_amount}}, "coinsUsed": {{coins_used}},
 *      "mobileNumber": "7760362814", "cashAmountPaid": {{discounted_amount}} }
 *
 * NOTE: The CC confirm request in the collection uses hardcoded orderNumber and
 * paymentReferenceId values (likely developer test artifacts). In automated tests
 * these are populated from earlier steps. No Postman test script exists for
 * CC confirm — only status 200 is asserted.
 *
 * Fields absent for a given usage are left null and omitted via @JsonInclude(NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfirmPaymentRequestDto {

    private String orderNumber;
    private String paymentReferenceId;
    private String paymentMode;
    private Double paymentAmount;
    private Double cashAmountPaid;
    private int    coinsUsed;
    private String mobileNumber;

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Mobile postpaid payment confirmation. */
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

    /** Mobile prepaid payment confirmation. */
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

    /**
     * Credit card payment confirmation.
     * mobileNumber is hardcoded in the collection ("7760362814").
     * cashAmountPaid uses discountedAmount (falls back to billAmount if not available).
     */
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

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getOrderNumber()               { return orderNumber; }
    public void   setOrderNumber(String v)       { this.orderNumber = v; }

    public String getPaymentReferenceId()        { return paymentReferenceId; }
    public void   setPaymentReferenceId(String v){ this.paymentReferenceId = v; }

    public String getPaymentMode()               { return paymentMode; }
    public void   setPaymentMode(String v)       { this.paymentMode = v; }

    public Double getPaymentAmount()             { return paymentAmount; }
    public void   setPaymentAmount(Double v)     { this.paymentAmount = v; }

    public Double getCashAmountPaid()            { return cashAmountPaid; }
    public void   setCashAmountPaid(Double v)    { this.cashAmountPaid = v; }

    public int    getCoinsUsed()                 { return coinsUsed; }
    public void   setCoinsUsed(int v)            { this.coinsUsed = v; }

    public String getMobileNumber()              { return mobileNumber; }
    public void   setMobileNumber(String v)      { this.mobileNumber = v; }
}
