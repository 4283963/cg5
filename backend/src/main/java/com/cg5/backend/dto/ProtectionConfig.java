package com.cg5.backend.dto;

public class ProtectionConfig {
    private Boolean protectionEnabled;
    private Boolean autoUnloadEnabled;
    private Double unloadAngleOffset;
    private Double windSpeedThreshold;

    public ProtectionConfig() {
    }

    public ProtectionConfig(Boolean protectionEnabled, Boolean autoUnloadEnabled,
                            Double unloadAngleOffset, Double windSpeedThreshold) {
        this.protectionEnabled = protectionEnabled;
        this.autoUnloadEnabled = autoUnloadEnabled;
        this.unloadAngleOffset = unloadAngleOffset;
        this.windSpeedThreshold = windSpeedThreshold;
    }

    public Boolean getProtectionEnabled() {
        return protectionEnabled;
    }

    public void setProtectionEnabled(Boolean protectionEnabled) {
        this.protectionEnabled = protectionEnabled;
    }

    public Boolean getAutoUnloadEnabled() {
        return autoUnloadEnabled;
    }

    public void setAutoUnloadEnabled(Boolean autoUnloadEnabled) {
        this.autoUnloadEnabled = autoUnloadEnabled;
    }

    public Double getUnloadAngleOffset() {
        return unloadAngleOffset;
    }

    public void setUnloadAngleOffset(Double unloadAngleOffset) {
        this.unloadAngleOffset = unloadAngleOffset;
    }

    public Double getWindSpeedThreshold() {
        return windSpeedThreshold;
    }

    public void setWindSpeedThreshold(Double windSpeedThreshold) {
        this.windSpeedThreshold = windSpeedThreshold;
    }
}
