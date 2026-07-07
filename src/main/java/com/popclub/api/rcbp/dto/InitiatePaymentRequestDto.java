package com.popclub.api.rcbp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for POST /v2/payment/initiate.
 *
 * Three usages across collections:
 *
 * 1. Mobile postpaid:
 *    { "referenceId": {{reference_id}}, "category": "mobilepostpaid",
 *      "billId": {{bill_id}}, "amount": {{bill_amount}}, "coinsUsed": 0 }
 *
 * 2. Mobile prepaid:
 *    { "referenceId": {{plan_id}}, "category": "mobileprepaid",
 *      "planId": {{plan_id}}, "amount": {{plan_amount}}, "coinsUsed": 0 }
 *
 * 3. Credit card:
 *    { "referenceId": {{reference_id}}, "category": "creditcard",
 *      "billId": {{bill_id}}, "amount": {{bill_amount}}, "coinsUsed": {{coins_used}} }
 *
 * Fields absent for a given category are left null and omitted via @JsonInclude(NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitiatePaymentRequestDto {

    private Long   referenceId;
    private String category;
    private Long   billId;
    private Long   planId;
    private Double amount;
    private int    coinsUsed;

    // ── Factory methods ───────────────────────────────────────────────────────

    /** Mobile postpaid payment initiation. */
    public static InitiatePaymentRequestDto forMobilePostpaid(
            long referenceId, long billId, double amount) {
        InitiatePaymentRequestDto dto = new InitiatePaymentRequestDto();
        dto.referenceId = referenceId;
        dto.category    = "mobilepostpaid";
        dto.billId      = billId;
        dto.amount      = amount;
        dto.coinsUsed   = 0;
        return dto;
    }

    /** Mobile prepaid payment initiation. planId serves as referenceId too. */
    public static InitiatePaymentRequestDto forMobilePrepaid(
            long planId, double planAmount) {
        InitiatePaymentRequestDto dto = new InitiatePaymentRequestDto();
        dto.referenceId = planId;
        dto.category    = "mobileprepaid";
        dto.planId      = planId;
        dto.amount      = planAmount;
        dto.coinsUsed   = 0;
        return dto;
    }

    /** Credit card payment initiation. */
    public static InitiatePaymentRequestDto forCreditCard(
            long referenceId, long billId, double amount, int coinsUsed) {
        InitiatePaymentRequestDto dto = new InitiatePaymentRequestDto();
        dto.referenceId = referenceId;
        dto.category    = "creditcard";
        dto.billId      = billId;
        dto.amount      = amount;
        dto.coinsUsed   = coinsUsed;
        return dto;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Long   getReferenceId()               { return referenceId; }
    public void   setReferenceId(Long v)         { this.referenceId = v; }

    public String getCategory()                  { return category; }
    public void   setCategory(String v)          { this.category = v; }

    public Long   getBillId()                    { return billId; }
    public void   setBillId(Long v)              { this.billId = v; }

    public Long   getPlanId()                    { return planId; }
    public void   setPlanId(Long v)              { this.planId = v; }

    public Double getAmount()                    { return amount; }
    public void   setAmount(Double v)            { this.amount = v; }

    public int    getCoinsUsed()                 { return coinsUsed; }
    public void   setCoinsUsed(int v)            { this.coinsUsed = v; }
}
