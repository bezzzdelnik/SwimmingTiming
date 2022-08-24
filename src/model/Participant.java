package model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Participant {
    private final StringProperty name;
    private ObservableList<String> splits = FXCollections.observableArrayList();

    public Participant() {
        this.name = new SimpleStringProperty("");
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public ObservableList<String> getSplits() {
        return splits;
    }

    public void setSplits(ObservableList<String> splits) {
        this.splits = splits;
    }
}
