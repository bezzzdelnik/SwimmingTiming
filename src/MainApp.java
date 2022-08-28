import controllers.RootLayoutController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.*;

public class MainApp extends Application {
    private Stage primaryStage;
    private BorderPane rootLayout;
    private RootLayoutController controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("Lynxs Client");

        initRootLayout();
    }

    public void initRootLayout() {
        try {
            // Загружаем корневой макет из fxml файла.
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(MainApp.class
                    .getResource("view/RootLayout.fxml"));
            rootLayout = loader.load();

            // Отображаем сцену, содержащую корневой макет.
            Scene scene = new Scene(rootLayout, 1920, 600);
            primaryStage.setScene(scene);
            primaryStage.minHeightProperty().set(600);
            primaryStage.minWidthProperty().set(1920);
            primaryStage.maxHeightProperty().set(1080);
            primaryStage.maxWidthProperty().set(1920);
            //primaryStage.getIcons().add(new Image("avt/caspar/client/icons/icons.png")) ;
            // Даём контроллеру доступ к главному прилодению.
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
