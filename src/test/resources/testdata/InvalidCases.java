package com.popclub.testdata;

import lombok.Data;

/**
 * Maps to invalid_cases_card_onboarding.json.
 * All negative test input values live here — no hardcoded strings in test classes.
 *
 * Usage: InvalidCases.invalidToken(), InvalidCases.fakeValidPan(), etc.
 */
@Data
public class InvalidCases {

    // ── Static singleton ─────────────────────────────────────────
    private static final InvalidCases INSTANCE = TestDataLoader.loadInvalidCases();

    // ── Auth ─────────────────────────────────────────────────────
    public static String invalidToken()           { return INSTANCE.auth.invalidToken; }

    // ── SSO ──────────────────────────────────────────────────────
    public static String emptyString()            { return INSTANCE.sso.emptyString; }
    public static String shortMobile()            { return INSTANCE.sso.shortMobile; }
    public static String specialCharsMobile()     { return INSTANCE.sso.specialCharsMobile; }
    public static String alphabeticMobile()       { return INSTANCE.sso.alphabeticMobile; }
    public static String tooLongMobile()          { return INSTANCE.sso.tooLongMobile; }

    // ── PAN ──────────────────────────────────────────────────────
    public static String invalidPan()             { return INSTANCE.pan.invalidPan; }
    public static String specialCharsPan()        { return INSTANCE.pan.specialCharsPan; }
    public static String tooLongPan()             { return INSTANCE.pan.tooLongPan; }
    public static String fakeValidPan()           { return INSTANCE.pan.fakeValidPan; }

    // ── Pincode ──────────────────────────────────────────────────
    public static String lettersPincode()         { return INSTANCE.pincode.lettersPincode; }
    public static String shortPincode()           { return INSTANCE.pincode.shortPincode; }
    public static String specialCharsPincode()    { return INSTANCE.pincode.specialCharsPincode; }
    public static String tooLongPincode()         { return INSTANCE.pincode.tooLongPincode; }
    public static String invalidAddressPincode()  { return INSTANCE.pincode.invalidAddressPincode; }

    // ── Basic Details ─────────────────────────────────────────────
    public static String invalidEmail()           { return INSTANCE.basicDetails.invalidEmail; }
    public static String invalidDob()             { return INSTANCE.basicDetails.invalidDob; }
    public static String invalidGender()          { return INSTANCE.basicDetails.invalidGender; }

    // ── Personal Details ──────────────────────────────────────────
    public static String specialCharsName()       { return INSTANCE.personalDetails.specialCharsName; }

    // ── Professional Details ──────────────────────────────────────
    public static String negativeIncome()         { return INSTANCE.professionalDetails.negativeIncome; }
    public static String zeroIncome()             { return INSTANCE.professionalDetails.zeroIncome; }

    // ── Consent ───────────────────────────────────────────────────
    public static String wrongMobile()            { return INSTANCE.consent.wrongMobile; }

    // ── Delivery Address ──────────────────────────────────────────
    public static String invalidAddressType()     { return INSTANCE.deliveryAddress.invalidAddressType; }

    // ── Server Error ──────────────────────────────────────────────
    public static String malformedJson()          { return INSTANCE.serverError.malformedJson; }
    public static String emptyBody()              { return INSTANCE.serverError.emptyBody; }
    public static String invalidContentType()     { return INSTANCE.serverError.invalidContentType; }

    // ── Error Codes ───────────────────────────────────────────────
    public static String errInvalidPan()          { return INSTANCE.errorCodes.invalidPan; }

    private Auth               auth;
    private Sso                sso;
    private Pan                pan;
    private Pincode            pincode;
    private BasicDetails       basicDetails;
    private PersonalDetails    personalDetails;
    private ProfessionalDetails professionalDetails;
    private Consent            consent;
    private DeliveryAddress    deliveryAddress;
    private ServerError        serverError;
    private ErrorCodes         errorCodes;

    @Data
    public static class Auth {
        private String invalidToken;
    }

    @Data
    public static class Sso {
        private String emptyString;
        private String shortMobile;
        private String specialCharsMobile;
        private String alphabeticMobile;
        private String tooLongMobile;
    }

    @Data
    public static class Pan {
        private String invalidPan;
        private String specialCharsPan;
        private String tooLongPan;
        private String fakeValidPan;
    }

    @Data
    public static class Pincode {
        private String lettersPincode;
        private String shortPincode;
        private String specialCharsPincode;
        private String tooLongPincode;
        private String invalidAddressPincode;
    }

    @Data
    public static class BasicDetails {
        private String invalidEmail;
        private String invalidDob;
        private String invalidGender;
    }

    @Data
    public static class PersonalDetails {
        private String specialCharsName;
    }

    @Data
    public static class ProfessionalDetails {
        private String negativeIncome;
        private String zeroIncome;
    }

    @Data
    public static class Consent {
        private String wrongMobile;
    }

    @Data
    public static class DeliveryAddress {
        private String invalidAddressType;
    }

    @Data
    public static class ServerError {
        private String malformedJson;
        private String emptyBody;
        private String invalidContentType;
    }

    @Data
    public static class ErrorCodes {
        private String invalidPan;
    }
}
