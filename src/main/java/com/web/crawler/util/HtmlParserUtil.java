// HtmlParserUtil.java
package com.web.crawler.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HtmlParserUtil {

    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "WebCrawler/1.0";

    public static List<String> extractLinks(String url) throws IOException {
        List<String> links = new ArrayList<>();

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .execute();

            String contentType = response.contentType();
            if (contentType == null ||
                    !(contentType.startsWith("text/") || contentType.contains("xml") || contentType.contains("+xml"))) {
                log.warn("Unhandled content type at {}: {}", url, contentType);
                return links;
            }

            Document doc = response.parse();

            for (Element link : doc.select("a[href]")) {
                String href = link.attr("href");

                if (isInvalidLink(href)) {
                    continue;
                }

                String absoluteUrl = resolveUrl(url, href);

                if (absoluteUrl != null && UrlUtils.isValidUrl(absoluteUrl) && isHttpLink(absoluteUrl)) {
                    links.add(absoluteUrl);
                }
            }

        } catch (IOException e) {
            //Do nothing
        }

        return links;
    }

    private static boolean isInvalidLink(String href) {
        if (href == null || href.trim().isEmpty()) {
            return true;
        }

        href = href.toLowerCase().trim();

        return href.startsWith("mailto:") ||
                href.startsWith("tel:") ||
                href.startsWith("javascript:") ||
                href.startsWith("ftp:") ||
                href.startsWith("#") ||
                href.equals("/") ||
                href.startsWith("data:");
    }

    private static boolean isHttpLink(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "http" .equals(scheme) || "https" .equals(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    private static String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            URI base = URI.create(baseUrl);
            URI resolved = base.resolve(relativeUrl);
            return resolved.toString();
        } catch (Exception e) {
            log.debug("Failed to resolve URL {} against base {}: {}", relativeUrl, baseUrl, e.getMessage());
            return null;
        }
    }
}