package com.example.paktrainfoodapp.ui.main;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;

public class FirebaseSeeder {

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ================= RUN ALL =================
    public void seedAll() {
        uploadStations();
        uploadRoutes();
        uploadTrains();
    }

    private DocumentReference root() {
        return db.collection("RailwaySystem").document("main");
    }

    private void uploadStations() {

        // ================== Karachi Division ==================
        addStation("KarachiCantt", 24.8543, 67.0099);
        addStation("DrighRoad", 24.9018, 67.1604);
        addStation("LandhiJn", 24.8427, 67.2056);
        addStation("Jungshahi", 25.4348, 67.7775);
        addStation("KotriJn", 25.3663, 68.3083);
        addStation("HyderabadJn", 25.3960, 68.3578);
        addStation("TandoAdamJn", 25.7685, 68.6612);
        addStation("Shahdadpur", 25.9252, 68.6227);
        addStation("Nawabshah", 26.2458, 68.4103);
        addStation("Bandhi", 26.5888, 68.3027);
        addStation("PadidanJn", 26.7743, 68.3002);
        addStation("Mehrabpur", 27.0938, 68.4204);
        addStation("RohriJn", 27.6837, 68.8952);
        addStation("PanoAqil", 27.8568, 69.1114);
        addStation("Ghotki", 28.0062, 69.3164);
        addStation("MirpurMathelo", 28.0219, 69.5499);

        // ================== South Punjab ==================
        addStation("Sadiqabad", 28.3078, 70.1268);
        addStation("RahimYarKhan", 28.4207, 70.2989);
        addStation("KhanpurJn", 28.6483, 70.6577);
        addStation("Bahawalpur", 29.3956, 71.6836);
        addStation("LodhranJn", 29.5404, 71.6335);
        addStation("MultanCantt", 30.1998, 71.4753);
        addStation("KhanewalJn", 30.3018, 71.9320);
        addStation("ShorkotJn", 30.5001, 72.4010);
        addStation("Chichawatni", 30.5337, 72.6915);
        addStation("Harappa", 30.6298, 72.8735);
        addStation("Sahiwal", 30.6682, 73.1114);
        addStation("Okara", 30.8081, 73.4458);
        addStation("RenalaKhurd", 30.8784, 73.5984);
        addStation("TobaTekSingh", 30.9730, 72.4820);
        addStation("Chunian", 30.9662, 73.9794);
        addStation("Pattoki", 31.0187, 73.8510);
        addStation("KasurJn", 31.1165, 74.4504);
        addStation("Gojra", 31.1493, 72.6862);
        addStation("RaiwindJn", 31.2483, 74.2152);
        addStation("Faisalabad", 31.4180, 73.0791);
        addStation("KotLakhpat", 31.4684, 74.3295);
        addStation("LahoreCantt", 31.5209, 74.4030);
        addStation("ChakJhumra", 31.5683, 73.1837);
        addStation("LahoreJn", 31.5655, 74.3414);
        addStation("Mughalpura", 31.5907, 74.3774);
        addStation("ShahdaraBagh", 31.6218, 74.2865);
        addStation("KalaShahKaku", 31.7246, 74.2723);
        addStation("Muridke", 31.8023, 74.2573);
        addStation("Kamoke", 31.9754, 74.2238);

        // ================== Central / North Punjab ==================
        addStation("Gujranwala", 32.1617, 74.1883);
        addStation("Shahinabad", 32.1708, 72.7682);
        addStation("Bhalwal", 32.2658, 72.8980);
        addStation("Pasrur", 32.2613, 74.6635);
        addStation("Chawinda", 32.3440, 74.7050);
        addStation("Sambrial", 32.4783, 74.3535);
        addStation("Sodhra", 32.4626, 74.1835);
        addStation("WazirabadJn", 32.4432, 74.1210);
        addStation("SialkotJn", 32.4945, 74.5229);
        addStation("MalakwalJn", 32.5548, 73.2138);
        addStation("MandiBahauddin", 32.5853, 73.4917);
        addStation("Gujrat", 32.5731, 74.0789);
        addStation("LalaMusaJn", 32.7048, 73.9575);
        addStation("Kharian", 32.8143, 73.8489);
        addStation("SaraiAlamgir", 32.9043, 73.7535);
        addStation("Jhelum", 32.9405, 73.7276);
        addStation("NarowalJn", 32.1018, 74.8783);
        addStation("Dina", 33.0267, 73.5932);
        addStation("GujarKhan", 33.2552, 73.3043);
        addStation("Rawalpindi", 33.6007, 73.0679);
        addStation("Margalla", 33.6897, 72.8215);
        addStation("GolraSharif", 33.6936, 72.9478);
        addStation("AttockCity", 33.7667, 72.3605);
        addStation("Chakwal", 32.9326, 72.8622);

        // ================== Hazara / KP ==================
        addStation("SargodhaJn", 32.0836, 72.6711);
        addStation("KundianJn", 32.4562, 71.4788);
        addStation("Mianwali", 32.5776, 71.5285);
        addStation("Nowshera", 34.0159, 71.9753);
        addStation("PeshawarCantt", 34.0053, 71.5590);
    }

