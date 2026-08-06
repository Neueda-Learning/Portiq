package com.portiq.dto;

public class AuthResponse {

    private String token;
    private String username;
    private boolean biometricEnabled;

    public AuthResponse(String token, String username, boolean biometricEnabled) {
        this.token = token;
        this.username = username;
        this.biometricEnabled = biometricEnabled;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isBiometricEnabled() { return biometricEnabled; }
    public void setBiometricEnabled(boolean biometricEnabled) { this.biometricEnabled = biometricEnabled; }
}
