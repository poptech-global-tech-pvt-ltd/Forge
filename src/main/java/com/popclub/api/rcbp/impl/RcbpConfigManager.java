package com.popclub.api.rcbp.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

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

    public static String getBaseUrl()        { return PROPS.getProperty("rcbp.base.url"); }
    public static String getApiBaseUrl()     { return PROPS.getProperty("rcbp.api.base.url"); }
    public static String getPhone()          { return PROPS.getProperty("rcbp.phone"); }
    public static String getUserId()         { return PROPS.getProperty("rcbp.user.id"); }
    public static String getUserid()         { return PROPS.getProperty("rcbp.userid"); }
    public static String getXUserid()        { return PROPS.getProperty("rcbp.x.userid"); }
    public static String getOperatorUserid() { return PROPS.getProperty("rcbp.operator.userid"); }
    public static String getCcInputUserId()  { return PROPS.getProperty("rcbp.cc.input.userid"); }
    public static String getOperatorName()   { return PROPS.getProperty("rcbp.operator.name"); }
}
