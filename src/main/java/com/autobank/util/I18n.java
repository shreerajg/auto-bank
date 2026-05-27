package com.autobank.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;

public class I18n {

    private static Map<String, String> strings = Map.of();
    private static String currentLang = "en";

    public static void load(String lang) {
        currentLang = lang;
        try (InputStreamReader r = new InputStreamReader(
                I18n.class.getResourceAsStream("/lang/" + lang + ".json"),
                StandardCharsets.UTF_8)) {
            strings = new Gson().fromJson(r, new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception e) {
            strings = Map.of();
        }
    }

    public static String t(String key) {
        return strings.getOrDefault(key, key);
    }

    public static String getCurrentLang() { return currentLang; }

    public static ResourceBundle getBundle() {
        return new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                return strings.get(key);
            }

            @Override
            public Enumeration<String> getKeys() {
                return Collections.enumeration(strings.keySet());
            }
        };
    }
}
