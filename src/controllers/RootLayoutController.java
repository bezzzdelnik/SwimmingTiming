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
import java.nio.ByteBuffer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootLayoutController {
    private Properties properties = new Properties();
    private Stage primaryStage;
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    private static SerialPort serialPort;

    private Retalk2ConnectionController controller = null;

    private static final String RE_SCENE = "Olympic/swimming";
    /** Сцена плашек лидеров (ORAD). */
    private static final String PLACE_SWIMMING_SCENE = "Olympic/Place_swimming";
    private static final String PLACE_SWIMMING_ALL_GROUP_VISIBLE = "Object_ALL-GROUP_Object_Visible";
    private static final int LANES_COUNT = 10;
    private static final long POSITION_DEBOUNCE_MS = 300;
    private static final double DISTANCE_EXPORT_EPS = 0.05; // meters
    private static final double PLASH_POSITION_X_EXPORT_EPS = 0.02;
    private static final int ORAD_POSITION_EXPORT_HZ = 25;
    private static final int ORAD_POSITION_EXPORT_HZ_FAST = 50;
    private static final long ORAD_POSITION_BUFFER_DELAY_MS = 40;
    private static final long ORAD_POSITION_DUPLICATE_DELAY_MS = 10;
    private static final String ORAD_POS_MODE_BUFFERED_25HZ = "buffered_25hz";
    private static final String ORAD_POS_MODE_BUFFERED_50HZ = "buffered_50hz";
    private static final String ORAD_POS_MODE_BUFFERED_DELAY_40MS = "buffered_delay_40ms";
    private static final String ORAD_POS_MODE_RAW_DUPLICATE_10MS = "raw_duplicate_10ms";

    private final Label[] positionLabels = new Label[LANES_COUNT];
    private final Label[] distanceLabels = new Label[LANES_COUNT];
    private final Label[] gpLabels = new Label[LANES_COUNT];
    private final Label[] directionLabels = new Label[LANES_COUNT];

    private final double[] lastTotalDistance = new double[LANES_COUNT];
    private final double[] lastJsonXSegment = new double[LANES_COUNT];
    private final boolean[] hasLastJsonXSegment = new boolean[LANES_COUNT];
    /** Только ORAD Place_swimming Position_X — сырое x_m_pool из того же swimmer JSON. */
    private final double[] lastTcpXmPoolForOrad = new double[LANES_COUNT];
    private final boolean[] hasLastTcpXmPoolForOrad = new boolean[LANES_COUNT];
    private final String[] lastDirection = new String[LANES_COUNT];
    private final int[] displayedRanks = new int[LANES_COUNT];

    private final double[] lastExportedDistance = new double[LANES_COUNT];
    private final boolean[] hasExportedDistance = new boolean[LANES_COUNT];
    private final String[] lastExportedDirection = new String[LANES_COUNT];

    private long lastRankUpdateTs = 0;

    /** Экспорт топ-4 на Place_swimming: обновляется по TCP, без debounce рангов. */
    private volatile boolean leadersPlaceSwimmingTracking = false;

    private final int[] lastPlashVisibleSent = new int[LANES_COUNT];
    private final boolean[] lastPlashPosXInitialized = new boolean[LANES_COUNT];
    private final double[] lastPlashPosXSent = new double[LANES_COUNT];
    private final String[] lastPlashDelaySent = new String[LANES_COUNT];
    private final String[] lastPlashPlaceSent = new String[LANES_COUNT];
    private final String[] lastPlashChildSent = new String[LANES_COUNT];
    private final boolean[] pendingPlashPosXDirty = new boolean[LANES_COUNT];
    private final boolean[] pendingPlashPosXForce = new boolean[LANES_COUNT];
    private final double[] pendingPlashPosXValue = new double[LANES_COUNT];
    private final long[] pendingPlashPosXDueAtMs = new long[LANES_COUNT];

    private Socket tcpSocket;
    private Thread tcpThread;
    private volatile boolean tcpRunning = false;
    private final AtomicBoolean uiUpdateQueued = new AtomicBoolean(false);

    private final Pattern laneIdPattern = Pattern.compile("\"lane_id\":(\\d+)");
    private final Pattern xPattern = Pattern.compile("\"x_m\":([-0-9\\.Ee]+)");
    private final Pattern xPoolPattern = Pattern.compile("\"x_m_pool\":([-0-9\\.Ee]+)");
    private final Pattern directionPattern = Pattern.compile("\"direction\":\"(left|right)\"");
    private ServerSocket splitsTcpServerSocket;
    private Thread splitsTcpServerThread;
    private volatile boolean splitsTcpServerRunning = false;
    private final List<Socket> splitsTcpClients = new ArrayList<>();
    private final Object splitsStateLock = new Object();
    private boolean pendingSplitsJsonReset = false;
    private static final int SPLITS_TCP_BROADCAST_HZ = 25;
    private ScheduledExecutorService splitsTcpBroadcastExecutor;
    private ScheduledExecutorService placeSwimmingPosExportExecutor;
    private ScheduledExecutorService placeSwimmingPosDuplicateExecutor;

    @FXML
    private TextArea logArea;

    @FXML
    private TextField portField;

    @FXML
    private ComboBox<String> parityComboBox, flowControlComboBox, speedComboBox, dataBitsComboBox, stopBitsComboBox, encodingComboBox;

    @FXML private TextField tcpHostField;
    @FXML private TextField tcpPortField;
    @FXML private Button startTcpButton, stopTcpButton;
    @FXML private Label tcpConnectionLabel;
    @FXML private ImageView tcpConnectionImageView;
    @FXML private TextField splitsTcpServerPortField;
    @FXML private Button startSplitsTcpServerButton, stopSplitsTcpServerButton;
    @FXML private Label splitsTcpServerStatusLabel, splitsTcpClientsCountLabel;

    @FXML private ScrollPane scrollPaneSplits;

    @FXML private Button startSerialButton, stopSerialButton;

    @FXML private RadioButton radio25m, radio50m;
    @FXML public RadioButton radio50Distance, radio100Distance, radio200Distance, radio400Distance, radio800Distance, radio1500Distance;

    @FXML public RadioButton automaticDistanceButton, manualDistanceButton;

    @FXML public Label timerLabel;
    @FXML private Label connectionLabel;

    @FXML private ImageView connectionImageView;

    @FXML private AnchorPane oradControllerAnchorPane;

    @FXML private TextField reAddress, reCanvas;

    @FXML public TextField thirdPlaceText, secondPlaceText, firstPlaceText;

    @FXML private ToggleButton oradPlaceLeadersToggle;
    /** Слоты ORAD 001…010 получают данные с дорожек 9…0 вместо 0…9. */
    @FXML private CheckBox oradLaneReverseCheckBox;
    /** Инвертировать left/right от правила по числу сплитов (только ORAD). */
    @FXML private CheckBox oradLeadersDirectionReverseCheckBox;
    /** Режим отправки Position_X в ORAD. */
    @FXML private ComboBox<String> oradPosXModeComboBox;

    private OradController oradConnectionController;

    private String discon = "/pic/disconnected.png";
    private String con = "/pic/connected.png";

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

    private final ObservableList<String> encodingList = FXCollections.observableArrayList("UTF-8", "Windows-1251");
    private final ObservableList<String> oradPosXModeList = FXCollections.observableArrayList(
            "1) Буфер 25 Гц",
            "2) Буфер 50 Гц",
            "3) Буфер + задержка 40мс",
            "4) Сырое TCP + дубль 10мс"
    );

    private String serialPortName = "COM3";
    private int serialSpeed = 9600;
    private int serialDataBits = 8;
    private int serialStopBits = 1;
    private int serialParity = 0;
    private int serialFlowControl1 = 1;
    private int serialFlowControl2 = 2;




    @FXML
    private void initialize() {
        loadProperties();

        serialPort = new SerialPort(portField.getText());

        connectionImageView.setImage(new Image(getClass().getResourceAsStream(discon)));
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
        encodingComboBox.setItems(encodingList);

        // TCP defaults (can be overwritten by config.properties)
        tcpHostField.setText(properties.getProperty("tcpHost", "127.0.0.1"));
        tcpPortField.setText(properties.getProperty("tcpPort", "9000"));
        stopTcpButton.setDisable(true);
        startTcpButton.setDisable(false);
        tcpConnectionLabel.setText("TCP Disconnected");
        if (tcpConnectionImageView != null) {
            tcpConnectionImageView.setImage(new Image(getClass().getResourceAsStream(discon)));
        }

        tcpHostField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) setProperties("tcpHost", newValue);
        });
        tcpPortField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) return;
            String v = newValue.trim();
            if (v.isEmpty()) return;
            try {
                Integer.parseInt(v);
                setProperties("tcpPort", v);
            } catch (NumberFormatException ignored) {
            }
        });

        if (splitsTcpServerPortField != null) {
            splitsTcpServerPortField.setText(properties.getProperty("splitsTcpServerPort", "9100"));
            splitsTcpServerPortField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null) return;
                String v = newValue.trim();
                if (v.isEmpty()) return;
                try {
                    Integer.parseInt(v);
                    setProperties("splitsTcpServerPort", v);
                } catch (NumberFormatException ignored) {
                }
            });
        }
        if (startSplitsTcpServerButton != null && stopSplitsTcpServerButton != null) {
            startSplitsTcpServerButton.setDisable(false);
            stopSplitsTcpServerButton.setDisable(true);
        }
        updateSplitsTcpServerUi("Stopped");


        reAddress.textProperty().addListener((observable, oldValue, newValue) ->{
            setProperties("reAddress", newValue);
        });

        reCanvas.textProperty().addListener((observable, oldValue, newValue) ->{
            setProperties("reCanvas", newValue);
        });

        portField.textProperty().addListener((observable, oldValue, newValue) -> {
            serialPortName = newValue;
            setProperties("serialPort", newValue);
            serialPort = new SerialPort(newValue);
        });

        speedComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    serialSpeed = Integer.parseInt(newValue);
                    setProperties("serialSpeed", newValue);
                });


        dataBitsComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    serialDataBits = Integer.parseInt(newValue);
                    setProperties("serialDataBits", newValue);
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
                    setProperties("serialStopBits", newValue);
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
                    setProperties("serialParity", newValue);
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
                    setProperties("serialFlow", newValue);
                });
        distanceGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            RadioButton selectedBtn = (RadioButton) newValue;
            setDistance(selectedBtn);
        });

        encodingComboBox.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    setProperties("encoding", newValue);
                });

        Arrays.fill(lastPlashVisibleSent, -1);
        if (oradPlaceLeadersToggle != null) {
            oradPlaceLeadersToggle.selectedProperty().addListener((obs, prev, sel) ->
                    oradPlaceLeadersToggle.setText(Boolean.TRUE.equals(sel) ? "Убрать лидеров" : "Показать лидеров"));
        }
        if (oradLaneReverseCheckBox != null) {
            oradLaneReverseCheckBox.selectedProperty().addListener((o, p, n) -> refreshOradPlaceLeadersExportsIfTracking());
        }
        if (oradLeadersDirectionReverseCheckBox != null) {
            oradLeadersDirectionReverseCheckBox.selectedProperty().addListener((o, p, n) -> refreshOradPlaceLeadersExportsIfTracking());
        }
        if (oradPosXModeComboBox != null) {
            oradPosXModeComboBox.setItems(oradPosXModeList);
            String mode = properties.getProperty("oradPositionXMode", ORAD_POS_MODE_BUFFERED_25HZ);
            oradPosXModeComboBox.getSelectionModel().select(posXModeToUiLabel(mode));
            if (oradPosXModeComboBox.getSelectionModel().isEmpty()) {
                oradPosXModeComboBox.getSelectionModel().select(oradPosXModeList.get(0));
            }
            oradPosXModeComboBox.valueProperty().addListener((o, p, n) -> {
                String newMode = uiLabelToPosXMode(n);
                setProperties("oradPositionXMode", newMode);
                startPlaceSwimmingPosExportScheduler();
            });
        }

        startPlaceSwimmingPosExportScheduler();
        createGridPaneSplits();
    }


    @FXML
    private void startSerial() {

        try {
            // Открываем порт
            serialPort.openPort();
            // Настраиваем параметры порта
            serialPort.setParams(
                    serialSpeed,
                    serialDataBits,
                    serialStopBits,
                    serialParity);
            // Настраиваем flow control
            serialPort.setFlowControlMode(serialFlowControl1 |
                    serialFlowControl2);
            // Подписываемся на входящие данные
            serialPort.addEventListener(new PortReader(logArea, this), SerialPort.MASK_RXCHAR);
            // Обновляем UI при успешном открытии порта
            if (serialPort.isOpened()) {
                connectionImageView.setImage(new Image(getClass().getResourceAsStream(con)));
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
        stopTcp();
        stopSplitsTcpServer();
        stopPlaceSwimmingPosExportScheduler();
        stopPlaceSwimmingPosDuplicateScheduler();
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
                    connectionImageView.setImage(new Image(getClass().getResourceAsStream(discon)));
                }

            }
        } catch (SerialPortException e) {
            e.printStackTrace();
        }

    }

    @FXML
    private void startTcp() {
        if (tcpRunning) return;

        String host = tcpHostField.getText() == null ? "127.0.0.1" : tcpHostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(tcpPortField.getText().trim());
        } catch (Exception ex) {
            tcpConnectionLabel.setText("TCP bad port");
            return;
        }

        tcpRunning = true;
        startTcpButton.setDisable(true);
        stopTcpButton.setDisable(false);
        tcpConnectionLabel.setText("TCP Connecting...");
        if (tcpConnectionImageView != null) {
            tcpConnectionImageView.setImage(new Image(getClass().getResourceAsStream(discon)));
        }

        tcpThread = new Thread(() -> {
            try {
                tcpSocket = new Socket(host, port);
                tcpSocket.setSoTimeout(0);

                Platform.runLater(() -> {
                    tcpConnectionLabel.setText("TCP Connected");
                    if (tcpConnectionImageView != null) {
                        tcpConnectionImageView.setImage(new Image(getClass().getResourceAsStream(con)));
                    }
                });

                InputStream inputStream = tcpSocket.getInputStream();
                byte[] buffer = new byte[8192];
                StringBuilder streamBuffer = new StringBuilder();

                while (tcpRunning) {
                    int read = inputStream.read(buffer);
                    if (read < 0) break;
                    if (read == 0) continue;

                    streamBuffer.append(new String(buffer, 0, read, StandardCharsets.UTF_8));

                    // Extract complete JSON objects from the stream (naive brace-depth parser).
                    while (true) {
                        int startIdx = streamBuffer.indexOf("{");
                        if (startIdx < 0) break;

                        int depth = 0;
                        boolean foundEnd = false;
                        for (int i = startIdx; i < streamBuffer.length(); i++) {
                            char ch = streamBuffer.charAt(i);
                            if (ch == '{') depth++;
                            else if (ch == '}') depth--;
                            if (depth == 0) {
                                String jsonObj = streamBuffer.substring(startIdx, i + 1);
                                streamBuffer.delete(0, i + 1);
                                foundEnd = true;
                                handleTcpJson(jsonObj);
                                break;
                            }
                        }

                        if (!foundEnd) break;
                    }
                }
            } catch (Exception ex) {
                Platform.runLater(() -> tcpConnectionLabel.setText("TCP error: " + ex.getClass().getSimpleName()));
            } finally {
                try {
                    if (tcpSocket != null) tcpSocket.close();
                } catch (IOException ignored) {
                }
                tcpSocket = null;
                tcpRunning = false;
                Platform.runLater(() -> {
                    startTcpButton.setDisable(false);
                    stopTcpButton.setDisable(true);
                    tcpConnectionLabel.setText("TCP Disconnected");
                    if (tcpConnectionImageView != null) {
                        tcpConnectionImageView.setImage(new Image(getClass().getResourceAsStream(discon)));
                    }
                });
            }
        }, "tcp-json-reader");
        tcpThread.start();
    }

    @FXML
    private void stopTcp() {
        tcpRunning = false;
        if (tcpThread != null) {
            tcpThread.interrupt();
            tcpThread = null;
        }
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException ignored) {
            }
            tcpSocket = null;
        }

        if (startTcpButton != null && stopTcpButton != null) {
            startTcpButton.setDisable(false);
            stopTcpButton.setDisable(true);
        }
        if (tcpConnectionLabel != null) {
            tcpConnectionLabel.setText("TCP Disconnected");
        }
        if (tcpConnectionImageView != null) {
            tcpConnectionImageView.setImage(new Image(getClass().getResourceAsStream(discon)));
        }
    }

    @FXML
    private void startSplitsTcpServer() {
        if (splitsTcpServerRunning) return;
        int port;
        try {
            port = Integer.parseInt(splitsTcpServerPortField.getText().trim());
        } catch (Exception ex) {
            updateSplitsTcpServerUi("Bad port");
            return;
        }

        splitsTcpServerRunning = true;
        if (startSplitsTcpServerButton != null) startSplitsTcpServerButton.setDisable(true);
        if (stopSplitsTcpServerButton != null) stopSplitsTcpServerButton.setDisable(false);
        updateSplitsTcpServerUi("Starting...");

        splitsTcpServerThread = new Thread(() -> {
            try {
                splitsTcpServerSocket = new ServerSocket(port);
                Platform.runLater(this::startSplitsTcpBroadcastScheduler);
                updateSplitsTcpServerUi("Listening on " + port);
                while (splitsTcpServerRunning) {
                    Socket client = splitsTcpServerSocket.accept();
                    client.setTcpNoDelay(true);
                    synchronized (splitsTcpClients) {
                        splitsTcpClients.add(client);
                    }
                    updateSplitsTcpServerUi("Listening on " + port);
                }
            } catch (IOException ex) {
                if (splitsTcpServerRunning) {
                    updateSplitsTcpServerUi("Server error");
                }
            } finally {
                splitsTcpServerRunning = false;
                closeSplitsTcpServerResources();
                Platform.runLater(() -> {
                    if (startSplitsTcpServerButton != null) startSplitsTcpServerButton.setDisable(false);
                    if (stopSplitsTcpServerButton != null) stopSplitsTcpServerButton.setDisable(true);
                    if (splitsTcpServerStatusLabel != null && !"Server error".equals(splitsTcpServerStatusLabel.getText())) {
                        splitsTcpServerStatusLabel.setText("Stopped");
                    }
                    if (splitsTcpClientsCountLabel != null) splitsTcpClientsCountLabel.setText("0");
                });
            }
        }, "splits-tcp-server");
        splitsTcpServerThread.start();
    }

    @FXML
    private void stopSplitsTcpServer() {
        splitsTcpServerRunning = false;
        if (splitsTcpServerThread != null) {
            splitsTcpServerThread.interrupt();
            splitsTcpServerThread = null;
        }
        closeSplitsTcpServerResources();
        if (startSplitsTcpServerButton != null) startSplitsTcpServerButton.setDisable(false);
        if (stopSplitsTcpServerButton != null) stopSplitsTcpServerButton.setDisable(true);
        updateSplitsTcpServerUi("Stopped");
    }

    private void closeSplitsTcpServerResources() {
        stopSplitsTcpBroadcastScheduler();
        if (splitsTcpServerSocket != null) {
            try {
                splitsTcpServerSocket.close();
            } catch (IOException ignored) {
            }
            splitsTcpServerSocket = null;
        }
        synchronized (splitsTcpClients) {
            for (Socket client : splitsTcpClients) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
            splitsTcpClients.clear();
        }
    }

    private void updateSplitsTcpServerUi(String status) {
        Platform.runLater(() -> {
            if (splitsTcpServerStatusLabel != null) {
                splitsTcpServerStatusLabel.setText(status);
            }
            if (splitsTcpClientsCountLabel != null) {
                synchronized (splitsTcpClients) {
                    splitsTcpClientsCountLabel.setText(String.valueOf(splitsTcpClients.size()));
                }
            }
        });
    }

    private void broadcastSplitsAsJson() {
        String payload = buildSplitsJson() + "\n";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        synchronized (splitsTcpClients) {
            List<Socket> dead = new ArrayList<>();
            for (Socket client : splitsTcpClients) {
                try {
                    OutputStream os = client.getOutputStream();
                    os.write(bytes);
                    os.flush();
                } catch (IOException ex) {
                    dead.add(client);
                }
            }
            for (Socket client : dead) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
                splitsTcpClients.remove(client);
            }
        }
        updateSplitsTcpServerUi(splitsTcpServerRunning ? "Listening" : "Stopped");
    }

    /** Следующий JSON сплит-сервера будет с полем "reset": true (один раз). */
    public void notifySplitsTcpReset() {
        synchronized (splitsStateLock) {
            pendingSplitsJsonReset = true;
        }
        broadcastSplitsAsJson();
    }

    private void startSplitsTcpBroadcastScheduler() {
        if (!splitsTcpServerRunning) return;
        stopSplitsTcpBroadcastScheduler();
        splitsTcpBroadcastExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "splits-tcp-broadcast");
            t.setDaemon(true);
            return t;
        });
        long periodMs = Math.max(1L, 1000L / SPLITS_TCP_BROADCAST_HZ);
        splitsTcpBroadcastExecutor.scheduleAtFixedRate(() -> {
            if (!splitsTcpServerRunning) return;
            synchronized (splitsTcpClients) {
                if (splitsTcpClients.isEmpty()) return;
            }
            Platform.runLater(() -> {
                if (!splitsTcpServerRunning) return;
                broadcastSplitsAsJson();
            });
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopSplitsTcpBroadcastScheduler() {
        if (splitsTcpBroadcastExecutor == null) return;
        splitsTcpBroadcastExecutor.shutdown();
        try {
            if (!splitsTcpBroadcastExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                splitsTcpBroadcastExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            splitsTcpBroadcastExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        splitsTcpBroadcastExecutor = null;
    }

    /** Rank 1 = leader (max distance); ties broken by lower lane index. */
    private int[] computeRanksByTotalDistance() {
        Integer[] idx = new Integer[LANES_COUNT];
        for (int lane = 0; lane < LANES_COUNT; lane++) idx[lane] = lane;
        Arrays.sort(idx, (a, b) -> {
            int cmp = Double.compare(lastTotalDistance[b], lastTotalDistance[a]);
            if (cmp != 0) return cmp;
            return Integer.compare(a, b);
        });
        int[] ranks = new int[LANES_COUNT];
        for (int rank = 1; rank <= LANES_COUNT; rank++) {
            ranks[idx[rank - 1]] = rank;
        }
        return ranks;
    }

    private static String plashGroupSuffix(int laneZeroBased) {
        return String.format("%03d", laneZeroBased + 1);
    }

    private void resetPlaceSwimmingLeaderExportCacheLocked() {
        Arrays.fill(lastPlashVisibleSent, -1);
        Arrays.fill(lastPlashPosXInitialized, false);
        Arrays.fill(lastPlashDelaySent, null);
        Arrays.fill(lastPlashPlaceSent, null);
        Arrays.fill(lastPlashChildSent, null);
        Arrays.fill(pendingPlashPosXDirty, false);
        Arrays.fill(pendingPlashPosXForce, false);
        Arrays.fill(pendingPlashPosXDueAtMs, 0L);
    }

    /** Кладет последнее Position_X в буфер; отправка идет отдельным тикером 25 Гц. */
    private void queuePlaceSwimmingPosXExportLocked(int oradSlot, double posX, boolean forceResend) {
        long now = System.currentTimeMillis();
        pendingPlashPosXValue[oradSlot] = posX;
        pendingPlashPosXDirty[oradSlot] = true;
        if (isPosXModeBufferedDelay40ms()) {
            if (pendingPlashPosXDueAtMs[oradSlot] == 0L || forceResend) {
                pendingPlashPosXDueAtMs[oradSlot] = now + ORAD_POSITION_BUFFER_DELAY_MS;
            }
        } else {
            pendingPlashPosXDueAtMs[oradSlot] = 0L;
        }
        if (forceResend) {
            pendingPlashPosXForce[oradSlot] = true;
        }
    }

    private void flushQueuedPlaceSwimmingPosXExports() {
        synchronized (splitsStateLock) {
            if (controller == null || !leadersPlaceSwimmingTracking) {
                return;
            }
            if (isPosXModeRawDuplicate10ms()) {
                return;
            }
            long now = System.currentTimeMillis();
            for (int oradSlot = 0; oradSlot < LANES_COUNT; oradSlot++) {
                if (!pendingPlashPosXDirty[oradSlot]) {
                    continue;
                }
                if (isPosXModeBufferedDelay40ms() && now < pendingPlashPosXDueAtMs[oradSlot]) {
                    continue;
                }
                double posX = pendingPlashPosXValue[oradSlot];
                boolean forceResend = pendingPlashPosXForce[oradSlot];
                boolean needPos = forceResend
                        || !lastPlashPosXInitialized[oradSlot]
                        || Math.abs(posX - lastPlashPosXSent[oradSlot]) > PLASH_POSITION_X_EXPORT_EPS;
                if (needPos) {
                    String suf = plashGroupSuffix(oradSlot);
                    String exportName = "Transformation_Plash-Group-" + suf + "_Position_X";
                    String exportValue = String.format(Locale.US, "%.6f", posX);
                    controller.sendSetExport(PLACE_SWIMMING_SCENE, exportName, exportValue);
                    lastPlashPosXSent[oradSlot] = posX;
                    lastPlashPosXInitialized[oradSlot] = true;
                }
                pendingPlashPosXDirty[oradSlot] = false;
                pendingPlashPosXForce[oradSlot] = false;
                pendingPlashPosXDueAtMs[oradSlot] = 0L;
            }
        }
    }

    private void startPlaceSwimmingPosExportScheduler() {
        stopPlaceSwimmingPosExportScheduler();
        startPlaceSwimmingPosDuplicateScheduler();
        placeSwimmingPosExportExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orad-place-swimming-pos-export");
            t.setDaemon(true);
            return t;
        });
        long periodMs = getBufferedPosXSchedulerPeriodMs();
        placeSwimmingPosExportExecutor.scheduleAtFixedRate(this::flushQueuedPlaceSwimmingPosXExports,
                0, periodMs, TimeUnit.MILLISECONDS);
    }

    private long getBufferedPosXSchedulerPeriodMs() {
        int hz = isPosXModeBuffered50hz() ? ORAD_POSITION_EXPORT_HZ_FAST : ORAD_POSITION_EXPORT_HZ;
        return Math.max(1L, 1000L / hz);
    }

    private void stopPlaceSwimmingPosExportScheduler() {
        if (placeSwimmingPosExportExecutor == null) return;
        placeSwimmingPosExportExecutor.shutdown();
        try {
            if (!placeSwimmingPosExportExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                placeSwimmingPosExportExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            placeSwimmingPosExportExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        placeSwimmingPosExportExecutor = null;
    }

    private void startPlaceSwimmingPosDuplicateScheduler() {
        stopPlaceSwimmingPosDuplicateScheduler();
        placeSwimmingPosDuplicateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "orad-place-swimming-pos-duplicate");
            t.setDaemon(true);
            return t;
        });
    }

    private void stopPlaceSwimmingPosDuplicateScheduler() {
        if (placeSwimmingPosDuplicateExecutor == null) return;
        placeSwimmingPosDuplicateExecutor.shutdown();
        try {
            if (!placeSwimmingPosDuplicateExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                placeSwimmingPosDuplicateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            placeSwimmingPosDuplicateExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        placeSwimmingPosDuplicateExecutor = null;
    }

    private void scheduleDuplicatePositionXExport(String scene, String exportName, String exportValue) {
        ScheduledExecutorService duplicateExecutor = placeSwimmingPosDuplicateExecutor;
        if (duplicateExecutor == null) {
            return;
        }
        duplicateExecutor.schedule(() -> {
            Retalk2ConnectionController currentController = controller;
            if (currentController == null || !leadersPlaceSwimmingTracking) {
                return;
            }
            currentController.sendSetExport(scene, exportName, exportValue);
        }, ORAD_POSITION_DUPLICATE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Тестовый режим: немедленно отправляем сырое x_m_pool из TCP + дубль через 10мс.
     * Работает независимо от буферного 25Гц режима.
     */
    private void sendPlaceSwimmingPosXRawFromTcpLocked(boolean[] havePoolFrame, double[] frameXmPool) {
        if (controller == null || !leadersPlaceSwimmingTracking) {
            return;
        }
        if (!isPosXModeRawDuplicate10ms()) {
            return;
        }
        boolean reverseLanes = oradLaneReverseCheckBox != null && oradLaneReverseCheckBox.isSelected();
        for (int oradSlot = 0; oradSlot < LANES_COUNT; oradSlot++) {
            int srcLane = mapOradSlotToSourceLane(oradSlot, reverseLanes);
            if (!havePoolFrame[srcLane]) {
                continue;
            }
            String suf = plashGroupSuffix(oradSlot);
            String exportName = "Transformation_Plash-Group-" + suf + "_Position_X";
            String exportValue = String.format(Locale.US, "%.6f", frameXmPool[srcLane]);
            controller.sendSetExport(PLACE_SWIMMING_SCENE, exportName, exportValue);
            scheduleDuplicatePositionXExport(PLACE_SWIMMING_SCENE, exportName, exportValue);
            lastPlashPosXSent[oradSlot] = frameXmPool[srcLane];
            lastPlashPosXInitialized[oradSlot] = true;
        }
    }

    private boolean isPosXModeRawDuplicate10ms() {
        return ORAD_POS_MODE_RAW_DUPLICATE_10MS.equals(getSelectedPosXMode());
    }

    private boolean isPosXModeBufferedDelay40ms() {
        return ORAD_POS_MODE_BUFFERED_DELAY_40MS.equals(getSelectedPosXMode());
    }

    private boolean isPosXModeBuffered50hz() {
        return ORAD_POS_MODE_BUFFERED_50HZ.equals(getSelectedPosXMode());
    }

    private String getSelectedPosXMode() {
        if (oradPosXModeComboBox == null) {
            return ORAD_POS_MODE_BUFFERED_25HZ;
        }
        String v = oradPosXModeComboBox.getValue();
        return uiLabelToPosXMode(v);
    }

    private String posXModeToUiLabel(String mode) {
        if (ORAD_POS_MODE_BUFFERED_50HZ.equals(mode)) {
            return oradPosXModeList.get(1);
        }
        if (ORAD_POS_MODE_BUFFERED_DELAY_40MS.equals(mode)) {
            return oradPosXModeList.get(2);
        }
        if (ORAD_POS_MODE_RAW_DUPLICATE_10MS.equals(mode)) {
            return oradPosXModeList.get(3);
        }
        return oradPosXModeList.get(0);
    }

    private String uiLabelToPosXMode(String label) {
        if (label == null) {
            return ORAD_POS_MODE_BUFFERED_25HZ;
        }
        if (label.startsWith("2)")) {
            return ORAD_POS_MODE_BUFFERED_50HZ;
        }
        if (label.startsWith("3)")) {
            return ORAD_POS_MODE_BUFFERED_DELAY_40MS;
        }
        if (label.startsWith("4)")) {
            return ORAD_POS_MODE_RAW_DUPLICATE_10MS;
        }
        return ORAD_POS_MODE_BUFFERED_25HZ;
    }

    private void sendPlaceSwimmingAllGroupMasterVisible(boolean visible) {
        if (controller == null) {
            return;
        }
        controller.sendSetExport(PLACE_SWIMMING_SCENE, PLACE_SWIMMING_ALL_GROUP_VISIBLE, visible ? "1" : "0");
    }

    private void refreshOradPlaceLeadersExportsIfTracking() {
        if (!leadersPlaceSwimmingTracking) {
            return;
        }
        synchronized (splitsStateLock) {
            resetPlaceSwimmingLeaderExportCacheLocked();
            applyPlaceSwimmingLeaderExportsLocked(true);
        }
    }

    private static int countFilledSplits(Participant p) {
        if (p == null) {
            return 0;
        }
        int n = 0;
        for (Split s : p.getSplits()) {
            if (s.getSpl() != null && !s.getSpl().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Направление плашки в ORAD: 0 и чётное число заполненных сплитов — left (1),
     * нечётное — right (2). «РЕВЕРС ЛИДЕРОВ» инвертирует.
     */
    private String oradChildIndexFromSplitsForLane(int lane, boolean reverseLeadersDir) {
        int n = countFilledSplits(participants.size() > lane ? participants.get(lane) : null);
        boolean left = (n % 2 == 0);
        if (reverseLeadersDir) {
            left = !left;
        }
        return left ? "1" : "2";
    }

    /** Слот экспорта ORAD 0..9 → индекс дорожки-источника данных. */
    private int mapOradSlotToSourceLane(int oradSlot, boolean reverseLanes) {
        return reverseLanes ? (LANES_COUNT - 1 - oradSlot) : oradSlot;
    }

    /** Топ-4 по пройденной дистанции; при равенстве — меньший индекс дорожки выше. */
    private int[] computeTopFourLanesByDistanceLocked() {
        Integer[] idx = new Integer[LANES_COUNT];
        for (int lane = 0; lane < LANES_COUNT; lane++) {
            idx[lane] = lane;
        }
        Arrays.sort(idx, (a, b) -> {
            int cmp = Double.compare(lastTotalDistance[b], lastTotalDistance[a]);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a, b);
        });
        return new int[]{idx[0], idx[1], idx[2], idx[3]};
    }

    private static String formatGapBehindLeaderRu(double metersBehind) {
        return String.format(Locale.GERMANY, "%+.1f", metersBehind) + "м";
    }

    /**
     * Плашки лидеров на {@link #PLACE_SWIMMING_SCENE}. Удерживать {@link #splitsStateLock}.
     * Расчёт топ-4 по программным дорожкам 0..9; в ORAD слоты 001..010 при «РЕВЕРС ДОРОЖЕК»
     * получают данные с дорожек в обратном порядке. Направление плашки — по сплитам (см. чекбокс реверса).
     */
    private void applyPlaceSwimmingLeaderExportsLocked(boolean forceResend) {
        if (controller == null || !leadersPlaceSwimmingTracking) {
            return;
        }
        boolean reverseLanes = oradLaneReverseCheckBox != null && oradLaneReverseCheckBox.isSelected();
        boolean reverseLeadersDir = oradLeadersDirectionReverseCheckBox != null
                && oradLeadersDirectionReverseCheckBox.isSelected();

        int[] top4 = computeTopFourLanesByDistanceLocked();
        int leaderLane = top4[0];
        double leaderDist = lastTotalDistance[leaderLane];
        boolean[] inTop4 = new boolean[LANES_COUNT];
        for (int lane : top4) {
            inTop4[lane] = true;
        }

        for (int oradSlot = 0; oradSlot < LANES_COUNT; oradSlot++) {
            int srcLane = mapOradSlotToSourceLane(oradSlot, reverseLanes);
            String suf = plashGroupSuffix(oradSlot);

            int visible = inTop4[srcLane] ? 1 : 0;
            if (forceResend || lastPlashVisibleSent[oradSlot] != visible) {
                controller.sendSetExport(PLACE_SWIMMING_SCENE,
                        "Object_Plash-Group-" + suf + "_Object_Visible", String.valueOf(visible));
                lastPlashVisibleSent[oradSlot] = visible;
            }

            double posX = hasLastTcpXmPoolForOrad[srcLane] ? lastTcpXmPoolForOrad[srcLane] : 0.0;
            if (!isPosXModeRawDuplicate10ms()) {
                queuePlaceSwimmingPosXExportLocked(oradSlot, posX, forceResend);
            }

            if (inTop4[srcLane]) {
                int placeInFour = 0;
                for (int i = 0; i < 4; i++) {
                    if (top4[i] == srcLane) {
                        placeInFour = i + 1;
                        break;
                    }
                }
                String placeStr = String.valueOf(placeInFour);
                if (forceResend || !placeStr.equals(lastPlashPlaceSent[oradSlot])) {
                    controller.sendSetExport(PLACE_SWIMMING_SCENE,
                            "Geometry_Place-R-" + suf + "_Input_String", placeStr);
                    lastPlashPlaceSent[oradSlot] = placeStr;
                }

                String delay = (srcLane == leaderLane) ? "ЛИДЕР"
                        : formatGapBehindLeaderRu(leaderDist - lastTotalDistance[srcLane]);
                String child = oradChildIndexFromSplitsForLane(srcLane, reverseLeadersDir);
                if (forceResend || !delay.equals(lastPlashDelaySent[oradSlot])) {
                    controller.sendSetExport(PLACE_SWIMMING_SCENE,
                            "Geometry_Delay-R-" + suf + "_Input_String", delay);
                    lastPlashDelaySent[oradSlot] = delay;
                }
                if (forceResend || !child.equals(lastPlashChildSent[oradSlot])) {
                    controller.sendSetExport(PLACE_SWIMMING_SCENE,
                            "Object_Plash-Group-" + suf + "_C_Enable_DrawingChildIndex", child);
                    lastPlashChildSent[oradSlot] = child;
                }
            } else {
                lastPlashDelaySent[oradSlot] = null;
                lastPlashPlaceSent[oradSlot] = null;
                lastPlashChildSent[oradSlot] = null;
            }
        }
    }

    private void sendAllPlaceSwimmingPlaquesHidden() {
        if (controller == null) {
            return;
        }
        sendPlaceSwimmingAllGroupMasterVisible(false);
        for (int oradSlot = 0; oradSlot < LANES_COUNT; oradSlot++) {
            String suf = plashGroupSuffix(oradSlot);
            controller.sendSetExport(PLACE_SWIMMING_SCENE,
                    "Object_Plash-Group-" + suf + "_Object_Visible", "0");
        }
    }

    @FXML
    private void onOradPlaceLeadersToggle() {
        if (oradPlaceLeadersToggle == null) {
            return;
        }
        boolean on = oradPlaceLeadersToggle.isSelected();
        leadersPlaceSwimmingTracking = on;
        if (!on) {
            sendAllPlaceSwimmingPlaquesHidden();
            synchronized (splitsStateLock) {
                resetPlaceSwimmingLeaderExportCacheLocked();
            }
        } else {
            if (controller != null) {
                sendPlaceSwimmingAllGroupMasterVisible(true);
            }
            synchronized (splitsStateLock) {
                resetPlaceSwimmingLeaderExportCacheLocked();
                applyPlaceSwimmingLeaderExportsLocked(true);
            }
        }
    }

    private String buildSplitsJson() {
        synchronized (splitsStateLock) {
            boolean resetOut = pendingSplitsJsonReset;
            pendingSplitsJsonReset = false;

            double leaderDist = 0;
            for (int l = 0; l < LANES_COUNT; l++) {
                leaderDist = Math.max(leaderDist, lastTotalDistance[l]);
            }
            int[] ranks = computeRanksByTotalDistance();

            StringBuilder sb = new StringBuilder();
            sb.append("{\"reset\":").append(resetOut ? "true" : "false");
            for (int lane = 0; lane < participants.size(); lane++) {
                Participant p = participants.get(lane);
                sb.append(",\"").append(lane).append("\":{");
                sb.append("\"splits\":[");

                List<Split> laneSplits = p.getSplits();
                boolean first = true;
                boolean laneFinished = false;
                if (!laneSplits.isEmpty()) {
                    String lastTime = laneSplits.get(laneSplits.size() - 1).getSpl();
                    laneFinished = lastTime != null && !lastTime.isEmpty();
                }

                for (int i = 0; i < laneSplits.size(); i++) {
                    String t = laneSplits.get(i).getSpl();
                    if (t == null) t = "";

                    if (!first) sb.append(",");
                    first = false;

                    boolean isFinal = i == laneSplits.size() - 1 && laneFinished;
                    sb.append("{\"time\":\"")
                            .append(escapeJson(t))
                            .append("\",\"final\":")
                            .append(isFinal ? "true" : "false")
                            .append("}");
                }
                sb.append("]");
                double gp = leaderDist - lastTotalDistance[lane];
                sb.append(",\"line_pos\":").append(ranks[lane])
                        .append(",\"line_real_x\":")
                        .append(String.format(Locale.US, "%.2f", lastTotalDistance[lane]))
                        .append(",\"line_gp\":")
                        .append(String.format(Locale.US, "%.2f", gp))
                        .append("}");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void handleTcpJson(String jsonObj) {
        // Remove spaces/new lines as requested (this also keeps parsing simpler).
        String cleaned = jsonObj.replaceAll("[\\s\\r\\n\\t]+", "");

        boolean[] seenLane = new boolean[LANES_COUNT];
        double[] frameX = new double[LANES_COUNT];
        boolean[] havePoolFrame = new boolean[LANES_COUNT];
        double[] frameXmPool = new double[LANES_COUNT];
        String[] frameDirections = new String[LANES_COUNT];

        int swimmersStart = cleaned.indexOf("\"swimmers\":[");
        if (swimmersStart < 0) return;

        int i = swimmersStart + "\"swimmers\":[".length();
        while (i < cleaned.length()) {
            char c = cleaned.charAt(i);
            if (c == '{') {
                int braceDepth = 0;
                int startObj = i;
                while (i < cleaned.length()) {
                    char ch = cleaned.charAt(i);
                    if (ch == '{') braceDepth++;
                    else if (ch == '}') braceDepth--;
                    i++;
                    if (braceDepth == 0) {
                        String swimmerObj = cleaned.substring(startObj, i);

                        Integer laneId = extractInt(laneIdPattern, swimmerObj);
                        Double xVal = extractDouble(xPattern, swimmerObj);
                        Double xPool = extractDouble(xPoolPattern, swimmerObj);

                        if (laneId != null && laneId >= 0 && laneId < LANES_COUNT && xPool != null) {
                            havePoolFrame[laneId] = true;
                            frameXmPool[laneId] = xPool;
                        }
                        if (laneId != null && laneId >= 0 && laneId < LANES_COUNT && xVal != null) {
                            seenLane[laneId] = true;
                            frameX[laneId] = xVal;
                            frameDirections[laneId] = extractString(directionPattern, swimmerObj);
                        }
                        break;
                    }
                }
            } else if (c == ']') {
                break;
            } else {
                i++;
            }
        }

        long now = System.currentTimeMillis();

        synchronized (splitsStateLock) {
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (havePoolFrame[lane]) {
                    lastTcpXmPoolForOrad[lane] = frameXmPool[lane];
                    hasLastTcpXmPoolForOrad[lane] = true;
                }
            }
            sendPlaceSwimmingPosXRawFromTcpLocked(havePoolFrame, frameXmPool);

            // Update state for lanes present in this frame (дистанция и UI по x_m).
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (!seenLane[lane]) continue;

                double xSeg = frameX[lane];
                String dir = frameDirections[lane];

                if (dir == null) {
                    if (hasLastJsonXSegment[lane]) {
                        dir = xSeg >= lastJsonXSegment[lane] ? "right" : "left";
                    } else {
                        dir = lastDirection[lane];
                    }
                }

                lastJsonXSegment[lane] = xSeg;
                hasLastJsonXSegment[lane] = true;
                lastDirection[lane] = dir;

                Participant p = participants.size() > lane ? participants.get(lane) : null;
                // "Пройденные сплиты" = сколько полей в таблице заполнено (а не только индекс splitCount).
                int passedSplits = 0;
                if (p != null) {
                    for (Split s : p.getSplits()) {
                        if (s.getSpl() != null && !s.getSpl().isEmpty()) {
                            passedSplits++;
                        }
                    }
                }

                // x_m интерпретируем как позицию внутри текущего отрезка бассейна [0..poolLength].
                double segLen = swimPoolSize;
                double segPos = Math.max(0, Math.min(segLen, xSeg));

                // Если количество сплитов чётное (включая 0) — считаем "по ходу" x_m.
                // Если нечётное — пловец уже на обратном отрезке, поэтому добавляем (poolLen - x_m).
                double base = passedSplits * segLen;
                double total = (passedSplits % 2 == 0) ? (base + segPos) : (base + (segLen - segPos));

                total = Math.max(0, Math.min(distance, total)); // avoid overshoot outside selected race distance
                lastTotalDistance[lane] = total;
            }

            int[] newRanks = computeRanksByTotalDistance();

            boolean rankChanged = false;
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (newRanks[lane] != displayedRanks[lane]) {
                    rankChanged = true;
                    break;
                }
            }

            boolean allowRankUpdate = rankChanged && (now - lastRankUpdateTs >= POSITION_DEBOUNCE_MS);
            if (allowRankUpdate) {
                System.arraycopy(newRanks, 0, displayedRanks, 0, LANES_COUNT);
                lastRankUpdateTs = now;
            }

            // Send exports to RE (if connected).
            if (controller != null) {
                for (int lane = 0; lane < LANES_COUNT; lane++) {
                    String dir = lastDirection[lane] == null ? "" : lastDirection[lane];
                    String distValue = String.format(Locale.US, "%.3f", lastTotalDistance[lane]);

                    boolean needSendDist = !hasExportedDistance[lane] || Math.abs(lastTotalDistance[lane] - lastExportedDistance[lane]) > DISTANCE_EXPORT_EPS;
                    boolean needSendDir = lastExportedDirection[lane] == null ? !dir.isEmpty() : !lastExportedDirection[lane].equals(dir);

                    if (needSendDist) {
                        controller.sendSetExport(RE_SCENE, "distance_lane_" + lane, distValue);
                        lastExportedDistance[lane] = lastTotalDistance[lane];
                        hasExportedDistance[lane] = true;
                    }

                    if (needSendDir) {
                        controller.sendSetExport(RE_SCENE, "directin_lane_" + lane, dir);
                        lastExportedDirection[lane] = dir;
                    }

                    if (allowRankUpdate) {
                        controller.sendSetExport(RE_SCENE, "position_lane_" + lane, String.valueOf(displayedRanks[lane]));
                    }
                }
                if (leadersPlaceSwimmingTracking) {
                    applyPlaceSwimmingLeaderExportsLocked(false);
                }
            }
        }

        // Update UI (coalesced to avoid spamming Platform.runLater).
        if (uiUpdateQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                try {
                    updateSplitUiFromState();
                } finally {
                    uiUpdateQueued.set(false);
                }
            });
        }
    }

    private void updateSplitUiFromState() {
        synchronized (splitsStateLock) {
            double leaderDist = 0;
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                leaderDist = Math.max(leaderDist, lastTotalDistance[lane]);
            }
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (positionLabels[lane] != null) {
                    int rank = displayedRanks[lane];
                    positionLabels[lane].setText(rank > 0 ? String.valueOf(rank) : "");
                }
                if (distanceLabels[lane] != null) {
                    distanceLabels[lane].setText(String.format(Locale.US, "%.2f", lastTotalDistance[lane]));
                }
                if (gpLabels[lane] != null) {
                    double gp = leaderDist - lastTotalDistance[lane];
                    gpLabels[lane].setText(String.format(Locale.US, "%.2f", gp));
                }
                if (directionLabels[lane] != null) {
                    directionLabels[lane].setText(lastDirection[lane] == null ? "" : lastDirection[lane]);
                }
            }
        }
    }

    private Integer extractInt(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) return Integer.parseInt(m.group(1));
        return null;
    }

    private Double extractDouble(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) return Double.parseDouble(m.group(1));
        return null;
    }

    private String extractString(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) return m.group(1);
        return null;
    }

