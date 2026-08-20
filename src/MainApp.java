import controllers.RootLayoutController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.*;

public class MainApp extends Application {
    private Stage primaryStage;
    private BorderPane rootLayout;
    private RootLayoutController controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("Swimming Client");

        initRootLayout();
    }

    public void initRootLayout() {
        try {
            // Загружаем корневой макет из fxml файла.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class
                    .getResource("view/RootLayout.fxml"));
            rootLayout = loader.load();

            Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
            double sceneWidth = Math.min(1920.0, visualBounds.getWidth());
            double sceneHeight = Math.min(1080.0, visualBounds.getHeight());

            Scene scene = new Scene(rootLayout, sceneWidth, sceneHeight);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1280);
            primaryStage.setMinHeight(720);
            primaryStage.setX(visualBounds.getMinX());
            primaryStage.setY(visualBounds.getMinY());
            primaryStage.setWidth(visualBounds.getWidth());
            primaryStage.setHeight(visualBounds.getHeight());
            primaryStage.setMaximized(true);
            // Даём контроллеру доступ к главному приложению.
            controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            primaryStage.show();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override

    public void stop(){
        controller.closeApp();
        System.exit(0);
        //Здесь Вы можете прописать все действия при закрытии Вашего приложения.

    }

    public static void main(String[] args) {
        launch(args);
    }

}
