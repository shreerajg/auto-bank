package com.autobank.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
}
