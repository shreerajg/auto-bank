package com.autobank.util;

import com.autobank.auth.model.UserSession;
import com.autobank.config.DatabaseConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages unsaved form data across different panels.
 * Persistent version using database storage.
 */
public class DraftManager {
    private static final Logger log = LoggerFactory.getLogger(DraftManager.class);
    private static final DraftManager INSTANCE = new DraftManager();
    private final Gson gson = new Gson();

    private DraftManager() {}

    public static DraftManager getInstance() {
        return INSTANCE;
    }

    public void saveDraft(String formId, Map<String, String> data) {
        int userId = getUserId();
        if (userId <= 0) return;

        if (data == null || data.isEmpty()) {
            clearDraft(formId);
            return;
        }

        String json = gson.toJson(data);
        String sql = "INSERT INTO form_drafts (form_id, user_id, draft_data) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE draft_data = ?, updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, formId);
            stmt.setInt(2, userId);
            stmt.setString(3, json);
            stmt.setString(4, json);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to save draft for form {}: {}", formId, e.getMessage());
        }
    }

    public Map<String, String> getDraft(String formId) {
        int userId = getUserId();
        if (userId <= 0) return new HashMap<>();

        String sql = "SELECT draft_data FROM form_drafts WHERE form_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, formId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("draft_data");
                    return gson.fromJson(json, new TypeToken<Map<String, String>>(){}.getType());
                }
            }
        } catch (Exception e) {
            log.error("Failed to load draft for form {}: {}", formId, e.getMessage());
        }
        return new HashMap<>();
    }

    public void clearDraft(String formId) {
        int userId = getUserId();
        if (userId <= 0) return;

        String sql = "DELETE FROM form_drafts WHERE form_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, formId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to clear draft for form {}: {}", formId, e.getMessage());
        }
    }

    public boolean hasDraft(String formId) {
        int userId = getUserId();
        if (userId <= 0) return false;

        String sql = "SELECT 1 FROM form_drafts WHERE form_id = ? AND user_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, formId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int getUserId() {
        var user = UserSession.getInstance().getCurrentUser();
        return (user != null) ? user.getId() : -1;
    }
}
