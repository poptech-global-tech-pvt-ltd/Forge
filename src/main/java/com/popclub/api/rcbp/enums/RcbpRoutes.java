package com.popclub.api.rcbp.enums;

public enum RcbpRoutes {

    CATALOGUE_BILLERS               ("/v2/catalogue/billers"),
    BILLS_FETCH                     ("/v2/bills/fetch"),
    RECHARGE_FETCH_PLANS            ("/v2/recharge/fetch/plans"),
    RECHARGE_FETCH_OPERATORS_STATES ("/v2/recharge/fetch/operators/states"),
    RECHARGE_FETCH_OPERATOR_CIRCLE  ("/v2/recharge/fetch/operator-circle"),
    PAYMENT_INITIATE                ("/v2/payment/initiate"),
    PAYMENT_CONFIRM                 ("/v3/payment/confirm"),
    PAYMENT_CONFIRMATION            ("/v3/payment/confirmation");

    private final String path;

    RcbpRoutes(String path) { this.path = path; }

    public String getPath() { return path; }
}
