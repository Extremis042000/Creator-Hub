package com.extremis.hub.tools.common;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Defense-in-depth before any user-supplied string reaches a template
 * engine — trims, collapses whitespace, strips control characters and
 * HTML tags. Not a substitute for the frontend encoding rendered
 * output. See docs/06-prd.md §5.4.
 */
@Component
public class TextSanitizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    public String sanitize(String input) {
        if (input == null) return null;
        String noTags = HTML_TAG.matcher(input).replaceAll("");
        String noControl = CONTROL_CHARS.matcher(noTags).replaceAll("");
        String collapsed = WHITESPACE_RUN.matcher(noControl).replaceAll(" ");
        return collapsed.trim();
    }
}
