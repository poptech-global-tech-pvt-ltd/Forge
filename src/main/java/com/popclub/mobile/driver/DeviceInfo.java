package com.popclub.mobile.driver;

public class DeviceInfo {

    public String udid;
    public int port;
    public String platformName;
    public String platformVersion;

    public DeviceInfo(String udid, int port) {
        this.udid = udid;
        this.port = port;
        this.platformName    = "Android";
        this.platformVersion = null;
    }

    public DeviceInfo(String udid, int port, String platformName, String platformVersion) {
        this.udid            = udid;
        this.port            = port;
        this.platformName    = platformName;
        this.platformVersion = platformVersion;
    }
}