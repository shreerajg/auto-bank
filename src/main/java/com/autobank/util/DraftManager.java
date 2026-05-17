package com.autobank.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages unsaved form data across different panels.
 * Allows "Work as Draft" functionality when switching views.
 */
public class DraftManager {
    private static final DraftManager INSTANCE = new DraftManager();
    private final Map<String, Map<String, String>> drafts = new HashMap<>();

    private DraftManager() {}

    public static DraftManager getInstance() {
        return INSTANCE;
    }

    public void saveDraft(String formId, Map<String, String> data) {
        drafts.put(formId, new HashMap<>(data));
    }

    public Map<String, String> getDraft(String formId) {
        return drafts.getOrDefault(formId, new HashMap<>());
    }

    public void clearDraft(String formId) {
        drafts.remove(formId);
    }

    public boolean hasDraft(String formId) {
        return drafts.containsKey(formId) && !drafts.get(formId).isEmpty();
    }
}
