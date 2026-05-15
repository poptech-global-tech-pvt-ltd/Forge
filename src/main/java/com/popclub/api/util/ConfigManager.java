package com.popclub.api.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {

    private static final Properties props = new Properties();

    static {
        String env = System.getProperty("env", "sit");
        try (InputStream in = ConfigManager.class.getClassLoader()
                .getResourceAsStream("config/" + env + ".properties")) {
            if (in == null) throw new RuntimeException("Config file not found for env: " + env);
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    // Auth
    public static String getBaseUrl()        { return props.getProperty("base.url"); }
    public static String getXSourceApiKey()  { return props.getProperty("x.source.api.key"); }
    public static String getMobileNumber()   { return props.getProperty("mobile.number"); }

    // PAN details
    public static String getPan()          { return props.getProperty("pan"); }
    public static String getPinCode()      { return props.getProperty("pin.code"); }

    // Basic user details
    public static String getFirstName()    { return props.getProperty("first.name"); }
    public static String getMiddleName()   { return props.getProperty("middle.name"); }
    public static String getLastName()     { return props.getProperty("last.name"); }
    public static String getEmail()        { return props.getProperty("email"); }
    public static String getDob()          { return props.getProperty("dob"); }
    public static String getGender()       { return props.getProperty("gender"); }
    public static String getOccupation()   { return props.getProperty("occupation"); }
    public static String getMaritalStatus(){ return props.getProperty("marital.status"); }

    // Personal details
    public static String getUserLevel()    { return props.getProperty("user.level"); }
    public static String getNameOnCard()   { return props.getProperty("name.on.card"); }
    public static String getFatherName()   { return props.getProperty("father.name"); }

    // Professional details
    public static String getCompanyName()       { return props.getProperty("company.name"); }
    public static String getDesignation()       { return props.getProperty("designation"); }
    public static String getAnnualIncome()      { return props.getProperty("annual.income"); }
    public static String getCompanyType()       { return props.getProperty("company.type"); }
    public static String getProfession()        { return props.getProperty("profession"); }
    public static String getProfessionalOccupation() { return props.getProperty("professional.occupation"); }

    // Current address
    public static String getCurrentAddressType()     { return props.getProperty("current.address.type"); }
    public static String getCurrentAddressLine1()    { return props.getProperty("current.address.line1"); }
    public static String getCurrentAddressLine2()    { return props.getProperty("current.address.line2"); }
    public static String getCurrentAddressLine3()    { return props.getProperty("current.address.line3"); }
    public static String getCurrentAddressLandmark() { return props.getProperty("current.address.landmark"); }
    public static String getCurrentAddressCity()     { return props.getProperty("current.address.city"); }
    public static String getCurrentAddressState()    { return props.getProperty("current.address.state"); }
    public static String getCurrentAddressCountry()  { return props.getProperty("current.address.country"); }
    public static String getCurrentAddressPincode()  { return props.getProperty("current.address.pincode"); }

    // Office address
    public static String getOfficeAddressType()     { return props.getProperty("office.address.type"); }
    public static String getOfficeAddressLine1()    { return props.getProperty("office.address.line1"); }
    public static String getOfficeAddressLine2()    { return props.getProperty("office.address.line2"); }
    public static String getOfficeAddressLine3()    { return props.getProperty("office.address.line3"); }
    public static String getOfficeAddressLandmark() { return props.getProperty("office.address.landmark"); }
    public static String getOfficeAddressCity()     { return props.getProperty("office.address.city"); }
    public static String getOfficeAddressState()    { return props.getProperty("office.address.state"); }
    public static String getOfficeAddressCountry()  { return props.getProperty("office.address.country"); }
    public static String getOfficeAddressPincode()  { return props.getProperty("office.address.pincode"); }

    // Delivery address
    public static String getDeliveryAddressType()   { return props.getProperty("delivery.address.type"); }
}
