package controllers;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import orad.IREConnectionModel;
import orad.IREConnectionModelListener;
import orad.retalk2.Retalk2ConnectionController;
import util.Constants;


public class OradController {
    private Retalk2ConnectionController controller;
    private AnchorPane oradControllerAnchorPane;
    private RootLayoutController rootLayoutController;
    private VBox vBox;
    private GridPane gridPaneREconnection;
    private Button connectionButton, disconnectButton, loadScene, activateScene, unloadScene;
    private Label connectionStatus;
    private TextField reAddress, reCanvas, sceneName, sceneSlot;
    private HBox hBoxConnectionButton, hBoxSceneControl;



    public OradController(AnchorPane oradControllerAnchorPane, RootLayoutController rootLayoutController) {
        this.oradControllerAnchorPane = oradControllerAnchorPane;
        this.rootLayoutController = rootLayoutController;
    }

    public void init() {
        controller = new Retalk2ConnectionController();
        rootLayoutController.setOradController(controller);
        vBox = (VBox) oradControllerAnchorPane.getChildren().get(0);
        gridPaneREconnection = (GridPane) vBox.getChildren().get(0);
        hBoxConnectionButton = (HBox) gridPaneREconnection.getChildren().get(6);
        hBoxSceneControl = (HBox) gridPaneREconnection.getChildren().get(11);

        connectionButton = (Button) hBoxConnectionButton.getChildren().get(0);
        disconnectButton = (Button) hBoxConnectionButton.getChildren().get(1);
        loadScene = (Button) hBoxSceneControl.getChildren().get(0);
        activateScene = (Button) hBoxSceneControl.getChildren().get(1);
        unloadScene = (Button) hBoxSceneControl.getChildren().get(2);

        disconnectButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                disconnectRE();
            }
        });
        connectionButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                connectRE();
            }
        });

        loadScene.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.sendLoadScene(sceneName.getText());
            }
        });

        activateScene.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.sendActivateScene(sceneName.getText(), 255-Integer.parseInt(sceneSlot.getText()));
            }
        });

        unloadScene.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                controller.sendUnLoadScene(sceneName.getText());
            }
        });

        reAddress = (TextField) gridPaneREconnection.getChildren().get(3);
        reCanvas = (TextField) gridPaneREconnection.getChildren().get(4);
        sceneName = (TextField) gridPaneREconnection.getChildren().get(8);
        sceneSlot = (TextField) gridPaneREconnection.getChildren().get(10);
        connectionStatus = (Label) gridPaneREconnection.getChildren().get(5);

        controller.addListener(new IREConnectionModelListener() {
            @Override
            public void statusChanged(IREConnectionModel model, IREConnectionModel.Status status) {
                Platform.runLater(() -> {
                    connectionStatus.setText(status.toString());
                    switch (status) {
                        case Connecting:
                        case PreConnected:
                        case LoadingScene:
                            connectionStatus.setStyle(Constants.STATUS_FONT_ORANGE);
                            break;
                        case Connected:
                            connectionStatus.setStyle(Constants.STATUS_FONT_GREEN);
                            break;
                        case Disconnected:
                        default:
                            connectionStatus.setStyle(Constants.STATUS_FONT_RED);
                    }
                });
            }

            @Override
            public void hostNameChanged(IREConnectionModel model, String hostname) {

            }

            @Override
            public void canvasChanged(IREConnectionModel model, String canvas) {

            }

            @Override
            public void portChanged(IREConnectionModel model, int port) {

            }
        });

    }


    private void connectRE() {
        controller.setHostName(reAddress.getText());
        controller.setCanvasName(reCanvas.getText());
        controller.connect();
        if (connectionStatus.equals("Connected")) {
            rootLayoutController.setOradController(controller);
        }
    }

    private void disconnectRE() {
        controller.disconnect();
    }

}
