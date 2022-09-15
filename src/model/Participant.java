package model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Participant {
    private final StringProperty name;
    private final IntegerProperty place;
    private final IntegerProperty splitCount;
    private ObservableList<Split> splits = FXCollections.observableArrayList();
    private final BooleanProperty showed;

    public Participant() {
        this.name = new SimpleStringProperty("");
        this.place = new SimpleIntegerProperty(0);
        this.splitCount = new SimpleIntegerProperty(0);
        this.showed = new SimpleBooleanProperty(false);
    }

    public boolean isShowed() {
        return showed.get();
    }

    public BooleanProperty isShowedProperty() {
        return showed;
    }

    public void setIsShowed(boolean showed) {
        this.showed.set(showed);
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

    public int getPlace() {
        return place.get();
    }

    public IntegerProperty placeProperty() {
        return place;
    }

    public void setPlace(int place) {
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
