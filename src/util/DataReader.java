package util;

import com.sun.org.apache.bcel.internal.generic.SWITCH;
import controllers.RootLayoutController;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import model.Participant;


public class DataReader {
    static String STX = "\u0002";
    static String EOT = "\u0004";
    static String SOH = "\u0001";
    static String DC4 = "\u0014";
    static String BS = "\b";

    static String DC1 = "\u0011";

    static String startList = "SOH\u001198STXSLH";


    private static String bf;

    private boolean recordLineIsShowed = false;

    private ObservableList<Participant> participants;
    private RootLayoutController rootLayoutController;

    public DataReader(ObservableList<Participant> participants, RootLayoutController rootLayoutController) {
        this.participants = participants;
        this.rootLayoutController = rootLayoutController;
    }

    public void parseData(String data) {

        bf += data;
        if (bf.contains(SOH) && bf.contains(EOT)) {
            readData(bf.replaceAll("[\r\n]", ""));
        }

    }


    private String oldOutput;
    public void readData(String buffer) {
        String pattern = "(?=[\u0001])";
        String[] buf = buffer.split(pattern);
        String bufferSOH = "";
        String bufferEOT = "";


        bf = buf[buf.length - 1];

        String output = "";

        for (int i = 0; i < buf.length; i++) {
            String b = buf[i];

            if (!b.isEmpty()) {
                if (b.contains(SOH) && b.contains(EOT)) {
                    output = b.
                            replaceAll(STX, "STX").replaceAll(EOT, "EOT").
                            replaceAll(SOH, "SOH").replaceAll(DC4, "DC4").
                            replaceAll(BS, "BS");

                } else if (b.contains(SOH) && !b.contains(EOT)) {
                    bufferSOH = b;


                } else if (!b.contains(SOH) && b.contains(EOT)) {
                    bufferEOT = b;

                }
                if ((bufferSOH+bufferEOT).contains(SOH) && (bufferSOH+bufferEOT).contains(EOT)) {
                    output = (bufferSOH+bufferEOT).replaceAll(STX, "STX").replaceAll(EOT, "EOT").
                            replaceAll(SOH, "SOH").replaceAll(DC4, "DC4").
                            replaceAll(BS, "BS");
                    bufferSOH = "";
                    bufferEOT = "";

                }
            }
            System.out.println(output);
            //System.out.println(oldOutput);
            if (!output.equals(oldOutput)) {
                readTimer(output);
                readStartList(output);
                readSplits(output);
                oldOutput = output;
            }

        }
    }


    public void readTimer(String output) {
        if (output.contains("SOHDC4R02STXBS1") || output.contains("SOHDC4R02STXBS")) {
            String timer = output.split("\\s+")[2];
            new Thread(() -> Platform.runLater(() -> rootLayoutController.timerLabel.setText(timer))).start();
            if (timer.equals("0.1")) {
                startRecordLine();
            }
        }

    }

    public void readStartList(String output) {
        if (output.contains("SOH\u001198STXTLH") && rootLayoutController.automaticDistanceButton.selectedProperty().get()) {
            automaticSwitchDistance(output);
        }

        if (output.contains("SOH\u001198STXSTARTEOT")) {
            resetTable();
        }

        if (output.contains("SOHDC4L0EOT")) {
            resetTable();
        }

        if (output.contains(startList)) {
            new Thread(() -> Platform.runLater(() -> {
                participants.get(Integer.parseInt(output.replaceAll("\\|", "").split("\\s+")[2])).
                        setName(output.replaceAll("\\||EOT", "").split("\\s+", 5)[4]);
            })).start();
        }

    }

    private void resetTable() {
        new Thread(() -> Platform.runLater(() -> {
            rootLayoutController.createGridPaneSplits();
            rootLayoutController.firstPlaceText.clear();
            rootLayoutController.secondPlaceText.clear();
            rootLayoutController.thirdPlaceText.clear();
            rootLayoutController.timerLabel.setText("0.0");
        })).start();
    }

