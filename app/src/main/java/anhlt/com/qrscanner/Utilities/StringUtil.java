package anhlt.com.qrscanner.Utilities;

public class StringUtil {
    public static boolean isLink(String text) {
        return text.toLowerCase().startsWith("http:\\")
                || text.toLowerCase().startsWith("https:\\");
    }
}
