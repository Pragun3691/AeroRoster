package com.aeroroster.support;

/** Existing business workflow tests fetch a fresh edit token; stale-token tests bypass this helper. */
public final class EditTokens {
    private EditTokens() { }

    public static boolean required(String method, String path) {
        return method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE")
                || method.equals("POST") && path.endsWith("/reassignments");
    }

    public static String resource(String path) {
        return path.replaceFirst("/(activity|status|cancellation|reassignments|expiry)$", "");
    }
}
