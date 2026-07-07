package com.popclub.api.rcbp.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads RCBP-specific config from src/test/resources/config/rcbp-{env}.properties.
 *
 * Postman collection variables and their mappings:
 *
 *   {{pop-rcbp-base}}   → rcbp.base.url         (base URL for catalogue, recharge, CC, prepaid initiate)
 *   (hardcoded URL)     → rcbp.api.base.url      (https://api.popclub.co.in/rcbp/api — postpaid+prepaid bills/payment)
 *   {{phone}}           → rcbp.phone             (customer mobile number)
 *   {{user_id}}         → rcbp.user.id           (X-Userid: billers for CC, postpaid, prepaid initiate)
 *   {{userid}}          → rcbp.userid            (not used in new collections; kept for reference)
 *   {{X-Userid}}        → rcbp.x.userid          (X-Userid on recharge operators/states)
 *   (hardcoded UUID)    → rcbp.operator.userid   ("019cfef7-10b7-7cdc-8077-b96cf69e39d6" — postpaid+prepaid bills/payment)
 *   (hardcoded UUID)    → rcbp.cc.input.userid   ("019a97c6-d10a-745c-b303-e39b963e540d" — CC input fields, bills, payment)
 *   {{operator_name}}   → rcbp.operator.name     (operator name string for prepaid fetch plans)
 *
 * NOTE: No Authorization header is sent by any request.
 *
 * Select environment via Maven: -Denv=sit (default)
 */
public class RcbpConfigManager {

    private static final Properties PROPS = new Properties();

    static {
        String env = System.getProperty("env", "sit");
        String file = "config/rcbp-" + env + ".properties";
        try (InputStream in = RcbpConfigManager.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) throw new RuntimeException("RCBP config not found: " + file);
            PROPS.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load RCBP config: " + file, e);
        }
    }

    private RcbpConfigManager() {}

    /** Base URL for catalogue, recharge, CC, and prepaid initiate requests ({{pop-rcbp-base}}) */
    public static String getBaseUrl()            { return PROPS.getProperty("rcbp.base.url"); }

    /**
     * Alternate base URL for postpaid and prepaid bill fetch / payment initiate / confirm.
     * Hardcoded as https://api.popclub.co.in/rcbp/api in those collection requests.
     */
    public static String getApiBaseUrl()         { return PROPS.getProperty("rcbp.api.base.url"); }

    /** Customer phone number ({{phone}}) */
    public static String getPhone()              { return PROPS.getProperty("rcbp.phone"); }

    /**
     * X-Userid for: CC list billers, postpaid list billers + input fields,
     * prepaid initiate payment ({{user_id}}).
     */
    public static String getUserId()             { return PROPS.getProperty("rcbp.user.id"); }

    /** X-Userid (legacy {{userid}} — kept for reference, not used in new collections) */
    public static String getUserid()             { return PROPS.getProperty("rcbp.userid"); }

    /** X-Userid for recharge fetch operators/states ({{X-Userid}}) */
    public static String getXUserid()            { return PROPS.getProperty("rcbp.x.userid"); }

    /**
     * X-Userid for operator-circle, postpaid/prepaid bill fetch, initiate, confirm.
     * Hardcoded as "019cfef7-10b7-7cdc-8077-b96cf69e39d6" in those collection requests.
     */
    public static String getOperatorUserid()     { return PROPS.getProperty("rcbp.operator.userid"); }

    /**
     * X-Userid for CC input fields, CC fetch bills, CC initiate, CC confirm.
     * Hardcoded as "019a97c6-d10a-745c-b303-e39b963e540d" in the CC collection.
     */
    public static String getCcInputUserId()      { return PROPS.getProperty("rcbp.cc.input.userid"); }

    /**
     * Operator name for prepaid fetch plans ({{operator_name}}).
     * Used as the "name" query parameter.
     */
    public static String getOperatorName()       { return PROPS.getProperty("rcbp.operator.name"); }
}