// Настройка размера бассейна
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

        synchronized (splitsStateLock) {
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                positionLabels[lane] = null;
                distanceLabels[lane] = null;
                gpLabels[lane] = null;
                directionLabels[lane] = null;

                lastTotalDistance[lane] = 0;
                lastJsonXSegment[lane] = 0;
                hasLastJsonXSegment[lane] = false;
                lastTcpXmPoolForOrad[lane] = 0;
                hasLastTcpXmPoolForOrad[lane] = false;
                lastDirection[lane] = null;

                displayedRanks[lane] = 0;
                lastExportedDistance[lane] = 0;
                hasExportedDistance[lane] = false;
                lastExportedDirection[lane] = null;
            }
            lastRankUpdateTs = 0;
            resetPlaceSwimmingLeaderExportCacheLocked();
        }

        firstPlaceText.clear();
        secondPlaceText.clear();
        thirdPlaceText.clear();

        int columns = distance / swimPoolSize;
        Label nameLabel = new Label("Имя");
        Label laneLabel = new Label("Дорожка");
        GridPane.setHalignment(nameLabel, HPos.CENTER);
        splits.add(nameLabel, 1, 0);
        splits.add(laneLabel, 0, 0);

        // Extra columns between athlete names and split columns.
        int positionCol = 2;
        int distanceCol = 3;
        int gpCol = 4;
        int directionCol = 5;
        int splitStartColumn = 6;

        Label positionHeader = new Label("Pos");
        Label distanceHeader = new Label("Dist (m)");
        Label gpHeader = new Label("GP");
        Label directionHeader = new Label("Dir");
        GridPane.setHalignment(positionHeader, HPos.CENTER);
        GridPane.setHalignment(distanceHeader, HPos.CENTER);
        GridPane.setHalignment(gpHeader, HPos.CENTER);
        GridPane.setHalignment(directionHeader, HPos.CENTER);
        splits.add(positionHeader, positionCol, 0);
        splits.add(distanceHeader, distanceCol, 0);
        splits.add(gpHeader, gpCol, 0);
        splits.add(directionHeader, directionCol, 0);

        for (int splitIdx = 0; splitIdx < columns; splitIdx++) {
            int uiCol = splitStartColumn + splitIdx;
            Label newDistanceLabel = new Label((splitIdx + 1) * swimPoolSize + "м");
            GridPane.setHalignment(newDistanceLabel, HPos.CENTER);
            splits.add(newDistanceLabel, uiCol, 0);
        }

        for (int row = 1; row < 11; row++) {
            Participant newParticipant = new Participant();
            newParticipant.setName(row + " " + "Пловец");
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

            for (int splitIdx = 0; splitIdx < columns; splitIdx++) {
                TextField newTextField = new TextField();
                newTextField.setPrefWidth(70);
                int finalRow = row;
                int finalSplitIdx = splitIdx;
                int uiCol = splitStartColumn + splitIdx;
                participants.get(finalRow - 1).getSplits().add(finalSplitIdx, new Split());
                newTextField.setText(participants.get(finalRow - 1).getSplits().get(finalSplitIdx).getSpl());
                newTextField.textProperty().addListener((observable, oldValue, newValue) ->
                        participants.get(finalRow - 1).getSplits().get(finalSplitIdx).setSpl(newValue));
                participants.get(finalRow - 1).getSplits().get(finalSplitIdx).splProperty().addListener(
                        (observable, oldValue, newValue) -> {
                            if (oldValue.equals("")) {
                                newTextField.setText(newValue);
                                if (participants.get(finalRow - 1).getSplitCount() < participants.get(finalRow - 1).getSplits().size() - 1) {
                                    participants.get(finalRow - 1).setSplitCount(participants.get(finalRow - 1).getSplitCount()+1);
                                }

                            }

                        });
                splits.add(newTextField, uiCol, row);

            }

            int laneIdx = row - 1;
            positionLabels[laneIdx] = new Label("");
            distanceLabels[laneIdx] = new Label("");
            gpLabels[laneIdx] = new Label("");
            directionLabels[laneIdx] = new Label("");

            GridPane.setHalignment(positionLabels[laneIdx], HPos.CENTER);
            GridPane.setHalignment(distanceLabels[laneIdx], HPos.CENTER);
            GridPane.setHalignment(gpLabels[laneIdx], HPos.CENTER);
            GridPane.setHalignment(directionLabels[laneIdx], HPos.CENTER);

            splits.add(positionLabels[laneIdx], positionCol, row);
            splits.add(distanceLabels[laneIdx], distanceCol, row);
            splits.add(gpLabels[laneIdx], gpCol, row);
            splits.add(directionLabels[laneIdx], directionCol, row);
        }
        scrollPaneSplits.setContent(splits);

        if (controller != null && leadersPlaceSwimmingTracking) {
            sendPlaceSwimmingAllGroupMasterVisible(true);
            synchronized (splitsStateLock) {
                resetPlaceSwimmingLeaderExportCacheLocked();
                applyPlaceSwimmingLeaderExportsLocked(true);
            }
        }
    }

    public void setOradController(Retalk2ConnectionController controller) {
        this.controller = controller;
        if (controller != null && leadersPlaceSwimmingTracking) {
            sendPlaceSwimmingAllGroupMasterVisible(true);
            synchronized (splitsStateLock) {
                resetPlaceSwimmingLeaderExportCacheLocked();
                applyPlaceSwimmingLeaderExportsLocked(true);
            }
        }
    }

    public Retalk2ConnectionController getController() {
        return controller;
    }

    @FXML private void hideLeaders() {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).isShowed()) {
                controller.sendAnimationPlay("Olympic/swimming", "swimmer_out_" + (i + 1));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        firstPlaceText.clear();
        secondPlaceText.clear();
        thirdPlaceText.clear();
    }

    @FXML private void hideAll() {
        for (int i = 0; i < 10; i++) {
            controller.sendAnimationPlay("Olympic/swimming", "swimmer_out_" + (i + 1));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        firstPlaceText.clear();
        secondPlaceText.clear();
        thirdPlaceText.clear();
    }



    @FXML private void hideRecordLine() {
        controller.sendAnimationPlay("Olympic/swimming", "WR_out");
    }

    @FXML private void showRecordLine(){
        controller.sendAnimationPlay("Olympic/swimming", "WR_in");
    }


    private void loadProperties() {
        try (InputStream input = new FileInputStream("config.properties")) {

            properties.load(input);

            //RESET
            reAddress.setText(properties.getProperty(""));
            reCanvas.setText(properties.getProperty(""));

            portField.setText(properties.getProperty(""));
            speedComboBox.getSelectionModel().select(properties.getProperty(""));
            dataBitsComboBox.getSelectionModel().select(properties.getProperty(""));
            stopBitsComboBox.getSelectionModel().select(properties.getProperty(""));
            parityComboBox.getSelectionModel().select(properties.getProperty(""));
            flowControlComboBox.getSelectionModel().select(properties.getProperty(""));
            encodingComboBox.getSelectionModel().select(properties.getProperty(""));



            //LOAD
            reAddress.setText(properties.getProperty("reAddress"));
            reCanvas.setText(properties.getProperty("reCanvas"));

            portField.setText(properties.getProperty("serialPort"));
            speedComboBox.getSelectionModel().select(properties.getProperty("serialSpeed"));
            dataBitsComboBox.getSelectionModel().select(properties.getProperty("serialDataBits"));
            stopBitsComboBox.getSelectionModel().select(properties.getProperty("serialStopBits"));
            parityComboBox.getSelectionModel().select(properties.getProperty("serialParity"));
            flowControlComboBox.getSelectionModel().select(properties.getProperty("serialFlow"));
            encodingComboBox.getSelectionModel().select(properties.getProperty("encoding"));


        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void setProperties(String key, String value) {
        properties.setProperty(key, value);
        writeProperties();
    }

    private void writeProperties() {
        try (OutputStream output = new FileOutputStream("config.properties")) {

            properties.store(output, null);

        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    // Decode incoming bytes from Serial:
    // - if they are valid UTF-8 => decode as UTF-8
    // - otherwise => fallback to Windows-1251
    //
    // This avoids mojibake like "Р‘РµСЂСѓ..." when the device sends UTF-8 bytes.
    private String decodeAutoCyrillic(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception ignored) {
            return new String(bytes, Charset.forName("Windows-1251"));
        }
    }

    private class PortReader implements SerialPortEventListener {
        TextArea logArea;
        String filePath;
        RootLayoutController rootLayoutController;


        public PortReader(TextArea logArea, RootLayoutController rootLayoutController) {
            this.logArea = logArea;
            this.rootLayoutController = rootLayoutController;
        }

        public void serialEvent(SerialPortEvent event) {

            if(event.isRXCHAR() && event.getEventValue() > 0){
                try {
                    byte[] bytes = serialPort.readBytes(event.getEventValue());
                    //String data = serialPort.readString(event.getEventValue());
                    String data = decodeAutoCyrillic(bytes);


                    new Thread(() -> Platform.runLater(() -> logArea.setText(data))).start();
                    new Thread(() -> Platform.runLater(() -> dataReader.parseData(data))).start();

                }
                catch (SerialPortException ex) {
                    ex.printStackTrace();
                }
            }
        }

    }

}
