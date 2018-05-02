package anhlt.com.qrscanner.Utilities;

import java.util.regex.Pattern;

public class StringUtil {
    private static final String URL_REGEX = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

    public static boolean isLink(String text) {
        return Pattern.compile(URL_REGEX).matcher(text.toLowerCase()).matches();
    }
}
