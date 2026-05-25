package com.autobank.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class Toast {

    public enum Type {
        SUCCESS, ERROR, INFO
    }

    public static void show(StackPane container, String message) {
        show(container, message, Type.INFO);
    }

    public static void success(StackPane container, String message) {
        show(container, message, Type.SUCCESS);
    }

    public static void error(StackPane container, String message) {
        show(container, message, Type.ERROR);
    }

    private static void show(StackPane container, String message, Type type) {
        Label toast = new Label(message);
        toast.getStyleClass().add("toast");
        
        switch (type) {
            case SUCCESS: toast.getStyleClass().add("toast-success"); break;
            case ERROR:   toast.getStyleClass().add("toast-error");   break;
            case INFO:    toast.getStyleClass().add("toast-info");    break;
        }

        toast.setOpacity(0);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 40, 0));
        
        container.getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.seconds(2.5));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition seq = new SequentialTransition(fadeIn, stay, fadeOut);
        seq.setOnFinished(e -> container.getChildren().remove(toast));
        seq.play();
    }
}
