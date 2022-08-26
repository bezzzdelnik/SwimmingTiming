package model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Participant {
    private final StringProperty name;
    private final StringProperty place;
    private final IntegerProperty splitCount;
    private ObservableList<Split> splits = FXCollections.observableArrayList();

    public Participant() {
        this.name = new SimpleStringProperty("");
        this.place = new SimpleStringProperty("");
        this.splitCount = new SimpleIntegerProperty(0);
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

    public String getPlace() {
        return place.get();
    }

    public StringProperty placeProperty() {
        return place;
    }

    public void setPlace(String place) {
        this.place.set(place);
    }

    public int getSplitCount() {
        return splitCount.get();
    }

    public IntegerProperty splitCountProperty() {
        return splitCount;
    }

    public void setSplitCount(int splitCount) {
        this.splitCount.set(splitCount);
    }

    public ObservableList<Split> getSplits() {
        return splits;
    }

    public void setSplits(ObservableList<Split> splits) {
        this.splits = splits;
    }
}
