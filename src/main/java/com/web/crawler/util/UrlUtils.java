package com.web.crawler.util;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class UrlUtils {

    public static String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : null;
        } catch (URISyntaxException e) {
            log.debug("Invalid URL for domain extraction: {}", url);
            return null;
        }
    }

    public static boolean isSameDomain(String url, String targetDomain) {
        if (targetDomain == null) return false;

        String urlDomain = extractDomain(url);
        if (urlDomain == null) return false;

        // Exact domain match only - no subdomains
        return targetDomain.equals(urlDomain);
    }

    public static String normalizeUrl(String url) {
        if (url == null) return null;

        try {
            URI uri = new URI(url);

            // Remove fragment
            uri = new URI(uri.getScheme(), uri.getAuthority(),
                    uri.getPath(), uri.getQuery(), null);

            String normalized = uri.toString();

            // Remove trailing slash unless it's root
            if (normalized.endsWith("/") && normalized.length() > 1) {
                String path = uri.getPath();
                if (path != null && path.length() > 1) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
            }

            return normalized;

        } catch (URISyntaxException e) {
            log.debug("Failed to normalize URL: {}", url);
            return url;
        }
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            URI uri = new URI(url);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}