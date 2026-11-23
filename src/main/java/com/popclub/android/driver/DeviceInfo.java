package com.popclub.android.driver;

public class DeviceInfo {

    public String udid;
    /** ADB-over-TCP address for cloud devices (e.g. "10.25.11.224:12011"). Null for local devices. */
    public String adbAddress;
    /** Remote ADB server host (e.g. the STF server IP). Null for local ADB. */
    public String adbHost;
    /** Remote ADB server port (e.g. 5037 on the STF server). 0 means local ADB. */
    public int adbServerPort;
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

    /** Returns the identifier Appium should use as udid/deviceName capability. */
    public String appiumUdid() {
        return adbAddress != null ? adbAddress : udid;
    }
}