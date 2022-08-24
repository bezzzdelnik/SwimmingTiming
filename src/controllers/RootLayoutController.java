package controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class RootLayoutController {
    private Stage primaryStage;
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    private static SerialPort serialPort;


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

    @FXML private RadioButton radio25m, radio50m;

    private ToggleGroup swimPool = new ToggleGroup();


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
        radio25m.setToggleGroup(swimPool);
        radio50m.setToggleGroup(swimPool);
        speedComboBox.setItems(speedList);
        dataBitsComboBox.setItems(dataBitsList);
        stopBitsComboBox.setItems(stopBits);
        parityComboBox.setItems(parityList);
        flowControlComboBox.setItems(flowControlList);

        portField.textProperty().addListener((observable, oldValue, newValue) -> serialPortName = newValue);

        speedComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.equals("110")) {
                        serialSpeed = 110;
                    }
                    if (newValue.equals("300")) {
                        serialSpeed = 300;
                    }
                    if (newValue.equals("1200")) {
                        serialSpeed = 1200;
                    }
                    if (newValue.equals("2400")) {
                        serialSpeed = 2400;
                    }
                    if (newValue.equals("4800")) {
                        serialSpeed = 4800;
                    }
                    if (newValue.equals("9600")) {
                        serialSpeed = 9600;
                    }
                    if (newValue.equals("14400")) {
                        serialSpeed = 14400;
                    }
                    if (newValue.equals("19200")) {
                        serialSpeed = 19200;
                    }
                    if (newValue.equals("38400")) {
                        serialSpeed = 38400;
                    }
                    if (newValue.equals("57600")) {
                        serialSpeed = 57600;
                    }
                    if (newValue.equals("115200")) {
                        serialSpeed = 115200;
                    }
                    if (newValue.equals("128000")) {
                        serialSpeed = 128000;
                    }
                    if (newValue.equals("256000")) {
                        serialSpeed = 256000;
                    }
                });

        dataBitsComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue.equals("5")) {
                        serialDataBits = 5;
                    }
                    if (newValue.equals("6")) {
                        serialDataBits = 6;
                    }
                    if (newValue.equals("7")) {
                        serialDataBits = 7;
                    }
                    if (newValue.equals("8")) {
                        serialDataBits = 8;
                    }
                });

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

        createGridPaneSplits();
    }

    private void createGridPaneSplits() {
        GridPane splits = new GridPane();
        for (int cell = 0; cell < 61; cell++) {
            for (int row = 0; row < 12; row++) {
                TextField newTextField = new TextField(cell + " " + row);
                //GridPane.setConstraints(newTextField, cell, row);
                splits.add(newTextField, cell, row);
            }
        }
        scrollPaneSplits.setContent(splits);
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
        serialPort = new SerialPort(serialPortName);
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
                serialPort.addEventListener(new PortReader(logArea, fileNameField.getText()), SerialPort.MASK_RXCHAR);
            }
            //Отправляем запрос устройству
        }
        catch (SerialPortException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void stopSerial() {
        try {
            if (serialPort.isOpened()) {
                serialPort.closePort();
            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

    }

    private static class PortReader implements SerialPortEventListener {
        TextArea logArea;
        String filePath;

        public PortReader(TextArea logArea, String filePath) {
            this.logArea = logArea;
            this.filePath = filePath;

        }

        public void serialEvent(SerialPortEvent event) {
            if(event.isRXCHAR() && event.getEventValue() > 0){
                try {
                    String data = serialPort.readString(event.getEventValue());
                    System.out.println(data);
                    String val;
                    if (data.contains(" 01 ") || data.contains(" 02 ") || data.contains(" 03 ") || data.contains(" 04 ") ||
                            data.contains(" 05 ") || data.contains(" 06 ") || data.contains(" 07 ") || data.contains(" 08 ") ||
                            data.contains(" 09 ") || data.contains(" 10 ") || data.contains(" 11 ") || data.contains(" 12 ") ||
                            data.contains(" 13 ") || data.contains(" 14 ") || data.contains(" 15 ") || data.contains(" 16 ") ||
                            data.contains(" 17 ") || data.contains(" 18 ") || data.contains(" 19 ") || data.contains(" 20 ") ) {
                        val = "<ITEMS><ITEM>1</ITEM></ITEMS>";
                    } else val = "<ITEMS><ITEM>0</ITEM></ITEMS>";



                    new Thread(new Runnable() {
                        @Override public void run() {
                            Platform.runLater(new Runnable() {
                                @Override public void run() {
                                    logArea.setText(data);
                                }
                            });
                        }
                    }).start();


                    BufferedWriter out = new BufferedWriter(new FileWriter(filePath));
                    out.write(val);
                    out.close();
                }
                catch (SerialPortException | IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

}