    private void addStation(String name, double lat, double lng) {

        Map<String, Object> map = new HashMap<>();
        map.put("lat", lat);
        map.put("lng", lng);
        map.put("isActive", true);

        root().collection("Stations")
                .document(name)
                .set(map);
    }

    // ================= ROUTES =================

    private void uploadRoutes() {

        addRoute("route_awan_up", "Karachi → Peshawar", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("RahimYarKhan", 3),
                station("Bahawalpur", 4),
                station("MultanCantt", 5),
                station("Sahiwal", 6),
                station("Okara", 7),
                station("LahoreJn", 8),
                station("Gujranwala", 9),
                station("WazirabadJn", 10),
                station("LalaMusaJn", 11),
                station("Jhelum", 12),
                station("GujarKhan", 13),
                station("Rawalpindi", 14),
                station("PeshawarCantt", 15)
        ));

        addRoute("route_hazara", "Karachi → Rawalpindi", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("Bahawalpur", 3),
                station("MultanCantt", 4),
                station("SargodhaJn", 5),
                station("MalakwalJn", 6),
                station("MandiBahauddin", 7),
                station("LalaMusaJn", 8),
                station("Jhelum", 9),
                station("Rawalpindi", 10)
        ));
        addRoute("route_hazara_return", "Rawalpindi → Karachi", Arrays.asList(
                station("Rawalpindi", 0),
                station("Jhelum", 1),
                station("LalaMusaJn", 2),
                station("MandiBahauddin", 3),
                station("MalakwalJn", 4),
                station("SargodhaJn", 5),
                station("MultanCantt", 6),
                station("Bahawalpur", 7),
                station("RohriJn", 8),
                station("HyderabadJn", 9),
                station("KarachiCantt", 10)
        ));

        addRoute("route_allama_iqbal", "Sialkot → Karachi", Arrays.asList(
                station("SialkotJn", 0),
                station("Chawinda", 1),
                station("Pasrur", 2),
                station("NarowalJn", 3),
                station("LahoreJn", 4),
                station("RaiwindJn", 5),
                station("Okara", 6),
                station("Sahiwal", 7),
                station("MultanCantt", 8),
                station("Bahawalpur", 9),
                station("RahimYarKhan", 10),
                station("RohriJn", 11),
                station("HyderabadJn", 12),
                station("KarachiCantt", 13)
        ));
        addRoute("route_green_line", "Islamabad → Karachi", Arrays.asList(
                station("Margalla", 0),
                station("Rawalpindi", 1),
                station("LahoreJn", 2),
                station("KhanewalJn", 3),
                station("Bahawalpur", 4),
                station("RohriJn", 5),
                station("HyderabadJn", 6),
                station("KarachiCantt", 7)
        ));

        addRoute("route_tezzgam", "Karachi → Rawalpindi", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("RahimYarKhan", 3),
                station("Bahawalpur", 4),
                station("MultanCantt", 5),
                station("KhanewalJn", 6),
                station("LahoreJn", 7),
                station("Gujranwala", 8),
                station("WazirabadJn", 9),
                station("Jhelum", 10),
                station("Rawalpindi", 11)
        ));

        addRoute("route_khyber_mail", "Karachi → Peshawar", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("MultanCantt", 3),
                station("LahoreJn", 4),
                station("Gujranwala", 5),
                station("WazirabadJn", 6),
                station("Gujrat", 7),
                station("Jhelum", 8),
                station("Rawalpindi", 9),
                station("AttockCity", 10),
                station("Nowshera", 11),
                station("PeshawarCantt", 12)
        ));

        addRoute("route_subak_kharam", "Lahore → Rawalpindi", Arrays.asList(
                station("LahoreJn", 0),
                station("Gujranwala", 1),
                station("WazirabadJn", 2),
                station("Gujrat", 3),
                station("LalaMusaJn", 4),
                station("Jhelum", 5),
                station("Chakwal", 6),
                station("Rawalpindi", 7)
        ));

        addRoute("route_shalimar", "Lahore → Karachi", Arrays.asList(
                station("LahoreJn", 0),
                station("RaiwindJn", 1),
                station("Sahiwal", 2),
                station("KhanewalJn", 3),
                station("MultanCantt", 4),
                station("Bahawalpur", 5),
                station("RohriJn", 6),
                station("HyderabadJn", 7),
                station("KarachiCantt", 8)
        ));

        addRoute("route_jinnah_express", "Lahore → Karachi", Arrays.asList(
                station("LahoreJn", 0),
                station("Faisalabad", 1),
                station("KhanewalJn", 2),
                station("MultanCantt", 3),
                station("Bahawalpur", 4),
                station("RohriJn", 5),
                station("HyderabadJn", 6),
                station("KarachiCantt", 7)
        ));

        addRoute("route_faisalabad_express", "Multan → Lahore", Arrays.asList(
                station("MultanCantt", 0),
                station("KhanewalJn", 1),
                station("Sahiwal", 2),
                station("Okara", 3),
                station("RaiwindJn", 4),
                station("LahoreJn", 5)
        ));

        addRoute("route_awam_express", "Karachi → Peshawar", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("MultanCantt", 3),
                station("LahoreJn", 4),
                station("Rawalpindi", 5),
                station("AttockCity", 6),
                station("Nowshera", 7),
                station("PeshawarCantt", 8)
        ));
    }

    private Map<String, Object> station(String name, int index) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("index", index);
        return m;
    }

    private void addRoute(String id, String name, List<Map<String, Object>> stations) {

        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("stations", stations);
        map.put("isActive", true);

        root().collection("Routes")
                .document(id)
                .set(map);
    }

    // ================= TRAINS =================

    private void uploadTrains() {

        addTrain("train_13", "Awan Express", "13UP", "route_awan_up");
        addTrain("train_11", "Hazara Express", "11UP", "route_hazara");
        addTrain("train_12", "Hazara Express", "12DOWN", "route_hazara_return");
        addTrain("train_10N", "Allama Iqbal Express", "10N", "route_allama_iqbal");
        addTrain("train_5DN", "Green Line Express", "5DN", "route_green_line");
        addTrain("train_7UP", "Tezgam Express", "7UP", "route_tezzgam");
        addTrain("train_1UP", "Khyber Mail", "1UP", "route_khyber_mail");
        addTrain("train_101UP", "Subak Kharam", "101UP", "route_subak_kharam");
        addTrain("train_27UP", "Shalimar Express", "27UP", "route_shalimar");
        addTrain("train_31UP", "Jinnah Express", "31UP", "route_jinnah_express");
        addTrain("train_39UP", "Faisalabad Express", "39UP", "route_faisalabad_express");
        addTrain("train_13DN", "Awam Express", "13DN", "route_awam_express");
    }
    private void addTrain(String id, String name, String number, String routeId) {

        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("number", number);
        map.put("routeId", routeId);
        map.put("avgSpeed", 60);
        map.put("stopTime", 10);
        map.put("isActive", true);
        map.put("isRunning", true);

        root().collection("Trains")
                .document(id)
                .set(map);
    }
}