    private void automaticSwitchDistance(String output){
        String distance = output.split("\\|")[6].split(" ")[0];
        String distance2 = output.split("\\|")[6].split(" ")[2];
        System.out.println(distance);

        if (!distance.contains("m")) {
            int dist1 = Integer.parseInt(distance);
            int dist2 = Integer.parseInt(distance2.replaceAll("m", ""));
            distance = String.valueOf(dist1 * dist2);
            System.out.println(Integer.parseInt(distance));
        } else distance = distance.replaceAll("m", "");
        switch (distance) {
            case ("50") :
                rootLayoutController.radio50Distance.selectedProperty().set(true);
                break;
            case ("100") :
                rootLayoutController.radio100Distance.selectedProperty().set(true);
                break;
            case ("200") :
                rootLayoutController.radio200Distance.selectedProperty().set(true);
                break;
            case ("400") :
                rootLayoutController.radio400Distance.selectedProperty().set(true);
                break;
            case ("800") :
                rootLayoutController.radio800Distance.selectedProperty().set(true);
                break;
            case ("1500") :
                rootLayoutController.radio1500Distance.selectedProperty().set(true);
                break;
        }
    }

    private String splitOutput;

    public void readSplits(String output) {

        if (output.contains("SOHDC4S02STXBS") && !output.equals(splitOutput)) {
            splitOutput = output;
            System.out.println(output);
            if (output.split("\\s+").length == 5) {
                int place = 0;
                int lane = -1;
                String time = output.split("\\s+")[3];
                String placeString = output.split("\\s+")[0].replaceAll("SOHDC4S02STXBS", "");

                try {
                    place = Integer.parseInt(placeString);
                } catch (NumberFormatException e) {
                    System.out.println(e);
                }

                try {
                    lane = Integer.parseInt(output.split("\\s+")[2]);
                } catch (NumberFormatException e) {
                    System.out.println(e);
                }

                if (lane >= 0 && !participants.get(lane).getSplits().contains(time)) {
                    int splitCount  = participants.get(lane).getSplitCount();
                    if (participants.get(lane).getSplits().get(splitCount).getSpl().equals("")) {
                        participants.get(lane).getSplits().get(splitCount).setSpl(time);

                        if (splitCount < participants.get(lane).getSplits().size() - 1) {
                            participants.get(lane).setSplitCount(splitCount+1);
                        }

                        if(splitCount == participants.get(lane).getSplits().size() - 1){
                            participants.get(lane).setPlace(place);
                            if (place <= 3 && place != 0) {
                                showLeaders(lane, place);
                            }
                        }
                    }
                }
            }

        }
    }

    private void showLeaders(int lane, int place) {
        //rootLayoutController.getController().sendSetExport("Olympic/swimming", "_numbers_mode", "0");
        rootLayoutController.getController().sendSetExport("Olympic/swimming", "number_" + (lane + 1), String.valueOf(place));
        rootLayoutController.getController().sendAnimationPlay("Olympic/swimming", "swimmer_in_" + (lane + 1));
        participants.get(lane).setIsShowed(true);
        new Thread(() -> Platform.runLater(() ->{
            switch (place) {
                case (1):
                    rootLayoutController.firstPlaceText.setText("Lane " + lane + "   " + participants.get(lane).getName());
                    break;
                case (2):
                    rootLayoutController.secondPlaceText.setText("Lane " + lane + "   " + participants.get(lane).getName());
                    break;
                case (3):
                    rootLayoutController.thirdPlaceText.setText("Lane " + lane + "   " + participants.get(lane).getName());
                    break;
                default: break;
            }
            System.out.println("place " + place + "   lane " + lane + "   " + participants.get(lane).getName() + " send to RE");
        })).start();

    }

    private void startRecordLine(){
        rootLayoutController.getController().sendAnimationPlay("Olympic/swimming", "swim");
    }

}
