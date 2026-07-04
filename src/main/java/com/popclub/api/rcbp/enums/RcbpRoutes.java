package com.popclub.api.rcbp.enums;

/**
 * URL paths for RCBP (Recharge, Credit card & Bill Payment) APIs.
 *
 * All requests in the collection share a single base URL: {{pop-rcbp-base}}.
 * Paths have no /api prefix — they begin with /v2/...
 */
public enum RcbpRoutes {

    // ── Catalogue ─────────────────────────────────────────────────────────────
    CATALOGUE_BILLERS               ("/v2/catalogue/billers"),

    // ── Bills ─────────────────────────────────────────────────────────────────
    BILLS_FETCH                     ("/v2/bills/fetch"),

    // ── Recharge ─────────────────────────────────────────────────────────────
    RECHARGE_FETCH_PLANS            ("/v2/recharge/fetch/plans"),
    RECHARGE_FETCH_OPERATORS_STATES ("/v2/recharge/fetch/operators/states"),
    RECHARGE_FETCH_OPERATOR_CIRCLE  ("/v2/recharge/fetch/operator-circle");

    private final String path;

    RcbpRoutes(String path) { this.path = path; }

    public String getPath() { return path; }
}
