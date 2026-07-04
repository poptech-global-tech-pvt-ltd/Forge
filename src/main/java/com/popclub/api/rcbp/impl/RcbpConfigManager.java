package com.popclub.api.rcbp.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads RCBP-specific config from src/test/resources/config/rcbp-{env}.properties.
 *
 * Postman collection variables and their mappings:
 *
 *   {{pop-rcbp-base}}   → rcbp.base.url        (single base URL for all requests)
 *   {{phone}}           → rcbp.phone            (customer mobile number)
 *   {{user_id}}         → rcbp.user.id          (X-Userid on credit card requests)
 *   {{userid}}          → rcbp.userid           (X-Userid on mobile postpaid requests)
 *   {{X-Userid}}        → rcbp.x.userid         (X-Userid on recharge operators/plans)
 *   (hardcoded UUID)    → rcbp.operator.userid  (X-Userid hardcoded in operator-circle request)
 *
 * NOTE: {{rcbp_token}} is declared as a collection variable but is DISABLED on
 * every request in the collection. No Authorization header is sent by any request.
 * It is not mapped here intentionally.
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

    /** Single base URL for all RCBP requests ({{pop-rcbp-base}}) */
    public static String getBaseUrl()         { return PROPS.getProperty("rcbp.base.url"); }

    /** Customer phone number ({{phone}}) */
    public static String getPhone()           { return PROPS.getProperty("rcbp.phone"); }

    /** X-Userid for credit card requests ({{user_id}}) */
    public static String getUserId()          { return PROPS.getProperty("rcbp.user.id"); }

    /** X-Userid for mobile postpaid requests ({{userid}}) */
    public static String getUserid()          { return PROPS.getProperty("rcbp.userid"); }

    /** X-Userid for recharge fetch operators/states and fetch plans ({{X-Userid}}) */
    public static String getXUserid()         { return PROPS.getProperty("rcbp.x.userid"); }

    /**
     * X-Userid for operator-circle request.
     * The Postman collection hardcodes the value "019cfef7-10b7-7cdc-8077-b96cf69e39d6"
     * directly in the header — not a variable. Supply the appropriate value here.
     */
    public static String getOperatorUserid()  { return PROPS.getProperty("rcbp.operator.userid"); }
}