/*package com.example.paktrainfoodapp.ui;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;

public class FirebaseSeeder {

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ================= RUN ALL =================
    public void seedAll() {
        uploadStations();
        uploadRoutes();
        uploadTrains();
    }

    private DocumentReference root() {
        return db.collection("RailwaySystem").document("main");
    }
    private void uploadStations() {

        // ================== Karachi Division ==================

        addStation("KarachiCantt", 24.8543, 67.0099);
        addStation("DrighRoad", 24.9018, 67.1604);
        addStation("LandhiJn", 24.8427, 67.2056);
        addStation("Jungshahi", 25.4348, 67.7775);
        addStation("KotriJn", 25.3663, 68.3083);
        addStation("HyderabadJn", 25.3960, 68.3578);
        addStation("TandoAdamJn", 25.7685, 68.6612);
        addStation("Shahdadpur", 25.9252, 68.6227);
        addStation("Nawabshah", 26.2458, 68.4103);
        addStation("Bandhi", 26.5888, 68.3027);
        addStation("Mehrabpur", 27.0938, 68.4204);
        addStation("PadidanJn", 26.7743, 68.3002);
        addStation("RohriJn", 27.6837, 68.8952);

        // ================== Upper Sindh ==================

        addStation("PanoAqil", 27.8568, 69.1114);
        addStation("Ghotki", 28.0062, 69.3164);
        addStation("MirpurMathelo", 28.0219, 69.5499);
        addStation("Sadiqabad", 28.3078, 70.1268);

        // ================== South Punjab ==================

        addStation("RahimYarKhan", 28.4207, 70.2989);
        addStation("KhanpurJn", 28.6483, 70.6577);
        addStation("Bahawalpur", 29.3956, 71.6836);
        addStation("LodhranJn", 29.5404, 71.6335);
        addStation("MultanCantt", 30.1998, 71.4753);
        addStation("KhanewalJn", 30.3018, 71.9320);
        addStation("MianChannu", 30.4408, 72.3567);
        addStation("Chichawatni", 30.5337, 72.6915);
        addStation("Sahiwal", 30.6682, 73.1114);
        addStation("Harappa", 30.6298, 72.8735);
        addStation("Okara", 30.8081, 73.4458);
        addStation("RenalaKhurd", 30.8784, 73.5984);
        addStation("Pattoki", 31.0187, 73.8510);
        addStation("RaiwindJn", 31.2483, 74.2152);
        addStation("KotLakhpat", 31.4684, 74.3295);
        addStation("LahoreJn", 31.5655, 74.3414);

    }

    }

    private void addStation(String name, double lat, double lng) {

        Map<String, Object> map = new HashMap<>();
        map.put("lat", lat);
        map.put("lng", lng);
        map.put("isActive", true);

        root().collection("Stations")
                .document(name)
                .set(map);
    }

    // ================= ROUTES (INDEX BASED FIXED) =================
    private void uploadRoutes() {

        addRoute("route_awan_up", "Karachi → Peshawar", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("RahimYarKhan", 3),
                station("Bahawalpur", 4),
                station("MultanCantt", 5),
                station("Sahiwal", 6),
                station("Okara", 7),
                station("LahoreJn", 8),
                station("Gujranwala", 9),
                station("WazirabadJn", 10),
                station("LalaMusaJn", 11),
                station("Jhelum", 12),
                station("GujarKhan", 13),
                station("Rawalpindi", 14),
                station("PeshawarCantt", 15)
        ));

        addRoute("route_hazara", "Karachi → Rawalpindi", Arrays.asList(
                station("KarachiCantt", 0),
                station("HyderabadJn", 1),
                station("RohriJn", 2),
                station("Bahawalpur", 3),
                station("MultanCantt", 4),
                station("SargodhaJn", 5),
                station("MalakwalJn", 6),
                station("MandiBahauddin", 7),
                station("LalaMusaJn", 8),
                station("Jhelum", 9),
                station("Rawalpindi", 10)
        ));

        addRoute("route_allama_iqbal", "Sialkot → Karachi", Arrays.asList(
                station("SialkotJn", 0),
                station("Chawinda", 1),
                station("Pasrur", 2),
                station("NarowalJn", 3),
                station("LahoreJn", 4),
                station("RaiwindJn", 5),
                station("Okara", 6),
                station("Sahiwal", 7),
                station("MultanCantt", 8),
                station("Bahawalpur", 9),
                station("RahimYarKhan", 10),
                station("RohriJn", 11),
                station("HyderabadJn", 12),
                station("KarachiCantt", 13)
        ));
    }

    private Map<String, Object> station(String name, int index) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("index", index);
        return m;
    }

    private void addRoute(String id, String name, List<Map<String, Object>> stations) {

        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("stations", stations);
        map.put("isActive", true);

        root().collection("Routes")
                .document(id)
                .set(map);
    }

    // ================= TRAINS =================
    private void uploadTrains() {

        addTrain("train_13", "Awan Express", "13UP", "route_awan_up");
        addTrain("train_11", "Hazara Express", "11UP", "route_hazara");
        addTrain("train_10N", "Allama Iqbal Express", "10N", "route_allama_iqbal");
    }

    private void addTrain(String id, String name, String number, String routeId) {

        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("number", number);
        map.put("routeId", routeId);
        map.put("avgSpeed", 60);
        map.put("stopTime", 10);
        map.put("isActive", true);
        map.put("isRunning", true);

        root().collection("Trains")
                .document(id)
                .set(map);
    }
}*/