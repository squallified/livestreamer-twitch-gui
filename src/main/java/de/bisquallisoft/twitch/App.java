package de.bisquallisoft.twitch;

import de.bisquallisoft.twitch.controller.MainController;
import de.bisquallisoft.twitch.utils.SysTrayUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setMinWidth(100.0);
        primaryStage.setMinHeight(80.0);

        URL location = getClass().getResource("/main.fxml");
        FXMLLoader loader = new FXMLLoader(location);

        Parent root = loader.load();
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Livestreamer Twitch GUI");

        MainController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);
        controller.setHostServices(getHostServices());

        primaryStage.getIcons().add(new Image("/app-icon.png"));
        primaryStage.show();

        SysTrayUtil.init(primaryStage);

        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });

    }

    public static void main(String[] args) {
        launch(args);
    }
}
