package com.autobank.ui;

import com.autobank.config.DatabaseConfig;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DashboardController {

    @FXML private Label totalAccountsLabel;
    @FXML private Label totalBalanceLabel;
    @FXML private Label activeLoansLabel;
    @FXML private Label todayTxLabel;

    @FXML
    public void initialize() {
        run("SELECT COUNT(*) FROM accounts WHERE status = 'ACTIVE'",
            rs -> { if (rs.next()) totalAccountsLabel.setText(String.valueOf(rs.getInt(1))); });
        run("SELECT COALESCE(SUM(balance),0) FROM accounts WHERE status = 'ACTIVE'",
            rs -> { if (rs.next()) totalBalanceLabel.setText("₹ " + rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP)); });
        run("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE'",
            rs -> { if (rs.next()) activeLoansLabel.setText(String.valueOf(rs.getInt(1))); });
        run("SELECT COUNT(*) FROM transactions WHERE created_at::date = CURRENT_DATE",
            rs -> { if (rs.next()) todayTxLabel.setText(String.valueOf(rs.getInt(1))); });
    }

    @FunctionalInterface
    interface RsHandler { void accept(ResultSet rs) throws Exception; }

    private void run(String sql, RsHandler handler) {
        try (Connection c = DatabaseConfig.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            handler.accept(rs);
        } catch (Exception ignored) {}
    }
}
