package com.popclub.api.rcbp.enums;

/**
 * URL paths for RCBP (Recharge, Credit card & Bill Payment) APIs.
 *
 * URL paths for RCBP APIs.
 *
 * Two base URLs are in use:
 *   rcbp.base.url     → {{pop-rcbp-base}}                (catalogue, recharge, CC all, prepaid initiate)
 *   rcbp.api.base.url → https://api.popclub.co.in/rcbp/api (postpaid+prepaid bills/payment)
 *
 * Paths have no /api prefix — they begin with /v2/ or /v3/...
 */
public enum RcbpRoutes {

    // ── Catalogue ─────────────────────────────────────────────────────────────
    CATALOGUE_BILLERS               ("/v2/catalogue/billers"),

    // ── Bills ─────────────────────────────────────────────────────────────────
    BILLS_FETCH                     ("/v2/bills/fetch"),

    // ── Recharge / Prepaid ────────────────────────────────────────────────────
    RECHARGE_FETCH_PLANS            ("/v2/recharge/fetch/plans"),
    RECHARGE_FETCH_OPERATORS_STATES ("/v2/recharge/fetch/operators/states"),
    RECHARGE_FETCH_OPERATOR_CIRCLE  ("/v2/recharge/fetch/operator-circle"),

    // ── Payment ───────────────────────────────────────────────────────────────
    /** POST /v2/payment/initiate — postpaid, prepaid, credit card */
    PAYMENT_INITIATE                ("/v2/payment/initiate"),

    /** POST /v3/payment/confirm — postpaid and prepaid */
    PAYMENT_CONFIRM                 ("/v3/payment/confirm"),

    /** POST /v3/payment/confirmation — credit card (different path suffix) */
    PAYMENT_CONFIRMATION            ("/v3/payment/confirmation");

    private final String path;

    RcbpRoutes(String path) { this.path = path; }

    public String getPath() { return path; }
}
