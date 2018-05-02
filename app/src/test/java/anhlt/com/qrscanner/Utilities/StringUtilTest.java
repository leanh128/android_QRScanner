package anhlt.com.qrscanner.Utilities;

import org.junit.Test;

import static org.junit.Assert.*;

public class StringUtilTest {

    @Test
    public void isLink() {
        String normalText = "hello";
        String http = "http://google.com";
        String httpUpper = "HTTP://google.com";
        String https = "https://google.com";
        String httpsUpper = "HTTPS://google.com";
        assertFalse(StringUtil.isLink(normalText));
        assertTrue(StringUtil.isLink(http));
        assertTrue(StringUtil.isLink(httpUpper));
        assertTrue(StringUtil.isLink(https));
        assertTrue(StringUtil.isLink(httpsUpper));
    }
}