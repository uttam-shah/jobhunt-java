package com.jobhunt.util;

import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;

public final class TextUtil {

    private static final Pattern BLOCK_END = Pattern.compile("<\\s*(br|/p|/div|/li|/h[1-6])\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\r\\f\\u000B]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private TextUtil() {}

    /** HTML (possibly entity-escaped HTML, as Greenhouse sends) -> readable plain text. */
    public static String stripHtml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = StringEscapeUtils.unescapeHtml4(raw);
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll(" ");
        text = StringEscapeUtils.unescapeHtml4(text);
        text = SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    public static String orEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    /** First non-blank argument, or "". */
    public static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
