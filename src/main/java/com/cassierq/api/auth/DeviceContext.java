package com.cassierq.api.auth;

/**
 * Device metadata read from request headers (not the JSON body) on
 * register/login/refresh — see {@code AuthController}. Every field is
 * optional; a client can send none, some, or all of them.
 */
public record DeviceContext(String deviceId, String deviceOs, String appVersion, String deviceType) {

    private static final java.util.Set<String> VALID_DEVICE_TYPES = java.util.Set.of("ANDROID", "IOS", "WEB");

    public boolean isEmpty() {
        return deviceId == null && deviceOs == null && appVersion == null && deviceType == null;
    }

    public boolean hasValidDeviceType() {
        return deviceType == null || VALID_DEVICE_TYPES.contains(deviceType);
    }
}
