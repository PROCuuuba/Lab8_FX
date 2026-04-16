package com.example.lab8_FX;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class P2PApp extends Application {

    private Peer peer;
    private ListView<String> peerListView;
    private TextArea statusArea;

    private TextField nicknameField;
    private TextField tcpPortField;
    private TextField udpPortField;

    private TextField remoteIpField;
    private TextField remotePortField;

    private Button startButton;
    private Button callButton;
    private Button endCallButton;
    private Button retryButton;

    private Button pttButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("P2P Голосовой звонок");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setTop(createSettingsPanel());
        root.setCenter(createCenterPanel());
        root.setBottom(createCallPanel());

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createSettingsPanel() {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        nicknameField = new TextField("User");
        tcpPortField = new TextField("5000");
        udpPortField = new TextField("5001");

        startButton = new Button("Запустить");
        startButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        startButton.setOnAction(e -> startPeer());

        grid.add(new Label("Ник:"), 0, 0);
        grid.add(nicknameField, 1, 0);

        grid.add(new Label("TCP:"), 2, 0);
        grid.add(tcpPortField, 3, 0);

        grid.add(new Label("UDP:"), 4, 0);
        grid.add(udpPortField, 5, 0);

        grid.add(startButton, 6, 0);

        panel.getChildren().add(grid);
        return panel;
    }

    private SplitPane createCenterPanel() {
        SplitPane split = new SplitPane();

        VBox left = new VBox(5);
        left.setPadding(new Insets(10));

        peerListView = new ListView<>();
        peerListView.setOnMouseClicked(e -> {
            String selected = peerListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String[] parts = selected.split(" \\| ");
                if (parts.length >= 2) {
                    String[] ipPort = parts[1].split(":");
                    remoteIpField.setText(ipPort[0]);
                    remotePortField.setText(ipPort[1]);
                }
            }
        });

        left.getChildren().addAll(new Label("Пользователи:"), peerListView);

        VBox right = new VBox(5);
        right.setPadding(new Insets(10));

        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefHeight(250);

        right.getChildren().addAll(new Label("Статус:"), statusArea);

        split.getItems().addAll(left, right);
        split.setDividerPositions(0.4);

        return split;
    }

    private VBox createCallPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        remoteIpField = new TextField("127.0.0.1");
        remotePortField = new TextField("5000");

        callButton = new Button("Позвонить");
        callButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        callButton.setDisable(true);
        callButton.setOnAction(e -> call());

        endCallButton = new Button("Завершить");
        endCallButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        endCallButton.setDisable(true);
        endCallButton.setOnAction(e -> endCall());

        retryButton = new Button("Перезвонить");
        retryButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        retryButton.setDisable(true);
        retryButton.setOnAction(e -> retryCall());

        pttButton = new Button("🎤 Push To Talk");
        pttButton.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white;");
        pttButton.setDisable(false);

        pttButton.setOnMousePressed(e -> {
            if (peer != null) {
                peer.setPushToTalk(true);
                updateStatus("🎤 Микрофон ВКЛ");
            }
        });

        pttButton.setOnMouseReleased(e -> {
            if (peer != null) {
                peer.setPushToTalk(false);
                updateStatus("🔇 Микрофон ВЫКЛ");
            }
        });

        grid.add(new Label("IP:"), 0, 0);
        grid.add(remoteIpField, 1, 0);

        grid.add(new Label("Port:"), 2, 0);
        grid.add(remotePortField, 3, 0);

        grid.add(callButton, 4, 0);
        grid.add(endCallButton, 5, 0);
        grid.add(retryButton, 6, 0);
        grid.add(pttButton, 7, 0);

        panel.getChildren().add(grid);
        return panel;
    }

    private void startPeer() {
        String nickname = nicknameField.getText();
        int tcpPort = Integer.parseInt(tcpPortField.getText());
        int udpPort = Integer.parseInt(udpPortField.getText());

        peer = new Peer(nickname, tcpPort, udpPort,
                this::updateStatus,
                this::updatePeerList);

        peer.start();

        startButton.setDisable(true);
        callButton.setDisable(false);

        updateStatus("Система запущена");
    }

    private void call() {
        peer.startCall(
                remoteIpField.getText(),
                Integer.parseInt(remotePortField.getText())
        );

        callButton.setDisable(true);
        endCallButton.setDisable(false);
        retryButton.setDisable(false);
    }

    private void endCall() {
        peer.endCall();

        callButton.setDisable(false);
        endCallButton.setDisable(true);
        retryButton.setDisable(true);
    }

    private void retryCall() {
        peer.startCall(
                remoteIpField.getText(),
                Integer.parseInt(remotePortField.getText())
        );
    }

    private void updateStatus(String msg) {
        Platform.runLater(() -> {
            statusArea.appendText(msg + "\n");
        });
    }

    private void updatePeerList(String data) {
        Platform.runLater(() -> {
            peerListView.getItems().clear();

            String[] lines = data.split("\n");
            for (String line : lines) {
                if (!line.isEmpty()) {
                    String[] p = line.split("\\|");
                    if (p.length >= 3) {
                        peerListView.getItems().add(p[0] + " | " + p[1] + ":" + p[2]);
                    }
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}