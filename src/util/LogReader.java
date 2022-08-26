package util;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

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

    public void setTimerLabel(Label timerLabel) {
        this.timerLabel = timerLabel;
    }

    public void readeFile() {

        File file = new File("src/util/puttySwimmingFull.log");
        //BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "Cp1251"));

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
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            readTimer(output);
            //System.out.println(output);
        }
    }
    public void setText() {
        timerLabel.setText("fdfdfdfd");
    }

    public void readTimer(String output) {
        if (output.contains("SOHDC4R02STXBS1")) {
            String tmp = output.split("\\s+")[2];
            timerLabel.setText(tmp);
            System.out.println(tmp);
        }

    }

    public void readStartList(String output) {
        if (output.contains("SOH98STXSLH")) {
            System.out.println(output.replaceAll("\\||EOT", "").split("\\s+", 5)[4]);
        }

    }

    public void readSplits(String output) {
        if (output.contains("SOHDC4S02STXBS")) {
            if (output.split("\\s+").length == 5) {
                System.out.println("Place - " + output.split("\\s+")[0].replaceAll("SOHDC4S02STXBS", "") + " " +
                        "Lane - " + output.split("\\s+")[2] + " " + "Time - " + output.split("\\s+")[3]);
            }

        }
    }
}
