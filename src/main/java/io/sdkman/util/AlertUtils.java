package io.sdkman.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.function.Consumer;

///
/// 说明：此注释风格为Java 23版本开始支持的[JEP 467: Markdown Documentation Comments](https://openjdk.org/jeps/467)
///
/// 描述：对话框工具类
///
/// @author Huang Xiao
/// @version 1.0.0
/// @since 2025/11/4 19:14
///

public final class AlertUtils {

    /**
     * 显示错误对话框
     */
    public static void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void showErrorAlert(String title, String content, ButtonType buttonType, Consumer<ButtonType> action) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(buttonType, ButtonType.CANCEL);
        alert.showAndWait().ifPresent(action);
    }

    public static void showInfoAlert(String title, String content) {
        showInfoAlert(title, content, false);
    }

    public static void showInfoAlert(String title, String content, boolean isExit) {
        var alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        var okButton = new ButtonType(I18nManager.get("settings.ok"), ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(okButton);
        alert.showAndWait().ifPresent(_ -> {
            if (isExit) {
                Platform.exit();
            }
        });
    }
}
