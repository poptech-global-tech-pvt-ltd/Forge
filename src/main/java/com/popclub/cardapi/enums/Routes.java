package com.popclub.cardapi.enums;

public class Routes {

    private Routes() {}

    public static final String OTP_SEND   = "/api/v1/otp/send";
    public static final String OTP_VERIFY = "/api/v1/otp/verify";
    public static final String POP_CONSENTS    = "/api/v1/pop/consents";
    public static final String USER_DETAILS    = "/api/v1/pop/user-details";
    public static final String VERIFY_PINCODE  = "/api/v1/pop/verify_pincode";
    public static final String YBL_CONSENTS    = "/api/v1/ybl/consents";
    public static final String YBL_ADDRESS     = "/api/v1/ybl/address";
    public static final String YBL_ADDRESSES       = "/api/v1/ybl/addresses";
    public static final String YBL_PERSONAL_DETAILS      = "/api/v1/ybl/personal-details";
    public static final String YBL_PROFESSIONAL_DETAILS  = "/api/v1/ybl/professional-details";
    public static final String YBL_MASTER_LISTS           = "/api/v1/ybl/master-lists";

    // Mock gateway routes (Consent / EKYC / VKYC)
    public static final String MOCK_CONSENT = "/CCPartnerProgram/v1/enc/Consent";
    public static final String MOCK_EKYC    = "/CCPartnerProgram/v1/enc/EKYC";
    public static final String MOCK_VKYC    = "/CCPartnerProgram/v1/enc/VKYC";
}