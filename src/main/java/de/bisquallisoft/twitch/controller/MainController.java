package de.bisquallisoft.twitch.controller;

import de.bisquallisoft.twitch.utils.FxScheduler;
import de.bisquallisoft.twitch.Settings;
import de.bisquallisoft.twitch.Stream;
import de.bisquallisoft.twitch.TwitchApi;
import de.bisquallisoft.twitch.utils.IOUtils;
import javafx.animation.Timeline;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.Notifications;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    public static final int NOTIFICATION_DURATION = 5;

    private Stage primaryStage;

    @FXML
    private ListView<Stream> streamList;
    @FXML
    private ImageView previewImage;
    @FXML
    private ProgressIndicator livestreamerProgess;
    @FXML
    private TextField streamLink;
    @FXML
    private TextField txtStreamStatus;
    @FXML
    private TextField txtViewers;
    @FXML
    private Pane imageParent;
    @FXML
    private TextField txtGame;
    @FXML
    private ImageView imgLogo;
    @FXML
    private TextField txtStreamName;

    private TwitchApi api;
    private Settings settings = Settings.getInstance();
    private Timeline scheduledRefresh;
    private HostServices hostServices;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        previewImage.fitWidthProperty().bind(imageParent.widthProperty());
        previewImage.fitHeightProperty().bind(imageParent.heightProperty());

        //refresh streams periodically
        scheduledRefresh = FxScheduler.schedule(Duration.minutes(settings.getUpdateInterval()), this::refreshStreams);
        //add handler to reschedule stream refreshing when interval changes
        settings.updateIntervalProperty().addListener((observableValue, ov, nv) -> {
            scheduledRefresh.stop();
            scheduledRefresh = FxScheduler.schedule(Duration.minutes(nv.doubleValue()), this::refreshStreams);
        });

        initTwitchApi();
        initStreamList();

        Platform.runLater(streamList::requestFocus);
    }

    private void initTwitchApi() {
        if (settings.getAuthToken() == null) {
            String authToken = authenticate();
            settings.setAuthToken(authToken);
        }
        api = new TwitchApi(settings.getAuthToken());
        if (!api.isAuthValid()) {
            String authToken = authenticate();
            settings.setAuthToken(authToken);
            api.setAuthToken(authToken);
        }
        settings.save();
    }

    private void initStreamList() {
        streamList.getSelectionModel().selectedItemProperty().addListener((observableValue, ov, nv) -> {
            if (nv != null) {
                previewImage.setImage(null);
                setPreview(nv);
            }
        });

        streamLink.setOnAction(this::streamLinkAction);
        try {
            streamList.getItems().setAll(api.getFollowedStreams());
        } catch (SocketTimeoutException e) {
            log.info("Twitch API timeout");
        }
        if (!streamList.getItems().isEmpty()) {
            streamList.getSelectionModel().select(0);
        }
    }

    @FXML
    private void openSettingsWindow(ActionEvent event) {
        Parent root;
        try {
            root = FXMLLoader.load(getClass().getResource("/settings.fxml"));
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setTitle("Settings");
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refreshStreams() {
        log.debug("refreshing streams");

        List<Stream> oldStreamList = new ArrayList<>();
        streamList.getItems().forEach(oldStreamList::add);
        Stream selectedItem = streamList.getSelectionModel().getSelectedItem();
        try {
            streamList.getItems().setAll(api.getFollowedStreams());
            if (!streamList.getItems().isEmpty()) {
                if (selectedItem != null && streamList.getItems().contains(selectedItem)) {
                    streamList.getSelectionModel().select(selectedItem);
                } else {
                    streamList.getSelectionModel().select(0);
                }
                showNotifications(oldStreamList);
            }
        } catch (SocketTimeoutException e) {
            log.info("Twitch API timeout");
        }
    }

    private void showNotifications(List<Stream> oldStreamList) {
        if (settings.isNotifications()) {
            streamList.getItems().stream()
                    .filter(Objects::nonNull)
                    .filter(s -> !oldStreamList.contains(s)).forEach(s -> {

                ImageView img = new ImageView(s.getLogo());
                img.setFitHeight(40.0);
                img.setFitWidth(40.0);

                Notifications.create()
                        .title(s.getName() + " just went live!")
                        .text(s.getStatus())
                        .graphic(img)
                        .onAction(e -> launchLivestreamer(s.getUrl()))
                        .hideAfter(Duration.seconds(NOTIFICATION_DURATION))
                        .darkStyle()
                        .show();
            });
        }
    }

    void streamLinkAction(ActionEvent event) {
        String text = streamLink.getText();
        if (text.startsWith("http") || text.startsWith("www") || text.startsWith("twitch")) {
            launchLivestreamer(text);
        } else {
            launchLivestreamer("twitch.tv/" + text);
        }
        streamLink.setText("");
    }

    @FXML
    void previewClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            launchLivestreamer(streamList.getSelectionModel().getSelectedItem().getUrl());
        } else if (event.getButton() == MouseButton.SECONDARY) {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(streamList.getSelectionModel().getSelectedItem().getUrl());
            clipboard.setContent(content);
        }
    }

    @FXML
    void streamListClicked(MouseEvent event) {
        Stream selectedItem = streamList.getSelectionModel().getSelectedItem();
        if (selectedItem != null && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            launchLivestreamer(selectedItem.getUrl());
        }
    }




    private void setPreview(Stream stream) {
        new Thread(() -> {
            InputStream inputStream = IOUtils.downloadFile(stream.getPreviewImage());
            if (inputStream != null) {
                Image previewImage = new Image(inputStream);
                Platform.runLater(() -> this.previewImage.setImage(previewImage));
            }
        }).start();

        new Thread(() -> {
            InputStream inputStream = IOUtils.downloadFile(stream.getLogo());
            if (inputStream != null) {
                Image previewImage = new Image(inputStream);
                Platform.runLater(() -> this.imgLogo.setImage(previewImage));
            }
        }).start();

        txtStreamName.setText(stream.getName());
        txtStreamStatus.setText(stream.getStatus());
        txtViewers.setText(stream.getViewers() + "");
        txtGame.setText(stream.getGame());
    }

    @FXML
    void refreshPressed(ActionEvent event) {
        refreshStreams();
    }

    private void launchLivestreamer(String url) {
        livestreamerProgess.setPrefHeight(imageParent.getHeight());
        livestreamerProgess.setPrefWidth(imageParent.getWidth());

        try {
            new ProcessBuilder("livestreamer", url, settings.getQuality(), "--no-version-check", "-Q", "--retry-open", "3").start();
            livestreamerProgess.setVisible(true);
            livestreamerProgess.setProgress(-1);

            FxScheduler.scheduleOnce(Duration.seconds(6), () -> {
                livestreamerProgess.setProgress(1);
                livestreamerProgess.setVisible(false);
            });
        } catch (IOException e) {
            Dialogs.create()
                    .owner(primaryStage)
                    .title("Livestreamer is not in path")
                    .message("Please install livestreamer: livestreamer.readthedocs.org")
                    .showError();
        }

    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    private String authenticate() {
        WebView webView = new WebView();
        webView.getEngine().load(TwitchApi.AUTH_URL);

        Stage popup = new Stage();
        popup.setScene(new Scene((webView)));
        popup.initModality(Modality.APPLICATION_MODAL);
        popup.initOwner(primaryStage);

        StringBuilder result = new StringBuilder();
        webView.getEngine().locationProperty().addListener((observableValue, ov, url) -> {
            if (url.startsWith("http://localhost")) {
                String token = StringUtils.substringBetween(url, "=", "&");
                result.append(token);
                popup.close();
            }
        });
        popup.showAndWait();
        return result.toString();
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    void openChat(ActionEvent actionEvent) {
        String chatLink = "http://www.twitch.tv/%s/chat";
        Stream selectedStream = streamList.getSelectionModel().getSelectedItem();
        hostServices.showDocument(String.format(chatLink, selectedStream.getName().toLowerCase()));
    }
}
