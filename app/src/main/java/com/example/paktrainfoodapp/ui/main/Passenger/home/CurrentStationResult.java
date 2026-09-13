package com.example.paktrainfoodapp.ui.main.Passenger.home;


public class CurrentStationResult {

    private String stationName;
    private int stationIndex;
    private float distance;

    public CurrentStationResult() {
    }

    public CurrentStationResult(String stationName,
                                int stationIndex,
                                float distance) {

        this.stationName = stationName;
        this.stationIndex = stationIndex;
        this.distance = distance;
    }

    public String getStationName() {
        return stationName;
    }

    public int getStationIndex() {
        return stationIndex;
    }

    public float getDistance() {
        return distance;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public void setStationIndex(int stationIndex) {
        this.stationIndex = stationIndex;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }
}