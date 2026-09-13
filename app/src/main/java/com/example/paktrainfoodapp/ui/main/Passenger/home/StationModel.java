package com.example.paktrainfoodapp.ui.main.Passenger.home;


public class StationModel {

    private String name;
    private double lat;
    private double lng;

    public StationModel() {
        // Required for Firestore
    }

    public StationModel(String name, double lat, double lng) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }
}