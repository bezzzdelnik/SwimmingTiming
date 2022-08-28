package util;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Constants {



    public static final String LABEL_FONT = "-fx-font-size: 13pt;";
    public static final String BUTTON_FONT = "-fx-font-size: 11pt;";
    public static final String STATUS_FONT_RED = "-fx-font-size: 12pt; -fx-text-fill: red;";
    public static final String STATUS_FONT_GREEN = "-fx-font-size: 12pt; -fx-text-fill: green;";
    public static final String STATUS_FONT_BLUE = "-fx-font-size: 12pt; -fx-text-fill: blue;";
    public static final String STATUS_FONT_ORANGE = "-fx-font-size: 12pt; -fx-text-fill: orange;";
    public static final String BUTTON_ACTIVE = "-fx-background-color: cyan;";
    public static final String BUTTON_INACTIVE = "-fx-background-color: yellow;";

    public static final String APPLICATION_TITLE = "Wisehockey observer";
    public static final String COMMON_ERROR = "Error, check logs or console";
    public static final String START = "Start";
    public static final String STOP = "Stop";
    public static final String DEFAULT_TEAM_SIZE = "5";

    public static final String PLAYERS = "players";
    public static final String SHOT = "shot";

    public static final String LOCALHOST = "127.0.0.1";

    public static final Pattern PLAYER_IDS_PATTERN = Pattern.compile("\\d+(-\\d+)?(,\\d+(-\\d+)?)*");
    public static final Pattern PORT_PATTERN = Pattern.compile("\\d{2,5}");
    public static final Pattern INTEGER = Pattern.compile("\\d+");

    public static final int REQUEST_TIMEOUT = 20 * 1000;

    public static final String OBSERVERS_PLAYERS_PORT = "wise_hockey_observer__observers__players_port";
    public static final String OBSERVERS_WEIGHT_X = "wise_hockey_observer__observers__weight_x";
    public static final String OBSERVERS_WEIGHT_Y = "wise_hockey_observer__observers__weight_y";
    public static final String OBSERVERS_DATA_TTL = "wise_hockey_observer__observers__data_ttl";
    public static final String FAKE_DATA_TTL = "wise_hockey_observer__observers__fake_data_ttl";
    public static final String FAKE_DATA_PERIOD = "wise_hockey_observer__observers__fake_data_period";
    public static final String RE_HOST = "wise_hockey_observer__re__re_host";
    public static final String RE_SCENE = "wise_hockey_observer__re__scene";
    public static final String RE_PORT = "wise_hockey_observer__re__port";
    public static final String RE_X_POS = "wise_hockey_observer__re__x_pos_export";
    public static final String RE_Y_POS = "wise_hockey_observer__re__y_pos_export";
    public static final String RE_VISIBILITY = "wise_hockey_observer__re__visibility_export";
    public static final String RE_NUMBER = "wise_hockey_observer__re__number_export";
    public static final String RE_FIRST_NAME = "wise_hockey_observer__re__first_name_export";
    public static final String RE_LAST_NAME = "wise_hockey_observer__re__last_name_export";
    public static final String RE_STAT = "wise_hockey_observer__re__stat_export";
    public static final String RE_STAT_VISIBLE = "wise_hockey_observer__re__stat_visible_export";
    public static final String RE_COLOR_RED = "wise_hockey_observer__re__color_red_export";
    public static final String RE_COLOR_GREEN = "wise_hockey_observer__re__color_green_export";
    public static final String RE_COLOR_BLUE = "wise_hockey_observer__re__color_blue_export";
    public static final String SUBSCRIBER_API_KEY = "wise_hockey_observer__subscriber__api_key";
    public static final String SUBSCRIBER_WISE_HOST = "wise_hockey_observer__subscriber__wise_host";
    public static final String SUBSCRIBER_RECEIVER_HOST = "wise_hockey_observer__subscriber__receiver_host";

    public static final Map<String, String> PREF_DEFAULT = Arrays.stream(new String[][] {
            { OBSERVERS_PLAYERS_PORT, "1234" },
            { OBSERVERS_WEIGHT_X, "0.71" },
            { OBSERVERS_WEIGHT_Y, "0.71" },
            { OBSERVERS_DATA_TTL, "2000" },
            { FAKE_DATA_TTL, "2000" },
            { FAKE_DATA_PERIOD, "40" },
            { RE_HOST, "10.10.15.85" },
            { RE_SCENE, "ALLSTAR-2020/PLAYERS" },
            { RE_PORT, "1" },
            { RE_X_POS, "Transformation_PLAYER-GROUP-%_Position_X" },
            { RE_Y_POS, "Transformation_PLAYER-GROUP-%_Position_Z" },
            { RE_VISIBILITY, "Object_PLAYER-GROUP-%_Object_Visible" },
            { RE_NUMBER, "Geometry_NUMBER-%_Input_String" },
            { RE_FIRST_NAME, "Geometry_Name-%_Input_String" },
            { RE_LAST_NAME, "Geometry_Last-Name-%_Input_String" },
            { RE_STAT, "Geometry_STAT-%_Input_String" },
            { RE_STAT_VISIBLE, "Object_STAT-GROUP-%_Object_Visible" },
            { RE_COLOR_RED, "Color_COLOR-%_Color_Red" },
            { RE_COLOR_GREEN, "Color_COLOR-%_Color_Green" },
            { RE_COLOR_BLUE, "Color_COLOR-%_Color_Blue" },
            { SUBSCRIBER_API_KEY, "nobRBzRV1yIJIIaXlubUW9YnJdO58aTw7toUmch7ZBQabraaisnYL4Speo9NXGXV5MUOZn6NU5zIQHNk" },
            { SUBSCRIBER_WISE_HOST, "http://192.168.0.1" },
            { SUBSCRIBER_RECEIVER_HOST, "192.168.0.2" }
    }).collect(Collectors.toMap(key -> key[0], value -> value[1]));
}
