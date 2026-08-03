package com.vn.qrscanner.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StringExtensionsTest {

    @Test
    fun testIsLink() {
        // Valid links
        assertTrue("https://google.com".isLink())
        assertTrue("http://google.com".isLink())
        assertTrue("www.google.com".isLink())
        assertTrue("google.com".isLink())
        assertTrue("http://192.168.1.1".isLink())
        
        // Invalid links
        assertFalse("just text".isLink())
        assertFalse("12345".isLink())
        assertFalse("".isLink())
    }

    @Test
    fun testToWebUri() {
        val uri1 = "https://google.com".toWebUri()
        assertTrue(uri1.scheme == "https")
        assertTrue(uri1.host == "google.com")

        val uri2 = "google.com".toWebUri()
        assertTrue(uri2.scheme == "https")
        assertTrue(uri2.host == "google.com")
        
        val uri3 = "http://google.com".toWebUri()
        assertTrue(uri3.scheme == "http")
        assertTrue(uri3.host == "google.com")
    }
}
