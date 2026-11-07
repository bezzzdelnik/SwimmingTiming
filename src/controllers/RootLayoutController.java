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
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RootLayoutController {
    private static final String CONFIG_PROPERTIES_FILE = "config.properties";
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
    private static final String RE_NUMBERS_MODE = "_numbers_mode";
    private static final double ORAD_NUMBERS_MODE_TIMER_MIN = 15.0;
    private static final double ORAD_NUMBERS_MODE_TIMER_MAX = 55.0;
    private static final int LANES_COUNT = 10;
    /** Заголовок + 10 дорожек в таблице сплитов. */
    private static final int SPLITS_TABLE_ROW_COUNT = LANES_COUNT + 1;
    private static final double SPLITS_TABLE_ROW_HEIGHT = 32.0;
    private static final double SPLITS_TABLE_VGAP = 8.0;
    /** Место под горизонтальный скроллбар ScrollPane. */
    private static final double SPLITS_TABLE_HSCROLL_RESERVE = 18.0;
    private static final double SPLITS_TABLE_PANE_PADDING = 6.0;
    private static final int TCP_CONNECT_TIMEOUT_MS = 5000;
    private static final int TCP_READ_TIMEOUT_MS = 3000;
    private static final long POSITION_DEBOUNCE_MS = 300;
    /**
     * Was 0.05m — skipped lanes/frames and split ORAD ticks.
     * Frame-aligned export: every TCP frame sends a full distance pack (EPS unused).
     */
    /** 0 = one full distance pack on every accepted video frame_id (ORAD paced by frame). */
    private static final double DISTANCE_EXPORT_EPS = 0.0;
    private static final double ORAD_POS_X_OFFSET_MIN = -100.0;
    private static final double ORAD_POS_X_OFFSET_MAX = 100.0;
    private static final int ORAD_POSITION_EXPORT_HZ = 25;
    private static final int ORAD_POSITION_EXPORT_HZ_FAST = 50;
    private static final long ORAD_POSITION_BUFFER_DELAY_MS = 40;
    private static final long ORAD_POSITION_DUPLICATE_DELAY_MS = 10;
    private static final String ORAD_POS_MODE_BUFFERED_25HZ = "buffered_25hz";
    private static final String ORAD_POS_MODE_BUFFERED_50HZ = "buffered_50hz";
    private static final String ORAD_POS_MODE_BUFFERED_DELAY_40MS = "buffered_delay_40ms";
    private static final String ORAD_POS_MODE_RAW_DUPLICATE_10MS = "raw_duplicate_10ms";
    private static final DateTimeFormatter COORDINATE_LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter COORDINATE_LOG_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final int COORDINATE_LOG_FLUSH_EVERY_LINES = 100;

    private final Label[] positionLabels = new Label[LANES_COUNT];
    private final Label[] distanceLabels = new Label[LANES_COUNT];
    private final Label[] gpLabels = new Label[LANES_COUNT];
    private final Label[] directionLabels = new Label[LANES_COUNT];

    private final double[] lastTotalDistance = new double[LANES_COUNT];
    private final double[] lastJsonXSegment = new double[LANES_COUNT];
    private final boolean[] hasLastJsonXSegment = new boolean[LANES_COUNT];
    /** Сырой x_m_pool из TCP JSON, вход фильтра. */
    private final double[] lastTcpXmPoolForOrad = new double[LANES_COUNT];
    private final boolean[] hasLastTcpXmPoolForOrad = new boolean[LANES_COUNT];
    /** x_m_pool после сглаживания внутри программы. */
    private final double[] lastFilteredXmPool = new double[LANES_COUNT];
    private final boolean[] hasLastFilteredXmPool = new boolean[LANES_COUNT];
    /** true: стрелка по сглаженной координате. false: по сырому x_m_pool из JSON. */
    private volatile boolean oradArrowSmoothEnabled = true;
    /** Пройденная дистанция для места и отставания, посчитанная из x_m_raw. */
    private final double[] lastRawTotalDistance = new double[LANES_COUNT];
    private final boolean[] hasLastRawTotalDistance = new boolean[LANES_COUNT];
    /** true: место и отставание из x_m_raw. false: из сглаженного x_m. */
    private volatile boolean placeFromRawEnabled = false;
    private final String[] lastDirection = new String[LANES_COUNT];
    private final int[] displayedRanks = new int[LANES_COUNT];

    private final double[] lastExportedDistance = new double[LANES_COUNT];
    private final boolean[] hasExportedDistance = new boolean[LANES_COUNT];
    private final String[] lastExportedDirection = new String[LANES_COUNT];

    private long lastRankUpdateTs = 0;
    private boolean oradNumbersModeSentForStartWindow = false;

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
    private final int[] pendingPlashPosXCount = new int[LANES_COUNT];
    private final boolean[] hasLastTcpPlashPosX = new boolean[LANES_COUNT];
    private final double[] previousTcpPlashPosX = new double[LANES_COUNT];
    private final double[] lastTcpPlashPosX = new double[LANES_COUNT];

    private Socket tcpSocket;
    private Thread tcpThread;
    private volatile boolean tcpRunning = false;
    private final AtomicBoolean uiUpdateQueued = new AtomicBoolean(false);

    private final Pattern laneIdPattern = Pattern.compile("\"lane_id\":(\\d+)");
    private final Pattern xPattern = Pattern.compile("\"x_m\":([-0-9\\.Ee]+)");
    private final Pattern xPoolPattern = Pattern.compile("\"x_m_pool\":([-0-9\\.Ee]+)");
    private final Pattern xRawPattern = Pattern.compile("\"x_m_raw\":([-0-9\\.Ee]+)");
    private final Pattern directionPattern = Pattern.compile("\"direction\":\"(left|right)\"");
    private final Pattern frameIdPattern = Pattern.compile("\"frame_id\":(-?\\d+)");
    private final Pattern captureTsPattern = Pattern.compile("\"capture_timestamp\":\"([^\"]+)\"");
    private final Pattern processingTsPattern = Pattern.compile("\"timestamp\":\"([^\"]+)\"");
    private final Pattern nominalPeriodPattern = Pattern.compile("\"nominal_frame_period_ms\":(\\d+)");
    private final Pattern interpolatedPattern = Pattern.compile("\"interpolated\":(true|false)");
    private ServerSocket splitsTcpServerSocket;
    private Thread splitsTcpServerThread;
    private volatile boolean splitsTcpServerRunning = false;
    private final List<Socket> splitsTcpClients = new ArrayList<>();
    private final Object splitsStateLock = new Object();
    private boolean pendingSplitsJsonReset = false;
    private static final int SPLITS_TCP_BROADCAST_HZ = 25;
    private ScheduledExecutorService splitsTcpBroadcastExecutor;

    private ServerSocket tracksTcpServerSocket;
    private Thread tracksTcpServerThread;
    private volatile boolean tracksTcpServerRunning = false;
    private final List<Socket> tracksTcpClients = new ArrayList<>();
    private static final int TRACKS_TCP_BROADCAST_HZ = 25;
    private ScheduledExecutorService tracksTcpBroadcastExecutor;
    private ScheduledExecutorService placeSwimmingPosExportExecutor;
    private ScheduledExecutorService placeSwimmingPosDuplicateExecutor;
    private final Object coordinateLogLock = new Object();
    private BufferedWriter coordinateLogWriter;
    private int coordinateLogPendingLines = 0;

    @FXML
    private TextArea logArea;

    @FXML
    private TextField portField;

    @FXML
    private ComboBox<String> parityComboBox, flowControlComboBox, speedComboBox, dataBitsComboBox, stopBitsComboBox, encodingComboBox;

    @FXML private TextField tcpHostField;
    @FXML private TextField tcpPortField;
    @FXML private TextField tcpHostField2;
    @FXML private TextField tcpPortField2;
    private Socket tcpSocket2;
    private Thread tcpThread2;
    private volatile boolean tcp1Alive = false;
    private volatile boolean tcp2Alive = false;
    private volatile boolean tcpSecondEnabled = false;
    /** Legacy per-lane wall-clock buffers (fallback if video-frame meta missing). */
    private final double[][] trackerX = new double[2][LANES_COUNT];
    private final double[][] trackerPool = new double[2][LANES_COUNT];
    private final String[][] trackerDir = new String[2][LANES_COUNT];
    private final long[][] trackerMs = new long[2][LANES_COUNT];
    private final boolean[][] trackerSeen = new boolean[2][LANES_COUNT];
    private final boolean[][] trackerPoolSeen = new boolean[2][LANES_COUNT];
    private final double[][] trackerRaw = new double[2][LANES_COUNT];
    private final boolean[][] trackerRawSeen = new boolean[2][LANES_COUNT];
    /** Dual-TCP merge by video frame (frame_id / capture_timestamp). */
    private final Object videoFrameMergeLock = new Object();
    private final TrackerVideoSample[] trackerVideoSamples = new TrackerVideoSample[2];
    private final ScheduledFuture<?>[] trackerVideoFlushFutures = new ScheduledFuture<?>[2];
    private final AtomicLong trackerVideoFlushGeneration = new AtomicLong();
    private ScheduledExecutorService videoFrameMergeExecutor;
    private long lastAppliedVideoFrameId = Long.MIN_VALUE;
    private long lastAppliedCaptureEpochMs = Long.MIN_VALUE;
    /** Previous accepted ORAD pack timings (for dt_* in coordinates log). */
    private long lastOradLogFrameId = Long.MIN_VALUE;
    private long lastOradLogCaptureMs = Long.MIN_VALUE;
    private long lastOradLogWallMs = Long.MIN_VALUE;
    private static final int DEFAULT_VIDEO_FRAME_PERIOD_MS = 40;
    private static final int VIDEO_FRAME_MATCH_WAIT_MS = 20;
    private final double[] smoothSegX = new double[LANES_COUNT];
    private final double[] smoothSegVel = new double[LANES_COUNT];
    private final long[] smoothSegMs = new long[LANES_COUNT];
    private final boolean[] smoothSegReady = new boolean[LANES_COUNT];
    private final double[] smoothPoolX = new double[LANES_COUNT];
    private final double[] smoothPoolVel = new double[LANES_COUNT];
    private final long[] smoothPoolMs = new long[LANES_COUNT];
    private final boolean[] smoothPoolReady = new boolean[LANES_COUNT];
    @FXML private Button startTcpButton, stopTcpButton;
    @FXML private Label tcpConnectionLabel;
    @FXML private ImageView tcpConnectionImageView;
    @FXML private Label tcpConnectionLabel2;
    @FXML private ImageView tcpConnectionImageView2;
    @FXML private TextField splitsTcpServerPortField;
    @FXML private Button startSplitsTcpServerButton, stopSplitsTcpServerButton;
    @FXML private Label splitsTcpServerStatusLabel, splitsTcpClientsCountLabel;
    @FXML private TextField tracksTcpServerPortField;
    @FXML private Button startTracksTcpServerButton, stopTracksTcpServerButton;
    @FXML private Label tracksTcpServerStatusLabel, tracksTcpClientsCountLabel;

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
    /** Смещение Position_X в ORAD (x_m_pool + offset). */
    @FXML private CheckBox oradPosXOffsetCheckBox;
    @FXML private TextField oradPosXOffsetField;
    /** Режим отправки Position_X в ORAD. */
    @FXML private ComboBox<String> oradPosXModeComboBox;
    /** Писать лог TCP/ORAD координат в файл рядом с приложением. */
    @FXML private CheckBox coordinateLogToFileCheckBox;
    /** Стрелка ORAD: сглаженная координата или сырой x_m_pool из TCP JSON. */
    @FXML private CheckBox oradArrowSmoothCheckBox;
    /** Место и отставание: x_m_raw из TCP JSON или сглаженный x_m. */
    @FXML private CheckBox placeFromRawCheckBox;

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
        if (tcpHostField2 != null) {
            tcpHostField2.setText(properties.getProperty("tcpHost2", "127.0.0.1"));
        }
        if (tcpPortField2 != null) {
            tcpPortField2.setText(properties.getProperty("tcpPort2", ""));
        }
        stopTcpButton.setDisable(true);
        startTcpButton.setDisable(false);
        updateTcpSlotUi(1, "TCP 1 Disconnected", false);
        updateTcpSlotUi(2, secondPortText().isEmpty() ? "TCP 2 off" : "TCP 2 Disconnected", false);

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
        if (tcpHostField2 != null) {
            tcpHostField2.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) setProperties("tcpHost2", newValue);
            });
        }
        if (tcpPortField2 != null) {
            tcpPortField2.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null) return;
                String v = newValue.trim();
                setProperties("tcpPort2", v);
                if (!tcpRunning) {
                    updateTcpSlotUi(2, v.isEmpty() ? "TCP 2 off" : "TCP 2 Disconnected", false);
                }
            });
        }

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

        if (tracksTcpServerPortField != null) {
            tracksTcpServerPortField.setText(properties.getProperty("tracksTcpServerPort", "9101"));
            tracksTcpServerPortField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null) return;
                String v = newValue.trim();
                if (v.isEmpty()) return;
                try {
                    Integer.parseInt(v);
                    setProperties("tracksTcpServerPort", v);
                } catch (NumberFormatException ignored) {
                }
            });
        }
        if (startTracksTcpServerButton != null && stopTracksTcpServerButton != null) {
            startTracksTcpServerButton.setDisable(false);
            stopTracksTcpServerButton.setDisable(true);
        }
        updateTracksTcpServerUi("Stopped");

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
        if (oradPosXOffsetCheckBox != null) {
            oradPosXOffsetCheckBox.setSelected(Boolean.parseBoolean(
                    properties.getProperty("oradPosXOffsetEnabled", "false")));
            oradPosXOffsetCheckBox.selectedProperty().addListener((o, p, n) -> {
                if (oradPosXOffsetField != null) {
                    oradPosXOffsetField.setDisable(!Boolean.TRUE.equals(n));
                }
                setProperties("oradPosXOffsetEnabled", String.valueOf(Boolean.TRUE.equals(n)));
                refreshOradPlaceLeadersExportsIfTracking();
            });
        }
        if (oradPosXOffsetField != null) {
            oradPosXOffsetField.setText(properties.getProperty("oradPosXOffset", "0"));
            oradPosXOffsetField.setDisable(oradPosXOffsetCheckBox == null || !oradPosXOffsetCheckBox.isSelected());
            oradPosXOffsetField.textProperty().addListener((o, p, n) -> {
                double offset = parseOradPosXOffset(n);
                setProperties("oradPosXOffset", String.format(Locale.US, "%.1f", offset));
                refreshOradPlaceLeadersExportsIfTracking();
            });
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
        oradArrowSmoothEnabled = Boolean.parseBoolean(
                properties.getProperty("oradArrowSmooth", "true"));
        if (oradArrowSmoothCheckBox != null) {
            oradArrowSmoothCheckBox.setSelected(oradArrowSmoothEnabled);
            oradArrowSmoothCheckBox.selectedProperty().addListener((o, p, n) -> {
                oradArrowSmoothEnabled = Boolean.TRUE.equals(n);
                setProperties("oradArrowSmooth", String.valueOf(oradArrowSmoothEnabled));
                refreshOradPlaceLeadersExportsIfTracking();
            });
        }
        placeFromRawEnabled = Boolean.parseBoolean(
                properties.getProperty("placeFromRaw", "false"));
        if (placeFromRawCheckBox != null) {
            placeFromRawCheckBox.setSelected(placeFromRawEnabled);
            placeFromRawCheckBox.selectedProperty().addListener((o, p, n) -> {
                placeFromRawEnabled = Boolean.TRUE.equals(n);
                setProperties("placeFromRaw", String.valueOf(placeFromRawEnabled));
                synchronized (splitsStateLock) {
                    publishPlaceRanksNowLocked();
                }
                refreshOradPlaceLeadersExportsIfTracking();
                updateSplitUiFromState();
            });
        }
        if (coordinateLogToFileCheckBox != null) {
            coordinateLogToFileCheckBox.setSelected(Boolean.parseBoolean(
                    properties.getProperty("coordinateLogToFile", "false")));
            coordinateLogToFileCheckBox.selectedProperty().addListener((o, p, enabled) -> {
                setProperties("coordinateLogToFile", String.valueOf(Boolean.TRUE.equals(enabled)));
                if (Boolean.TRUE.equals(enabled)) {
                    initCoordinateLogFile();
                } else {
                    closeCoordinateLogFile();
                }
            });
            if (coordinateLogToFileCheckBox.isSelected()) {
                initCoordinateLogFile();
            }
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
        closeCoordinateLogFile();
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
        if (host.isEmpty()) host = "127.0.0.1";
        int port;
        try {
            port = Integer.parseInt(tcpPortField.getText().trim());
        } catch (Exception ex) {
            updateTcpSlotUi(1, "TCP 1 bad port", false);
            return;
        }

        final String host2 = tcpHostField2 == null || tcpHostField2.getText() == null
                || tcpHostField2.getText().trim().isEmpty()
                ? host : tcpHostField2.getText().trim();
        final String port2Text = secondPortText();
        final boolean useSecond = !port2Text.isEmpty();
        int port2 = 0;
        if (useSecond) {
            try {
                port2 = Integer.parseInt(port2Text);
            } catch (Exception ex) {
                updateTcpSlotUi(2, "TCP 2 bad port", false);
                return;
            }
        }

        for (int src = 0; src < 2; src++) {
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                trackerSeen[src][lane] = false;
                trackerPoolSeen[src][lane] = false;
                trackerRawSeen[src][lane] = false;
            }
        }

        tcpRunning = true;
        tcpSecondEnabled = useSecond;
        tcp1Alive = true;
        tcp2Alive = useSecond;
        updateTcpSlotUi(1, "TCP 1 Connecting...", false);
        if (useSecond) {
            updateTcpSlotUi(2, "TCP 2 Connecting...", false);
        } else {
            updateTcpSlotUi(2, "TCP 2 off", false);
        }
        if (startTcpButton != null) startTcpButton.setDisable(true);
        if (stopTcpButton != null) stopTcpButton.setDisable(false);

        startVideoFrameMergeExecutor();
        final String host1Final = host;
        final int port1Final = port;
        tcpThread = new Thread(() -> runTrackerTcp(host1Final, port1Final, 0), "tcp-json-reader");
        tcpThread.setDaemon(true);
        tcpThread.start();

        if (useSecond) {
            final int port2Final = port2;
            tcpThread2 = new Thread(() -> runTrackerTcp(host2, port2Final, 1), "tcp-json-reader-2");
            tcpThread2.setDaemon(true);
            tcpThread2.start();
        }
    }

    private String secondPortText() {
        if (tcpPortField2 == null || tcpPortField2.getText() == null) {
            return "";
        }
        return tcpPortField2.getText().trim();
    }

    private void runTrackerTcp(String host, int port, int source) {
        String disconnectReason = source == 0 ? "TCP 1 Disconnected" : "TCP 2 Disconnected";
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(TCP_READ_TIMEOUT_MS);
            if (source == 0) {
                tcpSocket = socket;
            } else {
                tcpSocket2 = socket;
            }
            final String connected = source == 0 ? "TCP 1 Connected" : "TCP 2 Connected";
            Platform.runLater(() -> updateTcpSlotUi(source + 1, connected, true));

            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[8192];
            StringBuilder streamBuffer = new StringBuilder();
            while (tcpRunning && !Thread.currentThread().isInterrupted()) {
                int read;
                try {
                    read = inputStream.read(buffer);
                } catch (SocketTimeoutException timeout) {
                    if (!tcpRunning || socket.isClosed() || !socket.isConnected()) {
                        break;
                    }
                    continue;
                }
                if (read < 0) {
                    disconnectReason = source == 0 ? "TCP 1 server closed" : "TCP 2 server closed";
                    break;
                }
                if (read == 0) {
                    continue;
                }
                streamBuffer.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                parseTcpJsonObjectsFromBuffer(streamBuffer, source);
            }
        } catch (ConnectException ex) {
            disconnectReason = source == 0 ? "TCP 1 server unavailable" : "TCP 2 server unavailable";
        } catch (SocketTimeoutException ex) {
            disconnectReason = source == 0 ? "TCP 1 connect timeout" : "TCP 2 connect timeout";
        } catch (IOException ex) {
            if (tcpRunning) {
                disconnectReason = (source == 0 ? "TCP 1 error: " : "TCP 2 error: ") + ex.getClass().getSimpleName();
            }
        } catch (Exception ex) {
            disconnectReason = (source == 0 ? "TCP 1 error: " : "TCP 2 error: ") + ex.getClass().getSimpleName();
        } finally {
            if (source == 0) {
                closeTcpSocketQuietly();
                tcp1Alive = false;
            } else {
                closeTcpSocket2Quietly();
                tcp2Alive = false;
            }
            final String status = disconnectReason;
            final int slot = source + 1;
            Platform.runLater(() -> {
                updateTcpSlotUi(slot, status, false);
                if (!tcp1Alive && !tcp2Alive) {
                    tcpRunning = false;
                    tcpSecondEnabled = false;
                    stopVideoFrameMergeExecutor();
                    if (startTcpButton != null) startTcpButton.setDisable(false);
                    if (stopTcpButton != null) stopTcpButton.setDisable(true);
                }
            });
        }
    }

    private void closeTcpSocket2Quietly() {
        Socket socket = tcpSocket2;
        tcpSocket2 = null;
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void parseTcpJsonObjectsFromBuffer(StringBuilder streamBuffer, int source) {
        while (true) {
            int startIdx = streamBuffer.indexOf("{");
            if (startIdx < 0) {
                break;
            }

            int depth = 0;
            boolean foundEnd = false;
            for (int i = startIdx; i < streamBuffer.length(); i++) {
                char ch = streamBuffer.charAt(i);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                }
                if (depth == 0) {
                    String jsonObj = streamBuffer.substring(startIdx, i + 1);
                    streamBuffer.delete(0, i + 1);
                    foundEnd = true;
                    try {
                        handleTcpJson(jsonObj, source);
                    } catch (Exception ex) {
                        // Bad frame must not kill the reader thread.
                    }
                    break;
                }
            }

            if (!foundEnd) {
                break;
            }
        }
    }

    private void closeTcpSocketQuietly() {
        Socket socket = tcpSocket;
        tcpSocket = null;
        if (socket == null) {
            return;
        }
        try {
            socket.shutdownInput();
        } catch (IOException ignored) {
        }
        try {
            socket.shutdownOutput();
        } catch (IOException ignored) {
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void updateTcpSlotUi(int slot, String status, boolean connected) {
        Label label = slot == 2 ? tcpConnectionLabel2 : tcpConnectionLabel;
        ImageView image = slot == 2 ? tcpConnectionImageView2 : tcpConnectionImageView;
        if (label != null) {
            label.setText(status);
        }
        if (image != null) {
            image.setImage(new Image(getClass().getResourceAsStream(connected ? con : discon)));
        }
    }

    @FXML
    private void stopTcp() {
        tcpRunning = false;
        tcpSecondEnabled = false;
        tcp1Alive = false;
        tcp2Alive = false;
        stopVideoFrameMergeExecutor();
        closeTcpSocketQuietly();
        closeTcpSocket2Quietly();

        Thread thread = tcpThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            tcpThread = null;
        }
        Thread thread2 = tcpThread2;
        if (thread2 != null) {
            thread2.interrupt();
            try {
                thread2.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            tcpThread2 = null;
        }

        if (startTcpButton != null && stopTcpButton != null) {
            startTcpButton.setDisable(false);
            stopTcpButton.setDisable(true);
        }
        updateTcpSlotUi(1, "TCP 1 Disconnected", false);
        updateTcpSlotUi(2, secondPortText().isEmpty() ? "TCP 2 off" : "TCP 2 Disconnected", false);
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

    /** Обновляет UI таймера и при значении 15.0…55.0 отправляет ORAD _numbers_mode = 1. */
    public void updateTimer(String timerText) {
        if (timerLabel != null) {
            timerLabel.setText(timerText);
        }
        if (timerText == null || timerText.trim().isEmpty()) {
            return;
        }
        try {
            double timer = Double.parseDouble(timerText.trim().replace(',', '.'));
            if (timer >= ORAD_NUMBERS_MODE_TIMER_MIN && timer <= ORAD_NUMBERS_MODE_TIMER_MAX) {
                sendOradNumbersModeForStartWindow();
            } else {
                oradNumbersModeSentForStartWindow = false;
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void sendOradNumbersModeForStartWindow() {
        if (controller == null || oradNumbersModeSentForStartWindow) {
            return;
        }
        controller.sendSetExport(RE_SCENE, RE_NUMBERS_MODE, "1");
        oradNumbersModeSentForStartWindow = true;
    }

    private boolean isOradPosXOffsetEnabled() {
        return oradPosXOffsetCheckBox != null && oradPosXOffsetCheckBox.isSelected();
    }

    private double parseOradPosXOffset(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            return Math.max(ORAD_POS_X_OFFSET_MIN, Math.min(ORAD_POS_X_OFFSET_MAX, value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private double getOradPosXOffsetValue() {
        if (oradPosXOffsetField == null) {
            return 0.0;
        }
        return parseOradPosXOffset(oradPosXOffsetField.getText());
    }

    private double oradPosXForExport(double rawXmPool) {
        return isOradPosXOffsetEnabled() ? rawXmPool + getOradPosXOffsetValue() : rawXmPool;
    }

    private String formatOradPosXExport(double rawXmPool) {
        return String.format(Locale.US, "%.6f", oradPosXForExport(rawXmPool));
    }

    private void logCoordinate(String source, int lane, Double xmPool, Double xmProgress) {
        logCoordinate(source, lane, xmPool, xmProgress, null, null);
    }

    private void logCoordinate(String source, int lane, Double xmPool, Double xmProgress,
                               Long frameId, Long captureEpochMs) {
        if (!isCoordinateLogToFileEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(LocalDateTime.now().format(COORDINATE_LOG_TIMESTAMP))
                .append(" source=").append(source)
                .append(" lane=").append(lane);
        if (xmPool != null) {
            sb.append(String.format(Locale.US, " x_m_pool=%.6f", xmPool));
        }
        if (xmProgress != null) {
            sb.append(String.format(Locale.US, " x_m=%.6f", xmProgress));
        }
        if (frameId != null) {
            sb.append(" frame_id=").append(frameId);
        }
        if (captureEpochMs != null) {
            sb.append(" capture_ts_ms=").append(captureEpochMs);
        }
        writeCoordinateLogLine(sb.toString());
    }

    /** Лог фактического значения Position_X, ушедшего в ORAD (после offset). */
    private void logOradPositionX(int lane, int oradSlot, String exportName,
                                  double rawXmPool, String exportValue) {
        if (!isCoordinateLogToFileEnabled()) {
            return;
        }
        String line = String.format(Locale.US,
                "%s source=ORAD lane=%d orad_slot=%d export=%s x_m_pool=%.6f orad_position_x=%s",
                LocalDateTime.now().format(COORDINATE_LOG_TIMESTAMP),
                lane, oradSlot, exportName, rawXmPool, exportValue);
        writeCoordinateLogLine(line);
    }

    /**
     * One line per accepted video frame sent to ORAD, with timing deltas.
     * Expect {@code dt_capture_ms≈40} and {@code d_frame_id=1} at 25 Hz.
     */
    private void logOradFrameTiming(long frameId, long captureEpochMs, int source) {
        if (!isCoordinateLogToFileEnabled()) {
            return;
        }
        long wall = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(160);
        sb.append(LocalDateTime.now().format(COORDINATE_LOG_TIMESTAMP))
                .append(" source=ORAD_FRAME src=").append(source);
        if (frameId != Long.MIN_VALUE) {
            sb.append(" frame_id=").append(frameId);
        } else {
            sb.append(" frame_id=none");
        }
        if (captureEpochMs != Long.MIN_VALUE) {
            sb.append(" capture_ts_ms=").append(captureEpochMs);
        } else {
            sb.append(" capture_ts_ms=none");
        }
        if (captureEpochMs != Long.MIN_VALUE && lastOradLogCaptureMs != Long.MIN_VALUE) {
            sb.append(" dt_capture_ms=").append(captureEpochMs - lastOradLogCaptureMs);
        } else {
            sb.append(" dt_capture_ms=none");
        }
        if (lastOradLogWallMs != Long.MIN_VALUE) {
            sb.append(" dt_wall_ms=").append(wall - lastOradLogWallMs);
        } else {
            sb.append(" dt_wall_ms=none");
        }
        if (frameId != Long.MIN_VALUE && lastOradLogFrameId != Long.MIN_VALUE) {
            sb.append(" d_frame_id=").append(frameId - lastOradLogFrameId);
        } else {
            sb.append(" d_frame_id=none");
        }
        writeCoordinateLogLine(sb.toString());
        if (frameId != Long.MIN_VALUE) {
            lastOradLogFrameId = frameId;
        }
        if (captureEpochMs != Long.MIN_VALUE) {
            lastOradLogCaptureMs = captureEpochMs;
        }
        lastOradLogWallMs = wall;
    }

    private void writeCoordinateLogLine(String line) {
        synchronized (coordinateLogLock) {
            if (coordinateLogWriter == null) {
                return;
            }
            try {
                coordinateLogWriter.write(line);
                coordinateLogWriter.newLine();
                coordinateLogPendingLines++;
                if (coordinateLogPendingLines >= COORDINATE_LOG_FLUSH_EVERY_LINES) {
                    coordinateLogWriter.flush();
                    coordinateLogPendingLines = 0;
                }
            } catch (IOException ex) {
                try {
                    coordinateLogWriter.close();
                } catch (IOException ignored) {
                }
                coordinateLogWriter = null;
                coordinateLogPendingLines = 0;
                System.out.println("Coordinate log file write failed: " + ex.getMessage());
            }
        }
    }

    private boolean isCoordinateLogToFileEnabled() {
        return coordinateLogToFileCheckBox != null && coordinateLogToFileCheckBox.isSelected();
    }

    private void initCoordinateLogFile() {
        if (!isCoordinateLogToFileEnabled()) {
            return;
        }
        synchronized (coordinateLogLock) {
            if (coordinateLogWriter != null) {
                return;
            }
            try {
                Path logDirectory = getApplicationDirectory();
                Files.createDirectories(logDirectory);
                String fileName = "coordinates-" +
                        LocalDateTime.now().format(COORDINATE_LOG_FILE_TIMESTAMP) + ".log";
                coordinateLogWriter = Files.newBufferedWriter(logDirectory.resolve(fileName), StandardCharsets.UTF_8);
                coordinateLogWriter.write(
                        "timestamp source lane x_m_pool x_m frame_id capture_ts_ms"
                                + " | ORAD_FRAME: frame_id capture_ts_ms dt_capture_ms dt_wall_ms d_frame_id"
                                + " | ORAD: orad_slot export orad_position_x");
                coordinateLogWriter.newLine();
                coordinateLogWriter.flush();
                lastOradLogFrameId = Long.MIN_VALUE;
                lastOradLogCaptureMs = Long.MIN_VALUE;
                lastOradLogWallMs = Long.MIN_VALUE;
            } catch (Exception ex) {
                coordinateLogWriter = null;
                System.out.println("Coordinate log file init failed: " + ex.getMessage());
            }
        }
    }

    private Path getApplicationDirectory() {
        try {
            Path appPath = Paths.get(RootLayoutController.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            if (Files.isRegularFile(appPath)) {
                Path parent = appPath.getParent();
                if (parent != null) {
                    return parent;
                }
            }
            if (Files.isDirectory(appPath)) {
                return appPath;
            }
        } catch (Exception ignored) {
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private void closeCoordinateLogFile() {
        synchronized (coordinateLogLock) {
            if (coordinateLogWriter == null) {
                return;
            }
            try {
                coordinateLogWriter.flush();
                coordinateLogWriter.close();
            } catch (IOException ex) {
                System.out.println("Coordinate log file close failed: " + ex.getMessage());
            } finally {
                coordinateLogWriter = null;
                coordinateLogPendingLines = 0;
            }
        }
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

    @FXML
    private void startTracksTcpServer() {
        if (tracksTcpServerRunning) return;
        int port;
        try {
            port = Integer.parseInt(tracksTcpServerPortField.getText().trim());
        } catch (Exception ex) {
            updateTracksTcpServerUi("Bad port");
            return;
        }

        tracksTcpServerRunning = true;
        if (startTracksTcpServerButton != null) startTracksTcpServerButton.setDisable(true);
        if (stopTracksTcpServerButton != null) stopTracksTcpServerButton.setDisable(false);
        updateTracksTcpServerUi("Starting...");

        tracksTcpServerThread = new Thread(() -> {
            try {
                tracksTcpServerSocket = new ServerSocket(port);
                Platform.runLater(this::startTracksTcpBroadcastScheduler);
                updateTracksTcpServerUi("Listening on " + port);
                while (tracksTcpServerRunning) {
                    Socket client = tracksTcpServerSocket.accept();
                    client.setTcpNoDelay(true);
                    synchronized (tracksTcpClients) {
                        tracksTcpClients.add(client);
                    }
                    updateTracksTcpServerUi("Listening on " + port);
                }
            } catch (IOException ex) {
                if (tracksTcpServerRunning) {
                    updateTracksTcpServerUi("Server error");
                }
            } finally {
                tracksTcpServerRunning = false;
                closeTracksTcpServerResources();
                Platform.runLater(() -> {
                    if (startTracksTcpServerButton != null) startTracksTcpServerButton.setDisable(false);
                    if (stopTracksTcpServerButton != null) stopTracksTcpServerButton.setDisable(true);
                    if (tracksTcpServerStatusLabel != null && !"Server error".equals(tracksTcpServerStatusLabel.getText())) {
                        tracksTcpServerStatusLabel.setText("Stopped");
                    }
                    if (tracksTcpClientsCountLabel != null) tracksTcpClientsCountLabel.setText("0");
                });
            }
        }, "tracks-tcp-server");
        tracksTcpServerThread.start();
    }

    @FXML
    private void stopTracksTcpServer() {
        tracksTcpServerRunning = false;
        if (tracksTcpServerThread != null) {
            tracksTcpServerThread.interrupt();
            tracksTcpServerThread = null;
        }
        closeTracksTcpServerResources();
        if (startTracksTcpServerButton != null) startTracksTcpServerButton.setDisable(false);
        if (stopTracksTcpServerButton != null) stopTracksTcpServerButton.setDisable(true);
        updateTracksTcpServerUi("Stopped");
    }

    private void closeTracksTcpServerResources() {
        stopTracksTcpBroadcastScheduler();
        if (tracksTcpServerSocket != null) {
            try {
                tracksTcpServerSocket.close();
            } catch (IOException ignored) {
            }
            tracksTcpServerSocket = null;
        }
        synchronized (tracksTcpClients) {
            for (Socket client : tracksTcpClients) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
            tracksTcpClients.clear();
        }
    }

    private void updateTracksTcpServerUi(String status) {
        Platform.runLater(() -> {
            if (tracksTcpServerStatusLabel != null) {
                tracksTcpServerStatusLabel.setText(status);
            }
            if (tracksTcpClientsCountLabel != null) {
                synchronized (tracksTcpClients) {
                    tracksTcpClientsCountLabel.setText(String.valueOf(tracksTcpClients.size()));
                }
            }
        });
    }

    private void broadcastTracksAsJson() {
        String payload = buildTracksJson() + "\n";
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        synchronized (tracksTcpClients) {
            List<Socket> dead = new ArrayList<>();
            for (Socket client : tracksTcpClients) {
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
                tracksTcpClients.remove(client);
            }
        }
        updateTracksTcpServerUi(tracksTcpServerRunning ? "Listening" : "Stopped");
    }

    private String buildTracksJson() {
        synchronized (splitsStateLock) {
            int poolMax = swimPoolSize;
            StringBuilder sb = new StringBuilder();
            sb.append("{\"tracks\":{");
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (lane > 0) sb.append(",");
                double coordinate = hasLastTcpXmPoolForOrad[lane]
                        ? lastTcpXmPoolForOrad[lane]
                        : 0;
                coordinate = Math.max(0, Math.min(poolMax, coordinate));
                sb.append("\"").append(lane).append("\":{");
                sb.append("\"coordinate\":")
                        .append(String.format(Locale.US, "%.1f", coordinate))
                        .append(",\"distance\":")
                        .append(String.format(Locale.US, "%.1f", toDisplayedDistance(lastTotalDistance[lane])))
                        .append("}");
            }
            sb.append("}}");
            return sb.toString();
        }
    }

    private void startTracksTcpBroadcastScheduler() {
        if (!tracksTcpServerRunning) return;
        stopTracksTcpBroadcastScheduler();
        tracksTcpBroadcastExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tracks-tcp-broadcast");
            t.setDaemon(true);
            return t;
        });
        long periodMs = Math.max(1L, 1000L / TRACKS_TCP_BROADCAST_HZ);
        tracksTcpBroadcastExecutor.scheduleAtFixedRate(() -> {
            if (!tracksTcpServerRunning) return;
            synchronized (tracksTcpClients) {
                if (tracksTcpClients.isEmpty()) return;
            }
            Platform.runLater(() -> {
                if (!tracksTcpServerRunning) return;
                broadcastTracksAsJson();
            });
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stopTracksTcpBroadcastScheduler() {
        if (tracksTcpBroadcastExecutor == null) return;
        tracksTcpBroadcastExecutor.shutdown();
        try {
            if (!tracksTcpBroadcastExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                tracksTcpBroadcastExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            tracksTcpBroadcastExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        tracksTcpBroadcastExecutor = null;
    }

    /** Rank 1 = leader (max distance); ties broken by lower lane index. */
    private int[] computeRanksByTotalDistance() {
        Integer[] idx = new Integer[LANES_COUNT];
        for (int lane = 0; lane < LANES_COUNT; lane++) idx[lane] = lane;
        Arrays.sort(idx, (a, b) -> {
            int cmp = Double.compare(
                    displayedProgressForPlace(b),
                    displayedProgressForPlace(a));
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
        Arrays.fill(pendingPlashPosXCount, 0);
        Arrays.fill(hasLastTcpPlashPosX, false);
        Arrays.fill(previousTcpPlashPosX, 0.0);
        Arrays.fill(lastTcpPlashPosX, 0.0);
    }

    /** Ставит в буфер Position_X только для дорожек с новым x_m_pool в текущем TCP-кадре. */
    private void queuePlaceSwimmingPosXFromTcpFrameLocked(boolean[] havePoolFrame, double[] frameXmPool) {
        if (!leadersPlaceSwimmingTracking || isPosXModeRawDuplicate10ms()) {
            return;
        }
        boolean reverseLanes = oradLaneReverseCheckBox != null && oradLaneReverseCheckBox.isSelected();
        for (int lane = 0; lane < LANES_COUNT; lane++) {
            if (!havePoolFrame[lane]) {
                continue;
            }
            int oradSlot = mapSourceLaneToOradSlot(lane, reverseLanes);
            queuePlaceSwimmingPosXExportLocked(oradSlot, frameXmPool[lane], false);
        }
    }

    /** Кладет Position_X в буфер; в 25 Гц режиме тикер отправляет последнее TCP-значение окна. */
    private void queuePlaceSwimmingPosXExportLocked(int oradSlot, double posX, boolean forceResend) {
        long now = System.currentTimeMillis();
        pendingPlashPosXValue[oradSlot] = posX;
        pendingPlashPosXDirty[oradSlot] = true;
        if (isPosXModeBuffered25hz()) {
            pendingPlashPosXCount[oradSlot]++;
            previousTcpPlashPosX[oradSlot] = hasLastTcpPlashPosX[oradSlot]
                    ? lastTcpPlashPosX[oradSlot]
                    : posX;
            lastTcpPlashPosX[oradSlot] = posX;
            hasLastTcpPlashPosX[oradSlot] = true;
        }
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
            boolean fixedRate25hz = isPosXModeBuffered25hz();
            for (int oradSlot = 0; oradSlot < LANES_COUNT; oradSlot++) {
                if (!fixedRate25hz && !pendingPlashPosXDirty[oradSlot]) {
                    continue;
                }
                if (isPosXModeBufferedDelay40ms() && now < pendingPlashPosXDueAtMs[oradSlot]) {
                    continue;
                }
                double posX;
                if (fixedRate25hz) {
                    if (pendingPlashPosXCount[oradSlot] > 0) {
                        posX = pendingPlashPosXValue[oradSlot];
                    } else if (hasLastTcpPlashPosX[oradSlot]) {
                        posX = lastTcpPlashPosX[oradSlot]
                                + (lastTcpPlashPosX[oradSlot] - previousTcpPlashPosX[oradSlot]);
                    } else if (lastPlashPosXInitialized[oradSlot]) {
                        posX = lastPlashPosXSent[oradSlot];
                    } else if (pendingPlashPosXDirty[oradSlot]) {
                        posX = pendingPlashPosXValue[oradSlot];
                    } else {
                        continue;
                    }
                } else {
                    posX = pendingPlashPosXValue[oradSlot];
                }
                boolean forceResend = pendingPlashPosXForce[oradSlot];
                boolean needPos = fixedRate25hz
                        || forceResend
                        || !lastPlashPosXInitialized[oradSlot]
                        || Double.compare(posX, lastPlashPosXSent[oradSlot]) != 0;
                if (needPos) {
                    boolean reverseLanes = oradLaneReverseCheckBox != null && oradLaneReverseCheckBox.isSelected();
                    int srcLane = mapOradSlotToSourceLane(oradSlot, reverseLanes);
                    String suf = plashGroupSuffix(oradSlot);
                    String exportName = "Transformation_Plash-Group-" + suf + "_Position_X";
                    String exportValue = formatOradPosXExport(posX);
                    logOradPositionX(srcLane, oradSlot, exportName, posX, exportValue);
                    controller.sendSetExport(PLACE_SWIMMING_SCENE, exportName, exportValue);
                    lastPlashPosXSent[oradSlot] = posX;
                    lastPlashPosXInitialized[oradSlot] = true;
                }
                pendingPlashPosXDirty[oradSlot] = false;
                pendingPlashPosXForce[oradSlot] = false;
                pendingPlashPosXDueAtMs[oradSlot] = 0L;
                pendingPlashPosXCount[oradSlot] = 0;
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

    private void scheduleDuplicatePositionXExport(String scene, String exportName, String exportValue,
                                                  int lane, int oradSlot, double rawXmPool) {
        ScheduledExecutorService duplicateExecutor = placeSwimmingPosDuplicateExecutor;
        if (duplicateExecutor == null) {
            return;
        }
        duplicateExecutor.schedule(() -> {
            Retalk2ConnectionController currentController = controller;
            if (currentController == null || !leadersPlaceSwimmingTracking) {
                return;
            }
            logOradPositionX(lane, oradSlot, exportName, rawXmPool, exportValue);
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
            double rawXmPool = frameXmPool[srcLane];
            String exportValue = formatOradPosXExport(rawXmPool);
            logOradPositionX(srcLane, oradSlot, exportName, rawXmPool, exportValue);
            controller.sendSetExport(PLACE_SWIMMING_SCENE, exportName, exportValue);
            scheduleDuplicatePositionXExport(PLACE_SWIMMING_SCENE, exportName, exportValue,
                    srcLane, oradSlot, rawXmPool);
            lastPlashPosXSent[oradSlot] = frameXmPool[srcLane];
            lastPlashPosXInitialized[oradSlot] = true;
        }
    }

    private boolean isPosXModeRawDuplicate10ms() {
        return ORAD_POS_MODE_RAW_DUPLICATE_10MS.equals(getSelectedPosXMode());
    }

    private boolean isPosXModeBuffered25hz() {
        return ORAD_POS_MODE_BUFFERED_25HZ.equals(getSelectedPosXMode());
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

    /**
     * x_m и x_m_raw — позиция на текущем отрезке, 0 закреплён у одной стенки.
     * Чётный отрезок: плывут от 0 к длине бассейна. Нечётный: от длины к 0,
     * поэтому к уже пройденным длинам добавляется (длина − x), а не ещё одно зеркало.
     */
    private double totalDistanceFromSegment(int passedSplits, double xSeg) {
        double segLen = swimPoolSize;
        double segPos = Math.max(0, Math.min(segLen, xSeg));
        double base = passedSplits * (double) segLen;
        double total = (passedSplits % 2 == 0) ? (base + segPos) : (base + (segLen - segPos));
        return Math.max(0, Math.min(distance, total));
    }

    /** Дистанция, по которой считаются место и отставание. */
    private double progressForPlace(int lane) {
        if (placeFromRawEnabled && hasLastRawTotalDistance[lane]) {
            return lastRawTotalDistance[lane];
        }
        return lastTotalDistance[lane];
    }

    /**
     * Для x_m_raw место берётся из накопленной дистанции как есть.
     * Зеркало 50 м остаётся только у прежнего пути по сглаженному x_m.
     */
    private double displayedProgressForPlace(int lane) {
        double progress = progressForPlace(lane);
        if (placeFromRawEnabled && hasLastRawTotalDistance[lane]) {
            return Math.max(0.0, Math.min(distance, progress));
        }
        return toDisplayedDistance(progress);
    }

    /** Сразу публикует место в таблицу и ORAD. Удерживать {@link #splitsStateLock}. */
    private void publishPlaceRanksNowLocked() {
        int[] newRanks = computeRanksByTotalDistance();
        System.arraycopy(newRanks, 0, displayedRanks, 0, LANES_COUNT);
        lastRankUpdateTs = System.currentTimeMillis();
        if (controller == null) {
            return;
        }
        for (int lane = 0; lane < LANES_COUNT; lane++) {
            controller.sendSetExport(RE_SCENE, "position_lane_" + lane,
                    String.valueOf(displayedRanks[lane]));
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

    private boolean isDistanceDisplayInverted() {
        return distance == 50;
    }

    /** Стрелки left/right переворачиваются только на дистанции 50 м в бассейне 50 м. */
    private boolean isArrowDirectionInverted() {
        return distance == 50 && swimPoolSize == 50;
    }

    private double toDisplayedDistance(double progressDistance) {
        double clamped = Math.max(0.0, Math.min(distance, progressDistance));
        if (!isDistanceDisplayInverted()) {
            return clamped;
        }
        if (clamped <= 0.1) {
            return clamped;
        }
        return distance - clamped;
    }

    private String toDisplayedDirection(String direction) {
        if (!isArrowDirectionInverted() || direction == null) {
            return direction;
        }
        if ("left".equals(direction)) {
            return "right";
        }
        if ("right".equals(direction)) {
            return "left";
        }
        return direction;
    }

    /**
     * Направление плашки в ORAD: 0 и чётное число заполненных сплитов — left (1),
     * нечётное — right (2). На 50 м в бассейне 50 м направление обратное.
     * «РЕВЕРС ЛИДЕРОВ» инвертирует.
     */
    private String oradChildIndexFromSplitsForLane(int lane, boolean reverseLeadersDir) {
        int n = countFilledSplits(participants.size() > lane ? participants.get(lane) : null);
        boolean left = (n % 2 == 0);
        if (isArrowDirectionInverted()) {
            left = !left;
        }
        if (reverseLeadersDir) {
            left = !left;
        }
        return left ? "1" : "2";
    }

    /** Координата стрелки: сглаженный x_m_pool или сырой вход фильтра. */
    private double arrowCoordinateForOrad(int lane) {
        if (oradArrowSmoothEnabled && hasLastFilteredXmPool[lane]) {
            return lastFilteredXmPool[lane];
        }
        return hasLastTcpXmPoolForOrad[lane] ? lastTcpXmPoolForOrad[lane] : 0.0;
    }

    /** Слот экспорта ORAD 0..9 → индекс дорожки-источника данных. */
    private int mapOradSlotToSourceLane(int oradSlot, boolean reverseLanes) {
        return reverseLanes ? (LANES_COUNT - 1 - oradSlot) : oradSlot;
    }

    /** Дорожка-источник → слот экспорта ORAD. */
    private int mapSourceLaneToOradSlot(int srcLane, boolean reverseLanes) {
        return reverseLanes ? (LANES_COUNT - 1 - srcLane) : srcLane;
    }

    /** Топ-4 по пройденной дистанции; при равенстве — меньший индекс дорожки выше. */
    private int[] computeTopFourLanesByDistanceLocked() {
        Integer[] idx = new Integer[LANES_COUNT];
        for (int lane = 0; lane < LANES_COUNT; lane++) {
            idx[lane] = lane;
        }
        Arrays.sort(idx, (a, b) -> {
            int cmp = Double.compare(
                    displayedProgressForPlace(b),
                    displayedProgressForPlace(a));
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
        double leaderDist = displayedProgressForPlace(leaderLane);
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

            if (forceResend && !isPosXModeRawDuplicate10ms()) {
                queuePlaceSwimmingPosXExportLocked(oradSlot, arrowCoordinateForOrad(srcLane), true);
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
                        : formatGapBehindLeaderRu(leaderDist - displayedProgressForPlace(srcLane));
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

    /** Скрывает лидеров ORAD при приходе места победителя, если они были показаны. */
    public void autoHideOradPlaceLeadersIfShown() {
        Platform.runLater(() -> {
            if (!leadersPlaceSwimmingTracking) {
                return;
            }
            if (oradPlaceLeadersToggle != null) {
                oradPlaceLeadersToggle.setSelected(false);
            }
            leadersPlaceSwimmingTracking = false;
            sendAllPlaceSwimmingPlaquesHidden();
            synchronized (splitsStateLock) {
                resetPlaceSwimmingLeaderExportCacheLocked();
            }
        });
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
                leaderDist = Math.max(leaderDist, displayedProgressForPlace(l));
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
                double displayDistance = toDisplayedDistance(lastTotalDistance[lane]);
                double gp = leaderDist - displayedProgressForPlace(lane);
                sb.append(",\"line_pos\":").append(ranks[lane])
                        .append(",\"line_real_x\":")
                        .append(String.format(Locale.US, "%.2f", displayDistance))
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

    private void handleTcpJson(String jsonObj, int source) {
        // Remove spaces/new lines as requested (this also keeps parsing simpler).
        String cleaned = jsonObj.replaceAll("[\\s\\r\\n\\t]+", "");

        boolean[] seenLane = new boolean[LANES_COUNT];
        double[] frameX = new double[LANES_COUNT];
        boolean[] havePoolFrame = new boolean[LANES_COUNT];
        double[] frameXmPool = new double[LANES_COUNT];
        boolean[] haveRawFrame = new boolean[LANES_COUNT];
        double[] frameXmRaw = new double[LANES_COUNT];
        String[] frameDirections = new String[LANES_COUNT];

        Long frameId = extractLong(frameIdPattern, cleaned);
        Long captureEpochMs = parseIsoToEpochMs(extractString(captureTsPattern, cleaned));
        Long processingEpochMs = parseIsoToEpochMs(extractString(processingTsPattern, cleaned));
        Integer nominalPeriodMs = extractInt(nominalPeriodPattern, cleaned);
        boolean interpolated = "true".equalsIgnoreCase(extractString(interpolatedPattern, cleaned));

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
                        Double xRaw = extractDouble(xRawPattern, swimmerObj);

                        if (laneId != null && laneId >= 0 && laneId < LANES_COUNT && xPool != null) {
                            havePoolFrame[laneId] = true;
                            frameXmPool[laneId] = xPool;
                        }
                        if (laneId != null && laneId >= 0 && laneId < LANES_COUNT && xRaw != null) {
                            haveRawFrame[laneId] = true;
                            frameXmRaw[laneId] = xRaw;
                        }
                        if (laneId != null && laneId >= 0 && laneId < LANES_COUNT && xVal != null) {
                            seenLane[laneId] = true;
                            frameX[laneId] = xVal;
                            frameDirections[laneId] = extractString(directionPattern, swimmerObj);
                        }
                        if (laneId != null && laneId >= 0 && laneId < LANES_COUNT
                                && (xPool != null || xVal != null)) {
                            logCoordinate("TCP", laneId, xPool, xVal, frameId, captureEpochMs);
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

        TrackerVideoSample sample = TrackerVideoSample.fromParsed(
                source, frameId, captureEpochMs, processingEpochMs, nominalPeriodMs, interpolated,
                seenLane, frameX, havePoolFrame, frameXmPool, haveRawFrame, frameXmRaw, frameDirections);
        ingestTrackerVideoSample(sample);
    }

    private void ingestTrackerVideoSample(TrackerVideoSample sample) {
        if (sample == null) {
            return;
        }
        if (!tcpSecondEnabled) {
            applyTrackerVideoSample(sample);
            return;
        }

        final int src = sample.source == 1 ? 1 : 0;
        sample.source = src;
        boolean hasVideoKey = sample.frameId != Long.MIN_VALUE || sample.captureEpochMs != Long.MIN_VALUE;
        if (!hasVideoKey) {
            // Fallback: old wall-clock average when tracker does not send video meta.
            applyLegacyWallClockMerge(sample);
            return;
        }

        TrackerVideoSample merged = null;
        synchronized (videoFrameMergeLock) {
            cancelVideoFrameFlush(src);
            trackerVideoSamples[src] = sample;
            TrackerVideoSample other = trackerVideoSamples[1 - src];
            if (other != null && videoFramesMatch(sample, other)) {
                cancelVideoFrameFlush(1 - src);
                merged = mergeVideoSamples(sample, other);
                trackerVideoSamples[0] = null;
                trackerVideoSamples[1] = null;
            } else {
                scheduleVideoFrameFlush(src, sample);
            }
        }
        if (merged != null) {
            applyTrackerVideoSample(merged);
        }
    }

    private void applyLegacyWallClockMerge(TrackerVideoSample sample) {
        boolean[] seenLane = sample.seenLane.clone();
        double[] frameX = sample.frameX.clone();
        boolean[] havePoolFrame = sample.havePoolFrame.clone();
        double[] frameXmPool = sample.frameXmPool.clone();
        boolean[] haveRawFrame = sample.haveRaw.clone();
        double[] frameXmRaw = sample.frameXmRaw.clone();
        String[] frameDirections = sample.frameDirections.clone();
        long now = System.currentTimeMillis();
        final int src = sample.source == 1 ? 1 : 0;
        for (int lane = 0; lane < LANES_COUNT; lane++) {
            if (seenLane[lane]) {
                trackerX[src][lane] = frameX[lane];
                trackerDir[src][lane] = frameDirections[lane];
                trackerMs[src][lane] = now;
                trackerSeen[src][lane] = true;
            }
            if (havePoolFrame[lane]) {
                trackerPool[src][lane] = frameXmPool[lane];
                trackerPoolSeen[src][lane] = true;
                trackerMs[src][lane] = now;
            }
            if (haveRawFrame[lane]) {
                trackerRaw[src][lane] = frameXmRaw[lane];
                trackerRawSeen[src][lane] = true;
                trackerMs[src][lane] = now;
            }
            final boolean fresh0 = trackerSeen[0][lane] && now - trackerMs[0][lane] < 300;
            final boolean fresh1 = trackerSeen[1][lane] && now - trackerMs[1][lane] < 300;
            if (fresh0 && fresh1) {
                seenLane[lane] = true;
                frameX[lane] = 0.5 * (trackerX[0][lane] + trackerX[1][lane]);
                frameDirections[lane] = trackerDir[src][lane];
            } else if (fresh0) {
                seenLane[lane] = true;
                frameX[lane] = trackerX[0][lane];
                frameDirections[lane] = trackerDir[0][lane];
            } else if (fresh1) {
                seenLane[lane] = true;
                frameX[lane] = trackerX[1][lane];
                frameDirections[lane] = trackerDir[1][lane];
            }
            final boolean pool0 = fresh0 && trackerPoolSeen[0][lane];
            final boolean pool1 = fresh1 && trackerPoolSeen[1][lane];
            if (pool0 && pool1) {
                havePoolFrame[lane] = true;
                frameXmPool[lane] = 0.5 * (trackerPool[0][lane] + trackerPool[1][lane]);
            } else if (pool0) {
                havePoolFrame[lane] = true;
                frameXmPool[lane] = trackerPool[0][lane];
            } else if (pool1) {
                havePoolFrame[lane] = true;
                frameXmPool[lane] = trackerPool[1][lane];
            }
            final boolean raw0 = fresh0 && trackerRawSeen[0][lane];
            final boolean raw1 = fresh1 && trackerRawSeen[1][lane];
            if (raw0 && raw1) {
                haveRawFrame[lane] = true;
                frameXmRaw[lane] = 0.5 * (trackerRaw[0][lane] + trackerRaw[1][lane]);
            } else if (raw0) {
                haveRawFrame[lane] = true;
                frameXmRaw[lane] = trackerRaw[0][lane];
            } else if (raw1) {
                haveRawFrame[lane] = true;
                frameXmRaw[lane] = trackerRaw[1][lane];
            }
        }
        applyTrackerVideoSample(TrackerVideoSample.fromParsed(
                sample.source, sample.frameId == Long.MIN_VALUE ? null : sample.frameId,
                sample.captureEpochMs == Long.MIN_VALUE ? null : sample.captureEpochMs,
                sample.processingEpochMs == Long.MIN_VALUE ? null : sample.processingEpochMs,
                sample.nominalPeriodMs > 0 ? sample.nominalPeriodMs : null,
                sample.interpolated, seenLane, frameX, havePoolFrame, frameXmPool,
                haveRawFrame, frameXmRaw, frameDirections));
    }

    /**
     * One ORAD distance pack per video {@code frame_id}.
     * Dual-TCP / interpolated duplicates with the same id are dropped.
     * Large downward jumps of frame_id are treated as tracker counter reset.
     */
    private void applyTrackerVideoSample(TrackerVideoSample sample) {
        if (sample == null) {
            return;
        }
        final long exportFrameId;
        final long exportCaptureMs;
        synchronized (videoFrameMergeLock) {
            if (sample.frameId != Long.MIN_VALUE) {
                if (sample.frameId == lastAppliedVideoFrameId) {
                    return; // same video frame already exported
                }
                if (lastAppliedVideoFrameId != Long.MIN_VALUE
                        && sample.frameId < lastAppliedVideoFrameId
                        && (lastAppliedVideoFrameId - sample.frameId) < 1000L) {
                    return; // out-of-order / stale frame_id
                }
                lastAppliedVideoFrameId = sample.frameId;
                exportFrameId = sample.frameId;
            } else if (sample.captureEpochMs != Long.MIN_VALUE) {
                // Fallback when tracker omits frame_id.
                if (sample.captureEpochMs == lastAppliedCaptureEpochMs) {
                    return;
                }
                lastAppliedCaptureEpochMs = sample.captureEpochMs;
                exportFrameId = Long.MIN_VALUE;
            } else {
                exportFrameId = Long.MIN_VALUE;
            }
            if (sample.captureEpochMs != Long.MIN_VALUE) {
                lastAppliedCaptureEpochMs = sample.captureEpochMs;
            }
            exportCaptureMs = sample.captureEpochMs;
        }

        boolean[] seenLane = sample.seenLane;
        double[] frameX = sample.frameX;
        boolean[] havePoolFrame = sample.havePoolFrame;
        double[] frameXmPool = sample.frameXmPool;
        boolean[] haveRaw = sample.haveRaw;
        double[] frameXmRaw = sample.frameXmRaw;
        String[] frameDirections = sample.frameDirections;
        // Pace ORAD by video frame, not by processing-completion wall clock.
        long now = exportCaptureMs != Long.MIN_VALUE
                ? exportCaptureMs
                : (sample.processingEpochMs != Long.MIN_VALUE
                ? sample.processingEpochMs
                : System.currentTimeMillis());

        double[] jsonPool = Arrays.copyOf(frameXmPool, LANES_COUNT);
        synchronized (splitsStateLock) {
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (havePoolFrame[lane]) {
                    lastTcpXmPoolForOrad[lane] = jsonPool[lane];
                    hasLastTcpXmPoolForOrad[lane] = true;
                }
            }
            final double poolAxis = swimPoolSize > 0 ? swimPoolSize : 50;
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (seenLane[lane]) {
                    frameX[lane] = smoothSwimmerAxis(lane, frameX[lane], poolAxis,
                            smoothSegX, smoothSegVel, smoothSegMs, smoothSegReady);
                }
                if (havePoolFrame[lane]) {
                    frameXmPool[lane] = smoothSwimmerAxis(lane, jsonPool[lane], poolAxis,
                            smoothPoolX, smoothPoolVel, smoothPoolMs, smoothPoolReady);
                    lastFilteredXmPool[lane] = frameXmPool[lane];
                    hasLastFilteredXmPool[lane] = true;
                }
            }
            double[] arrowPool = oradArrowSmoothEnabled ? frameXmPool : jsonPool;
            sendPlaceSwimmingPosXRawFromTcpLocked(havePoolFrame, arrowPool);
            queuePlaceSwimmingPosXFromTcpFrameLocked(havePoolFrame, arrowPool);

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

                lastTotalDistance[lane] = totalDistanceFromSegment(passedSplits, xSeg);
            }
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (!haveRaw[lane]) {
                    continue;
                }
                Participant p = participants.size() > lane ? participants.get(lane) : null;
                lastRawTotalDistance[lane] = totalDistanceFromSegment(countFilledSplits(p), frameXmRaw[lane]);
                hasLastRawTotalDistance[lane] = true;
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

            // ORAD export is paced by video frame_id (one full distance pack per id).
            // Dir/rank stay change-only; place-swimming Position_X runs after the pack.
            if (controller != null) {
                logOradFrameTiming(exportFrameId, exportCaptureMs, sample.source);
                for (int lane = 0; lane < LANES_COUNT; lane++) {
                    double displayDistance = toDisplayedDistance(lastTotalDistance[lane]);
                    String distValue = String.format(Locale.US, "%.3f", displayDistance);
                    boolean needSendDist = DISTANCE_EXPORT_EPS <= 0.0
                            || !hasExportedDistance[lane]
                            || Math.abs(displayDistance - lastExportedDistance[lane]) > DISTANCE_EXPORT_EPS;
                    if (needSendDist) {
                        controller.sendSetExport(RE_SCENE, "distance_lane_" + lane, distValue);
                        lastExportedDistance[lane] = displayDistance;
                        hasExportedDistance[lane] = true;
                    }
                }
                for (int lane = 0; lane < LANES_COUNT; lane++) {
                    String dir = toDisplayedDirection(lastDirection[lane]);
                    if (dir == null) {
                        dir = "";
                    }
                    boolean needSendDir = lastExportedDirection[lane] == null
                            ? !dir.isEmpty()
                            : !lastExportedDirection[lane].equals(dir);
                    if (needSendDir) {
                        controller.sendSetExport(RE_SCENE, "directin_lane_" + lane, dir);
                        lastExportedDirection[lane] = dir;
                    }
                    if (allowRankUpdate) {
                        controller.sendSetExport(RE_SCENE, "position_lane_" + lane,
                                String.valueOf(displayedRanks[lane]));
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

    private double smoothSwimmerAxis(int lane, double measurement, double maxMeters,
                                     double[] position, double[] velocity, long[] stamp, boolean[] ready) {
        final long nowMs = System.currentTimeMillis();
        measurement = Math.max(0, Math.min(maxMeters, measurement));
        if (!ready[lane]) {
            position[lane] = measurement;
            velocity[lane] = 0;
            stamp[lane] = nowMs;
            ready[lane] = true;
            return position[lane];
        }
        final double dt = Math.max(0.02, Math.min(0.20, (nowMs - stamp[lane]) / 1000.0));
        stamp[lane] = nowMs;
        final double predicted = position[lane] + velocity[lane] * dt;
        final double error = measurement - predicted;
        final double gain = Math.abs(error) > 0.8 ? 0.12 : 0.45;
        double step = (predicted + gain * error) - position[lane];
        final double maxStep = 2.4 * dt;
        if (Math.abs(step) > maxStep) {
            step = Math.copySign(maxStep, step);
        }
        velocity[lane] = Math.max(-2.4, Math.min(2.4, 0.86 * velocity[lane] + 0.14 * (step / dt)));
        position[lane] = Math.max(0, Math.min(maxMeters, position[lane] + step));
        return position[lane];
    }

    private void updateSplitUiFromState() {
        synchronized (splitsStateLock) {
            double leaderDist = 0;
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                leaderDist = Math.max(leaderDist, displayedProgressForPlace(lane));
            }
            for (int lane = 0; lane < LANES_COUNT; lane++) {
                if (positionLabels[lane] != null) {
                    int rank = displayedRanks[lane];
                    positionLabels[lane].setText(rank > 0 ? String.valueOf(rank) : "");
                }
                if (distanceLabels[lane] != null) {
                    distanceLabels[lane].setText(String.format(Locale.US, "%.2f",
                            toDisplayedDistance(lastTotalDistance[lane])));
                }
                if (gpLabels[lane] != null) {
                    double gp = leaderDist - displayedProgressForPlace(lane);
                    gpLabels[lane].setText(String.format(Locale.US, "%.2f", gp));
                }
                if (directionLabels[lane] != null) {
                    String dir = toDisplayedDirection(lastDirection[lane]);
                    directionLabels[lane].setText(dir == null ? "" : dir);
                }
            }
        }
    }

    private void startVideoFrameMergeExecutor() {
        stopVideoFrameMergeExecutor();
        videoFrameMergeExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tcp-video-frame-merge");
            t.setDaemon(true);
            return t;
        });
        synchronized (videoFrameMergeLock) {
            trackerVideoSamples[0] = null;
            trackerVideoSamples[1] = null;
            cancelVideoFrameFlush(0);
            cancelVideoFrameFlush(1);
            lastAppliedVideoFrameId = Long.MIN_VALUE;
            lastAppliedCaptureEpochMs = Long.MIN_VALUE;
            lastOradLogFrameId = Long.MIN_VALUE;
            lastOradLogCaptureMs = Long.MIN_VALUE;
            lastOradLogWallMs = Long.MIN_VALUE;
        }
    }

    private void stopVideoFrameMergeExecutor() {
        synchronized (videoFrameMergeLock) {
            cancelVideoFrameFlush(0);
            cancelVideoFrameFlush(1);
            trackerVideoSamples[0] = null;
            trackerVideoSamples[1] = null;
        }
        ScheduledExecutorService exec = videoFrameMergeExecutor;
        videoFrameMergeExecutor = null;
        if (exec == null) {
            return;
        }
        exec.shutdownNow();
        try {
            exec.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cancelVideoFrameFlush(int source) {
        ScheduledFuture<?> f = trackerVideoFlushFutures[source];
        trackerVideoFlushFutures[source] = null;
        if (f != null) {
            f.cancel(false);
        }
    }

    private void scheduleVideoFrameFlush(int source, TrackerVideoSample sample) {
        ScheduledExecutorService exec = videoFrameMergeExecutor;
        if (exec == null || sample == null) {
            applyTrackerVideoSample(sample);
            return;
        }
        cancelVideoFrameFlush(source);
        final long gen = trackerVideoFlushGeneration.incrementAndGet();
        sample.flushGeneration = gen;
        int period = sample.nominalPeriodMs > 0 ? sample.nominalPeriodMs : DEFAULT_VIDEO_FRAME_PERIOD_MS;
        long waitMs = Math.max(5, Math.min(VIDEO_FRAME_MATCH_WAIT_MS, period / 2));
        trackerVideoFlushFutures[source] = exec.schedule(() -> {
            TrackerVideoSample toApply = null;
            synchronized (videoFrameMergeLock) {
                TrackerVideoSample pending = trackerVideoSamples[source];
                if (pending == null || pending.flushGeneration != gen) {
                    return;
                }
                trackerVideoSamples[source] = null;
                trackerVideoFlushFutures[source] = null;
                toApply = pending;
            }
            applyTrackerVideoSample(toApply);
        }, waitMs, TimeUnit.MILLISECONDS);
    }

    private boolean videoFramesMatch(TrackerVideoSample a, TrackerVideoSample b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.frameId != Long.MIN_VALUE && b.frameId != Long.MIN_VALUE) {
            return a.frameId == b.frameId;
        }
        if (a.captureEpochMs != Long.MIN_VALUE && b.captureEpochMs != Long.MIN_VALUE) {
            int period = Math.max(
                    a.nominalPeriodMs > 0 ? a.nominalPeriodMs : DEFAULT_VIDEO_FRAME_PERIOD_MS,
                    b.nominalPeriodMs > 0 ? b.nominalPeriodMs : DEFAULT_VIDEO_FRAME_PERIOD_MS);
            long tol = Math.max(5, period / 2);
            return Math.abs(a.captureEpochMs - b.captureEpochMs) <= tol;
        }
        return false;
    }

    private TrackerVideoSample mergeVideoSamples(TrackerVideoSample a, TrackerVideoSample b) {
        boolean[] seenLane = new boolean[LANES_COUNT];
        double[] frameX = new double[LANES_COUNT];
        boolean[] havePoolFrame = new boolean[LANES_COUNT];
        double[] frameXmPool = new double[LANES_COUNT];
        boolean[] haveRawFrame = new boolean[LANES_COUNT];
        double[] frameXmRaw = new double[LANES_COUNT];
        String[] frameDirections = new String[LANES_COUNT];
        for (int lane = 0; lane < LANES_COUNT; lane++) {
            boolean aSeen = a.seenLane[lane];
            boolean bSeen = b.seenLane[lane];
            if (aSeen && bSeen) {
                seenLane[lane] = true;
                frameX[lane] = 0.5 * (a.frameX[lane] + b.frameX[lane]);
                frameDirections[lane] = a.frameDirections[lane] != null
                        ? a.frameDirections[lane] : b.frameDirections[lane];
            } else if (aSeen) {
                seenLane[lane] = true;
                frameX[lane] = a.frameX[lane];
                frameDirections[lane] = a.frameDirections[lane];
            } else if (bSeen) {
                seenLane[lane] = true;
                frameX[lane] = b.frameX[lane];
                frameDirections[lane] = b.frameDirections[lane];
            }
            boolean aPool = a.havePoolFrame[lane];
            boolean bPool = b.havePoolFrame[lane];
            if (aPool && bPool) {
                havePoolFrame[lane] = true;
                frameXmPool[lane] = 0.5 * (a.frameXmPool[lane] + b.frameXmPool[lane]);
            } else if (aPool) {
                havePoolFrame[lane] = true;
                frameXmPool[lane] = a.frameXmPool[lane];
            } else if (bPool) {
                havePoolFrame[lane] = true;
                frameXmPool[lane] = b.frameXmPool[lane];
            }
            boolean aRaw = a.haveRaw[lane];
            boolean bRaw = b.haveRaw[lane];
            if (aRaw && bRaw) {
                haveRawFrame[lane] = true;
                frameXmRaw[lane] = 0.5 * (a.frameXmRaw[lane] + b.frameXmRaw[lane]);
            } else if (aRaw) {
                haveRawFrame[lane] = true;
                frameXmRaw[lane] = a.frameXmRaw[lane];
            } else if (bRaw) {
                haveRawFrame[lane] = true;
                frameXmRaw[lane] = b.frameXmRaw[lane];
            }
        }
        Long frameId = a.frameId != Long.MIN_VALUE ? a.frameId
                : (b.frameId != Long.MIN_VALUE ? b.frameId : null);
        Long captureMs = a.captureEpochMs != Long.MIN_VALUE ? a.captureEpochMs
                : (b.captureEpochMs != Long.MIN_VALUE ? b.captureEpochMs : null);
        if (a.captureEpochMs != Long.MIN_VALUE && b.captureEpochMs != Long.MIN_VALUE) {
            captureMs = (a.captureEpochMs + b.captureEpochMs) / 2;
        }
        Long procMs = a.processingEpochMs != Long.MIN_VALUE ? a.processingEpochMs
                : (b.processingEpochMs != Long.MIN_VALUE ? b.processingEpochMs : null);
        Integer period = a.nominalPeriodMs > 0 ? a.nominalPeriodMs
                : (b.nominalPeriodMs > 0 ? b.nominalPeriodMs : null);
        return TrackerVideoSample.fromParsed(
                a.source, frameId, captureMs, procMs, period,
                a.interpolated || b.interpolated,
                seenLane, frameX, havePoolFrame, frameXmPool, haveRawFrame, frameXmRaw, frameDirections);
    }

    private Long extractLong(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long parseIsoToEpochMs(String iso) {
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        // Qt ISODateWithMs may omit zone — treat as UTC.
        if (!iso.endsWith("Z") && !iso.contains("+") && !iso.matches(".*-\\d{2}:\\d{2}$")) {
            try {
                return Instant.parse(iso + "Z").toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        try {
            return LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
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

    /** One tracker TCP JSON message keyed for video-frame merge. */
    private static final class TrackerVideoSample {
        int source;
        long frameId = Long.MIN_VALUE;
        long captureEpochMs = Long.MIN_VALUE;
        long processingEpochMs = Long.MIN_VALUE;
        int nominalPeriodMs;
        boolean interpolated;
        long flushGeneration;
        final boolean[] seenLane = new boolean[LANES_COUNT];
        final double[] frameX = new double[LANES_COUNT];
        final boolean[] havePoolFrame = new boolean[LANES_COUNT];
        final double[] frameXmPool = new double[LANES_COUNT];
        final boolean[] haveRaw = new boolean[LANES_COUNT];
        final double[] frameXmRaw = new double[LANES_COUNT];
        final String[] frameDirections = new String[LANES_COUNT];

        static TrackerVideoSample fromParsed(
                int source, Long frameId, Long captureEpochMs, Long processingEpochMs,
                Integer nominalPeriodMs, boolean interpolated,
                boolean[] seenLane, double[] frameX, boolean[] havePoolFrame,
                double[] frameXmPool, boolean[] haveRaw, double[] frameXmRaw,
                String[] frameDirections) {
            TrackerVideoSample s = new TrackerVideoSample();
            s.source = source;
            if (frameId != null) {
                s.frameId = frameId;
            }
            if (captureEpochMs != null) {
                s.captureEpochMs = captureEpochMs;
            }
            if (processingEpochMs != null) {
                s.processingEpochMs = processingEpochMs;
            }
            s.nominalPeriodMs = nominalPeriodMs != null ? nominalPeriodMs : 0;
            s.interpolated = interpolated;
            System.arraycopy(seenLane, 0, s.seenLane, 0, LANES_COUNT);
            System.arraycopy(frameX, 0, s.frameX, 0, LANES_COUNT);
            System.arraycopy(havePoolFrame, 0, s.havePoolFrame, 0, LANES_COUNT);
            System.arraycopy(frameXmPool, 0, s.frameXmPool, 0, LANES_COUNT);
            System.arraycopy(haveRaw, 0, s.haveRaw, 0, LANES_COUNT);
            System.arraycopy(frameXmRaw, 0, s.frameXmRaw, 0, LANES_COUNT);
            System.arraycopy(frameDirections, 0, s.frameDirections, 0, LANES_COUNT);
            return s;
        }
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
        splits.setVgap(SPLITS_TABLE_VGAP);
        configureSplitsGridRowConstraints();
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
                lastFilteredXmPool[lane] = 0;
                hasLastFilteredXmPool[lane] = false;
                lastRawTotalDistance[lane] = 0;
                hasLastRawTotalDistance[lane] = false;
                lastDirection[lane] = null;
                smoothSegReady[lane] = false;
                smoothPoolReady[lane] = false;
                smoothSegVel[lane] = 0;
                smoothPoolVel[lane] = 0;

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
            newDistanceLabel.setMinWidth(70);
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
                newTextField.setMinWidth(70);
                newTextField.setPrefHeight(SPLITS_TABLE_ROW_HEIGHT);
                newTextField.setMinHeight(SPLITS_TABLE_ROW_HEIGHT);
                newTextField.setMaxHeight(SPLITS_TABLE_ROW_HEIGHT);
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
        splits.setMaxWidth(Region.USE_COMPUTED_SIZE);
        scrollPaneSplits.setFitToWidth(false);
        scrollPaneSplits.setFitToHeight(false);
        scrollPaneSplits.setContent(splits);
        updateSplitsScrollPaneHeight();

        if (controller != null && leadersPlaceSwimmingTracking) {
            sendPlaceSwimmingAllGroupMasterVisible(true);
            synchronized (splitsStateLock) {
                resetPlaceSwimmingLeaderExportCacheLocked();
                applyPlaceSwimmingLeaderExportsLocked(true);
            }
        }
    }

    private void configureSplitsGridRowConstraints() {
        splits.getRowConstraints().clear();
        for (int row = 0; row < SPLITS_TABLE_ROW_COUNT; row++) {
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setMinHeight(SPLITS_TABLE_ROW_HEIGHT);
            rowConstraints.setPrefHeight(SPLITS_TABLE_ROW_HEIGHT);
            rowConstraints.setMaxHeight(SPLITS_TABLE_ROW_HEIGHT);
            splits.getRowConstraints().add(rowConstraints);
        }
    }

    private double computeSplitsTableContentHeight() {
        return SPLITS_TABLE_ROW_COUNT * SPLITS_TABLE_ROW_HEIGHT
                + (SPLITS_TABLE_ROW_COUNT - 1) * SPLITS_TABLE_VGAP;
    }

    private void updateSplitsScrollPaneHeight() {
        if (scrollPaneSplits == null || splits == null) {
            return;
        }
        Runnable applyHeight = () -> {
            double contentHeight = computeSplitsTableContentHeight();
            scrollPaneSplits.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPaneSplits.setPrefViewportHeight(contentHeight);
            scrollPaneSplits.setMinViewportHeight(contentHeight);
            double paneHeight = contentHeight + SPLITS_TABLE_HSCROLL_RESERVE + SPLITS_TABLE_PANE_PADDING;
            scrollPaneSplits.setPrefHeight(paneHeight);
            scrollPaneSplits.setMinHeight(paneHeight);
            scrollPaneSplits.setMaxHeight(paneHeight);
        };
        if (Platform.isFxApplicationThread()) {
            applyHeight.run();
        } else {
            Platform.runLater(applyHeight);
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


    private void initDefaultProperties() {
        properties.setProperty("reAddress", "localhost");
        properties.setProperty("reCanvas", "Canvas1");
        properties.setProperty("serialPort", "COM1");
        properties.setProperty("serialSpeed", "9600");
        properties.setProperty("serialDataBits", "8");
        properties.setProperty("serialStopBits", "1");
        properties.setProperty("serialParity", "None");
        properties.setProperty("serialFlow", "XON/XOFF");
        properties.setProperty("encoding", "UTF-8");
        properties.setProperty("tcpHost", "127.0.0.1");
        properties.setProperty("tcpPort", "9000");
        properties.setProperty("tcpHost2", "127.0.0.1");
        properties.setProperty("tcpPort2", "");
        properties.setProperty("splitsTcpServerPort", "9100");
        properties.setProperty("tracksTcpServerPort", "9101");
        properties.setProperty("oradPositionXMode", ORAD_POS_MODE_BUFFERED_25HZ);
        properties.setProperty("oradPosXOffsetEnabled", "false");
        properties.setProperty("oradPosXOffset", "0");
        properties.setProperty("coordinateLogToFile", "false");
        properties.setProperty("oradArrowSmooth", "true");
        properties.setProperty("placeFromRaw", "false");
    }

    private void applyLoadedPropertiesToUi() {
        reAddress.setText(properties.getProperty("reAddress", "localhost"));
        reCanvas.setText(properties.getProperty("reCanvas", "Canvas1"));
        portField.setText(properties.getProperty("serialPort", "COM1"));
        speedComboBox.getSelectionModel().select(properties.getProperty("serialSpeed", "9600"));
        dataBitsComboBox.getSelectionModel().select(properties.getProperty("serialDataBits", "8"));
        stopBitsComboBox.getSelectionModel().select(properties.getProperty("serialStopBits", "1"));
        parityComboBox.getSelectionModel().select(properties.getProperty("serialParity", "None"));
        flowControlComboBox.getSelectionModel().select(properties.getProperty("serialFlow", "XON/XOFF"));
        encodingComboBox.getSelectionModel().select(properties.getProperty("encoding", "UTF-8"));
    }

    private void loadProperties() {
        File configFile = new File(CONFIG_PROPERTIES_FILE);
        if (!configFile.exists()) {
            initDefaultProperties();
            writeProperties();
        } else {
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
            } catch (IOException ex) {
                ex.printStackTrace();
                initDefaultProperties();
                writeProperties();
            }
        }
        applyLoadedPropertiesToUi();
    }
    private void setProperties(String key, String value) {
        properties.setProperty(key, value);
        writeProperties();
    }

    private void writeProperties() {
        try (OutputStream output = new FileOutputStream(CONFIG_PROPERTIES_FILE)) {

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
