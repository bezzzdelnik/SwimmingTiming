package util;

import controllers.RootLayoutController;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import model.Participant;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class LogReader {
    static String STX = "\u0002";
    static String EOT = "\u0004";
    static String SOH = "\u0001";
    static String DC4 = "\u0014";
    static String BS = "\b";
    private static String bf;

    private Label timerLabel;
    private GridPane splits;
    private ObservableList<Participant> participants;
    private RootLayoutController rootLayoutController;

    public LogReader(Label timerLabel, GridPane splits, ObservableList<Participant> participants, RootLayoutController rootLayoutController) {
        this.timerLabel = timerLabel;
        this.splits = splits;
        this.participants = participants;
        this.rootLayoutController = rootLayoutController;
    }

    public void readeFile() {

        File file = new File("src/util/puttySwimmingFull.log");

        try (FileInputStream fis = new FileInputStream(file)) {

            // remaining bytes that can be read
            System.out.println("Remaining bytes that can be read : " + fis.available());

            // 8 a time
            byte[] bytes = new byte[1000];

            // reads 8192 bytes at a time, if end of the file, returns -1
            while (fis.read(bytes) != -1) {
                bf += new String(bytes);
                if (bf.contains(SOH) && bf.contains(EOT)) {
                    readData(bf.replaceAll("\n", ""));



                    //Thread.sleep(100);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void readData(String buffer) {
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
           try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            readTimer(output);
            readStartList(output);
            readSplits(output);
            //System.out.println(output);
        }
    }


    public void readTimer(String output) {
        if (output.contains("SOHDC4R02STXBS1")) {
            String tmp = output.split("\\s+")[2];
            new Thread(() -> Platform.runLater(() -> timerLabel.setText(tmp))).start();
            //System.out.println(tmp);
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
            //System.out.println(output.replaceAll("\\||EOT", "").split("\\s+", 5)[4]);
        }

    }

    public void readSplits(String output) {
        if (output.contains("SOHDC4S02STXBS")) {
            if (output.split("\\s+").length == 5) {
                int lane = Integer.parseInt(output.split("\\s+")[2]);
                String time = output.split("\\s+")[3];
                String place = output.split("\\s+")[0].replaceAll("SOHDC4S02STXBS", "");
                int splitCount = participants.get(lane).getSplitCount();
                System.out.println(participants.get(lane).getSplits().size() + " SPLIT SIZE");

                if (participants.get(lane).getSplits().get(splitCount).getSpl().equals("")) {
                    participants.get(lane).getSplits().get(splitCount).setSpl(time);

                    if (splitCount < participants.get(lane).getSplits().size() - 1) {
                        participants.get(lane).setSplitCount(splitCount+1);

                    }
                    if(splitCount == participants.get(lane).getSplits().size() - 1){
                        new Thread(() -> Platform.runLater(() ->
                                participants.get(lane).setName(place + " " + participants.get(lane).getName()))).start();

                    }
                    System.out.println(participants.get(lane).getSplitCount());
                }


                System.out.println("Place - " + place + " " +"Lane - " + lane + " " + "Time - " + time);

            }

        }
    }
}
