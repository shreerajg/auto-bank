package com.autobank.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class I18nTest {

    @Test
    public void testEnglishTranslationsLoad() {
        I18n.load("en");
        assertEquals("en", I18n.getCurrentLang());
        assertNotNull(I18n.t("app.title"));
        assertNotEquals("app.title", I18n.t("app.title"), "app.title should have a valid translation");
    }

    @Test
    public void testMarathiTranslationsLoad() {
        I18n.load("mr");
        assertEquals("mr", I18n.getCurrentLang());
        assertNotNull(I18n.t("app.title"));
        assertNotEquals("app.title", I18n.t("app.title"), "app.title should have a valid Marathi translation");
    }

    @Test
    public void testFallbackForUnknownKey() {
        I18n.load("en");
        String unknownKey = "unknown.nonexistent.key.123";
        assertEquals(unknownKey, I18n.t(unknownKey), "Unknown keys should return the key itself as fallback");
    }

    @Test
    public void testResourceBundleKeys() {
        I18n.load("en");
        var bundle = I18n.getBundle();
        assertNotNull(bundle);
        assertTrue(bundle.containsKey("app.title"));
    }
}
