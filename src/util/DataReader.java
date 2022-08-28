package util;

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
    private static String bf;

    private ObservableList<Participant> participants;
    private RootLayoutController rootLayoutController;

    public DataReader(ObservableList<Participant> participants, RootLayoutController rootLayoutController) {
        this.participants = participants;
        this.rootLayoutController = rootLayoutController;
    }

    public void readeFile(String data) {

        bf += data;
        if (bf.contains(SOH) && bf.contains(EOT)) {
            readData(bf.replaceAll("\n", ""));
        }


    }

    public void readData(String buffer) {
        String pattern = "(?=[\u0001])";
        String[] buf = buffer.split(pattern);
        String bufferSOH = "";
        String bufferEOT = "";


        bf = buf[buf.length - 1];

        String output = "";

        for (int i = 0; i < buf.length - 1; i++) {
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
            readTimer(output);
            readStartList(output);
            readSplits(output);
        }
    }


    public void readTimer(String output) {
        if (output.contains("SOHDC4R02STXBS1")) {
            String tmp = output.split("\\s+")[2];
            new Thread(() -> Platform.runLater(() -> rootLayoutController.timerLabel.setText(tmp))).start();
        }

    }

    public void readStartList(String output) {
        if (output.contains("SOH98STXSTARTEOT")) {
            new Thread(() -> Platform.runLater(() -> rootLayoutController.createGridPaneSplits())).start();
        }

        if (output.contains("SOH98STXSLH")) {
            new Thread(() -> Platform.runLater(() -> {
                participants.get(Integer.parseInt(output.replaceAll("\\|", "").split("\\s+")[2])).
                        setName(output.replaceAll("\\||EOT", "").split("\\s+", 5)[4]);
            })).start();
        }

    }

    public void readSplits(String output) {
        if (output.contains("SOHDC4S02STXBS")) {
            if (output.split("\\s+").length == 5) {
                int lane = Integer.parseInt(output.split("\\s+")[2]);
                String time = output.split("\\s+")[3];
                String place = output.split("\\s+")[0].replaceAll("SOHDC4S02STXBS", "");
                int splitCount = participants.get(lane).getSplitCount();

                if (participants.get(lane).getSplits().get(splitCount).getSpl().equals("")) {
                    participants.get(lane).getSplits().get(splitCount).setSpl(time);

                    if (splitCount < participants.get(lane).getSplits().size() - 1) {
                        participants.get(lane).setSplitCount(splitCount+1);

                    }
                    if(splitCount == participants.get(lane).getSplits().size() - 1){
                        new Thread(() -> Platform.runLater(() ->
                                participants.get(lane).setName(place + " " + participants.get(lane).getName()))).start();

                    }
                }


                System.out.println("Place - " + place + " " +"Lane - " + lane + " " + "Time - " + time);

            }

        }
    }
}
