package com.web.crawler.util;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlUtils {

    public static URI parseAndValidateUrl(String url) throws URISyntaxException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        URI uri = new URI(url);

        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid URL: missing host");
        }

        return uri;
    }

    public static String normalizeUrl(String url) {
        try {
            URI uri = parseAndValidateUrl(url);

            // Remove fragment
            uri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);

            String normalized = uri.toString();

            if (normalized.endsWith("/") && !normalized.matches("https?://[^/]+/$")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            return normalized;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isValidUrl(String url) {
        try {
            parseAndValidateUrl(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}