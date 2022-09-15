package controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import model.Participant;
import model.Split;
import orad.retalk2.Retalk2ConnectionController;
import util.DataReader;

import java.io.*;

public class RootLayoutController {
    private Stage primaryStage;
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    private static SerialPort serialPort;

    private Retalk2ConnectionController controller = null;

    @FXML
    private TextArea logArea;

    @FXML
    private TextField portField;

    @FXML
    private ComboBox<String> parityComboBox;

    @FXML
    private ComboBox<String> flowControlComboBox;

    @FXML
    private TextField fileNameField;

    @FXML
    private ComboBox<String> speedComboBox;

    @FXML
    private ComboBox<String> dataBitsComboBox;

    @FXML
    private ComboBox<String> stopBitsComboBox;

    @FXML private ScrollPane scrollPaneSplits;

    @FXML private Button startSerialButton, stopSerialButton;

    @FXML private RadioButton radio25m, radio50m;
    @FXML private RadioButton radio50Distance, radio100Distance, radio200Distance, radio400Distance, radio800Distance, radio1500Distance;

    @FXML public Label timerLabel;
    @FXML private Label connectionLabel;

    @FXML private ImageView connectionImageView;

    @FXML private AnchorPane oradControllerAnchorPane;

    private OradController oradConnectionController;

    private File discon = new File("src/pic/disconnected.png");
    private File con = new File("src/pic/connected.png");

    private GridPane splits = new GridPane();

    private ToggleGroup swimPool = new ToggleGroup();
    private int swimPoolSize = 50;
    private ToggleGroup distanceGroup = new ToggleGroup();
    private int distance = 50;

    private ObservableList<Participant> participants = FXCollections.observableArrayList();

    private DataReader dataReader = new DataReader(participants, this);


    private final ObservableList<String> parityList = FXCollections.observableArrayList("None", "Odd", "Even", "Mark", "Space");
    private final ObservableList<String> flowControlList = FXCollections.observableArrayList("None", "XON/XOFF", "RTS/CST");
    private final ObservableList<String> speedList = FXCollections.observableArrayList(
"110", "300", "1200", "2400", "4800", "9600", "14400", "19200", "38400", "57600", "115200", "128000", "256000");
    private final ObservableList<String> dataBitsList = FXCollections.observableArrayList("5", "6", "7", "8");
    private final ObservableList<String> stopBits = FXCollections.observableArrayList("1", "2", "1.5");

    private String serialPortName = "COM1";
    private int serialSpeed = 9600;
    private int serialDataBits = 8;
    private int serialStopBits = 1;
    private int serialParity = 0;
    private int serialFlowControl1 = 1;
    private int serialFlowControl2 = 2;




    @FXML
    private void initialize() {
        serialPort = new SerialPort(serialPortName);

        connectionImageView.setImage(new Image(discon.toURI().toString()));
        oradConnectionController = new OradController(oradControllerAnchorPane, this);
        oradConnectionController.init();


        radio25m.setToggleGroup(swimPool);
        radio50m.setToggleGroup(swimPool);
        radio50Distance.setToggleGroup(distanceGroup);
        radio100Distance.setToggleGroup(distanceGroup);
        radio200Distance.setToggleGroup(distanceGroup);
        radio400Distance.setToggleGroup(distanceGroup);
        radio800Distance.setToggleGroup(distanceGroup);
        radio1500Distance.setToggleGroup(distanceGroup);

        speedComboBox.setItems(speedList);
        dataBitsComboBox.setItems(dataBitsList);
        stopBitsComboBox.setItems(stopBits);
        parityComboBox.setItems(parityList);
        flowControlComboBox.setItems(flowControlList);

        portField.textProperty().addListener((observable, oldValue, newValue) -> serialPortName = newValue);

        speedComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> serialSpeed = Integer.parseInt(newValue));


        dataBitsComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> serialDataBits = Integer.parseInt(newValue));

        stopBitsComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.equals("1")) {
                        serialStopBits = 1;
                    }
                    if (newValue.equals("2")) {
                        serialStopBits = 2;
                    }
                    if (newValue.equals("1.5")) {
                        serialStopBits = 3;
                    }
                });

        parityComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.equals("None")) {
                        serialParity = 0;
                    }
                    if (newValue.equals("Odd")) {
                        serialParity = 1;
                    }
                    if (newValue.equals("Even")) {
                        serialParity = 2;
                    }
                    if (newValue.equals("Mark")) {
                        serialParity = 3;
                    }
                    if (newValue.equals("Space")) {
                        serialParity = 4;
                    }
                });

        flowControlComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.equals("None")) {
                        serialFlowControl1 = 0;
                        serialFlowControl2 = 0;
                    }
                    if (newValue.equals("XON/XOFF")) {
                        serialFlowControl1 = 1;
                        serialFlowControl2 = 2;
                    }
                    if (newValue.equals("RTS/CST")) {
                        serialFlowControl1 = 4;
                        serialFlowControl2 = 8;
                    }
                });
        distanceGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            RadioButton selectedBtn = (RadioButton) newValue;
            setDistance(selectedBtn);
        });




        createGridPaneSplits();
    }



    @FXML
    private void handleSaveAs() {
        FileChooser fileChooser = new FileChooser();
        // Задаём фильтр расширений
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(
                "XML files (*.xml)", "*.xml");
        fileChooser.getExtensionFilters().add(extFilter);

        // Показываем диалог сохранения файла
        File file = fileChooser.showSaveDialog(primaryStage);
        fileNameField.setText(file.getPath());
    }

    @FXML
    private void startSerial() {

        try {
            //Открываем порт
            serialPort.openPort();
            //Выставляем параметры
            serialPort.setParams(serialSpeed,
                    serialDataBits,
                    serialStopBits,
                    serialParity);
            //Включаем аппаратное управление потоком
            serialPort.setFlowControlMode(serialFlowControl1 |
                    serialFlowControl2);
            //Устанавливаем ивент лисенер и маску
            if (!fileNameField.getText().isEmpty()) {
                serialPort.addEventListener(new PortReader(logArea, fileNameField.getText(), this), SerialPort.MASK_RXCHAR);
            }
            //Отправляем запрос устройству
            if (serialPort.isOpened()) {
                connectionImageView.setImage(new Image(con.toURI().toString()));
                connectionLabel.setText("Connected");
                startSerialButton.setDisable(true);
                stopSerialButton.setDisable(false);
            }
        }
        catch (SerialPortException ex) {
            ex.printStackTrace();
            connectionLabel.setText(ex.getExceptionType());
        }
    }

    public void closeApp() {
        stopSerial();
    }

    @FXML
    private void stopSerial() {
        try {
            if (serialPort.isOpened()) {
                serialPort.closePort();
                if (!serialPort.isOpened()) {
                    stopSerialButton.setDisable(true);
                    startSerialButton.setDisable(false);
                    connectionLabel.setText("Disconnected");
                    connectionImageView.setImage(new Image(discon.toURI().toString()));
                }

            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

    }

//ОБРАБОТКА ДАННЫХ
    @FXML private void setSwimPoolSize(){
        if (radio25m.isSelected()) {
            swimPoolSize = 25;
            createGridPaneSplits();
        }
        if (radio50m.isSelected()) {
            swimPoolSize = 50;
            createGridPaneSplits();
        }
    }

    private void setDistance(RadioButton selectedBtn){

        distance = Integer.parseInt(selectedBtn.getText());
        createGridPaneSplits();

    }

    public void createGridPaneSplits() {
        splits = new GridPane();
        splits.setHgap(10);
        participants.clear();

        int columns = distance / swimPoolSize;
        Label nameLabel = new Label("Имя");
        Label laneLabel = new Label("Дорожка");
        GridPane.setHalignment(nameLabel, HPos.CENTER);
        splits.add(nameLabel, 1, 0);
        splits.add(laneLabel, 0, 0);
        for (int column = 2; column < (columns + 2); column++) {
            Label newDistanceLabel = new Label((column - 1) * swimPoolSize + "м");
            GridPane.setHalignment(newDistanceLabel, HPos.CENTER);
            splits.add(newDistanceLabel, column, 0);
        }
        for (int row = 1; row < 11; row++) {
            Participant newParticipant = new Participant();
            newParticipant.setName(row + " " + "Имя ФАМИЛИЯ");
            participants.add(newParticipant);
            Label newNameLabel = new Label(newParticipant.getName());
            newParticipant.nameProperty().addListener((observable, oldValue, newValue) ->
                    newNameLabel.setText(newValue));
            newNameLabel.setPrefWidth(200);
            splits.add(newNameLabel, 1, row);
            Label laneLbl = new Label();
            laneLbl.setStyle("-fx-font-weight: bold");
            GridPane.setHalignment(laneLbl, HPos.CENTER);
            laneLbl.setText(String.valueOf(row - 1));
            splits.add(laneLbl, 0, row);
            for (int column = 2; column < (columns + 2); column++) {
                TextField newTextField = new TextField();
                newTextField.setPrefWidth(70);
                int finalRow = row;
                int finalColumn = column;
                participants.get(finalRow - 1).getSplits().add((finalColumn - 2), new Split());
                newTextField.setText(participants.get(finalRow - 1).getSplits().get((finalColumn - 2)).getSpl());
                newTextField.textProperty().addListener((observable, oldValue, newValue) ->
                        participants.get(finalRow - 1).getSplits().get(finalColumn - 2).setSpl(newValue));
                participants.get(finalRow - 1).getSplits().get(finalColumn - 2).splProperty().addListener(
                        (observable, oldValue, newValue) -> {
                            if (oldValue.equals("")) {
                                newTextField.setText(newValue);
                                if (participants.get(finalRow - 1).getSplitCount() < participants.get(finalRow - 1).getSplits().size() - 1) {
                                    participants.get(finalRow - 1).setSplitCount(participants.get(finalRow - 1).getSplitCount()+1);
                                }

                            }

                        });
                splits.add(newTextField, column, row);

            }
        }
        scrollPaneSplits.setContent(splits);
    }

    public void setOradController(Retalk2ConnectionController controller) {
        this.controller = controller;
    }

    public Retalk2ConnectionController getController() {
        return controller;
    }

    @FXML private void hideLeaders() {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).isShowed()) {
                controller.sendAnimationPlay("Olympic/swimming", "swimmer_out_" + (i + 1));
            }
        }
    }

    @FXML private void hideRecordLine() {
        controller.sendAnimationPlay("Olympic/swimming", "WR_out");
    }

    @FXML private void showRecordLine(){
        controller.sendAnimationPlay("Olympic/swimming", "WR_in");
    }

    private class PortReader implements SerialPortEventListener {
        TextArea logArea;
        String filePath;
        RootLayoutController rootLayoutController;


        public PortReader(TextArea logArea, String filePath, RootLayoutController rootLayoutController) {
            this.logArea = logArea;
            this.filePath = filePath;
            this.rootLayoutController = rootLayoutController;
        }

        public void serialEvent(SerialPortEvent event) {

            if(event.isRXCHAR() && event.getEventValue() > 0){
                try {
                    String data = serialPort.readString(event.getEventValue());

                    /*String val;
                    if (data.contains(" 01 ") || data.contains(" 02 ") || data.contains(" 03 ") || data.contains(" 04 ") ||
                            data.contains(" 05 ") || data.contains(" 06 ") || data.contains(" 07 ") || data.contains(" 08 ") ||
                            data.contains(" 09 ") || data.contains(" 10 ") || data.contains(" 11 ") || data.contains(" 12 ") ||
                            data.contains(" 13 ") || data.contains(" 14 ") || data.contains(" 15 ") || data.contains(" 16 ") ||
                            data.contains(" 17 ") || data.contains(" 18 ") || data.contains(" 19 ") || data.contains(" 20 ") ) {
                        val = "<ITEMS><ITEM>1</ITEM></ITEMS>";
                    } else val = "<ITEMS><ITEM>0</ITEM></ITEMS>";*/




                    new Thread(() -> Platform.runLater(() -> logArea.setText(data))).start();
                    new Thread(() -> Platform.runLater(() -> dataReader.parseData(data))).start();

/*
                    BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
                    out.write(val);
                    out.close();*/
                }
                catch (SerialPortException ex) {
                    ex.printStackTrace();
                }
            }
        }

    }

}
