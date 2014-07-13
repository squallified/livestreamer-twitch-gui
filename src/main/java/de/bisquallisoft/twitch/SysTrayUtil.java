package de.bisquallisoft.twitch;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URL;

/**
 * @author squall
 */
public class SysTrayUtil {
    private static final Logger log = LoggerFactory.getLogger(SysTrayUtil.class);


    private TrayIcon trayIcon;
    private SystemTray tray;
    private double y;
    private double x;

    public SysTrayUtil(Stage primaryStage) {
        log.debug("primarystage: {}", primaryStage);

        if (!SystemTray.isSupported()) {
            log.warn("system tray not supported");
            return;
        }

        final PopupMenu popup = new PopupMenu();

        tray = SystemTray.getSystemTray();

        // Create a pop-up menu components
        MenuItem exitItem = new MenuItem("Exit");

        //Add components to pop-up menu
        popup.add(exitItem);

        exitItem.addActionListener(e -> {
            Platform.exit();
            System.exit(0);
        });

        initTrayIcon(primaryStage, popup);

        //handle minimize/restore
        primaryStage.iconifiedProperty().addListener((observableValue, aBoolean, minimize) -> {
            if (Settings.getInstance().getMinimizeToTray()) {
                if (minimize) {
                    primaryStage.close();
                    showInTray();
                } else {
                    primaryStage.setX(x);
                    primaryStage.setY(y);
                    removeFromTray();
                }
            }
        });

        //javafx sucks
        x = primaryStage.getX();
        y = primaryStage.getY();

        primaryStage.xProperty().addListener((observableValue, number, newValue) -> {
            if(newValue.doubleValue() > -30000 && newValue.intValue() != 8)
                x = newValue.doubleValue();
        });

        primaryStage.yProperty().addListener((observableValue, number, newValue) -> {
            if(newValue.doubleValue() > -30000 && newValue.intValue() != 32)
                y = newValue.doubleValue();
        });

    }

    private void initTrayIcon(Stage primaryStage, PopupMenu popup) {
        URL resource = SysTrayUtil.class.getResource("/app-icon.png");
        trayIcon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(resource));
        trayIcon.setImageAutoSize(true);
        trayIcon.setPopupMenu(popup);
        trayIcon.addActionListener(e -> {
            Platform.runLater(() -> {
                primaryStage.setIconified(false);
                primaryStage.show();
            });
            log.debug("showing primary stage");
        });
    }


    void showInTray() {
        try {
            tray.add(trayIcon);
            Platform.setImplicitExit(false);
        } catch (AWTException e) {
            log.error("could not add icon", e);
        }
    }

    void removeFromTray() {
        tray.remove(trayIcon);
        Platform.setImplicitExit(true);
    }
}
