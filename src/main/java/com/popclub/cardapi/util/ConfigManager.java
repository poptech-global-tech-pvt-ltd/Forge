package com.popclub.cardapi.util;

import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = ConfigManager.class.getClassLoader()
                .getResourceAsStream("config/sit.properties")) {
            PROPS.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config/sit.properties", e);
        }
    }

    // Generated once per run: prefix 123456 + random 4 digits (e.g. 1234560023)
    private static final String MOBILE_NUMBER = "123456" + String.format("%04d", (int)(Math.random() * 10000));

    static {
        System.out.println("========================================");
        System.out.println(">>> Test Run Started");
        System.out.println(">>> Mobile Number : " + MOBILE_NUMBER);
        System.out.println("========================================");
    }

    private static String get(String key) {
        return PROPS.getProperty(key);
    }

    public static String getBaseUrl()       { return get("card.base.url"); }
    public static String getXSourceApiKey() { return get("card.x.source.api.key"); }
    public static String getMobileNumber()  { return MOBILE_NUMBER; }
    public static String getOtp()           { return get("card.otp"); }
    public static String getPan()           { return get("card.pan"); }
    public static String getPincode()       { return get("card.pincode"); }
    public static String getFirstName()     { return get("card.first.name"); }
    public static String getLastName()      { return get("card.last.name"); }
    public static String getEmail()         { return get("card.email"); }
    public static String getDob()           { return get("card.dob"); }
    public static String getGender()        { return get("card.gender"); }
    public static String getOccupation()    { return get("card.occupation"); }
    public static String getMaritalStatus()    { return get("card.marital.status"); }
    public static String getAddressLine1()     { return get("card.address.line1"); }
    public static String getAddressLine2()     { return get("card.address.line2"); }
    public static String getAddressLine3()     { return get("card.address.line3"); }
    public static String getAddressCity()      { return get("card.address.city"); }
    public static String getAddressState()     { return get("card.address.state"); }
    public static String getAddressCountry()   { return get("card.address.country"); }
    public static String getAddressPincode()   { return get("card.address.pincode"); }
    public static String getNameOnCard()             { return get("card.name.on.card"); }
    public static String getFatherName()             { return get("card.father.name"); }
    public static String getCompanyName()            { return get("card.company.name"); }
    public static String getDesignation()            { return get("card.designation"); }
    public static String getAnnualIncome()           { return get("card.annual.income"); }
    public static String getCompanyType()            { return get("card.company.type"); }
    public static String getProfession()             { return get("card.profession"); }
    public static String getProfessionalOccupation() { return get("card.professional.occupation"); }
    public static String getMockBaseUrl()             { return get("mock.base.url"); }
}