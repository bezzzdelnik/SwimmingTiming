package model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Split {
    private StringProperty spl;

    public Split() {
        this.spl = new SimpleStringProperty("");
    }

    public String getSpl() {
        return spl.get();
    }

    public StringProperty splProperty() {
        return spl;
    }

    public void setSpl(String spl) {
        this.spl.set(spl);
    }
}
