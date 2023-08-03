package com.example.myapplication;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import java.lang.ref.WeakReference;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements
        FragmentOnAttachListener,
        BaseArFragment.OnSessionConfigurationListener,
        ArFragment.OnViewCreatedListener{
    private ArFragment arFragment;
    private Renderable model;
    private ViewRenderable viewRenderable;
    private Node modelNode;
    private Renderable modelLeft, modelRight;
    public int direct = 0;
    private LocationManager locationManager;
    private static final int LOCATION_PERMISSION_CODE = 2;
    private static boolean TFT=false;
    private GoogleMap mMap;
    private Marker systemMarker;
    private EditText destination,road;
    private static LatLng latLng = new LatLng(23.69618979467513, 120.53405406804573);
    private static ArrayList<ArrayList<String>> turn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setTitle("GPSPro");
        getLocation();
        setContentView(R.layout.activity_maps);
        getSupportFragmentManager().addFragmentOnAttachListener(this);


        Button button = findViewById(R.id.navigation);
        destination = findViewById(R.id.destination);
        road = findViewById(R.id.road);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                positionModelInTheRight(modelRight);
                //positionModelInTheMiddle(modelLeft);
                getDirectionsAsync(latLngToString(latLng), destination.getText().toString(), new DirectionsCallback() {
                    @Override
                    public void onResult(String result) {
                        try {
                            turn = parseDirections(result);
                            TFT=true;
                            StringBuilder instructions = new StringBuilder();
                            for (ArrayList<String> step : turn) {
                                String lat = step.get(0);
                                String lng = step.get(1);
                                String instruction = step.get(2);
                                instructions.append(", Instruction: ").append(instruction);
                                if (step.size() > 5) {
                                    String maneuver = step.get(5);
                                    instructions.append(", Maneuver: ").append(maneuver);
                                }
                                instructions.append("\n");
                            }

                            road.setText(instructions.toString());

                        } catch (JSONException e) {
                            Log.d("TAG","JSONException");
                            throw new RuntimeException(e);
                        }

                    }
                });
            }
        });


        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }

        loadModels();

    }

    private void getLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.5f, locationListener);
        Location x = locationManager.getLastKnownLocation("gps");
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            latLng = new LatLng(location.getLatitude(), location.getLongitude());
            Log.d("TAG","找到新位置");
            road.setText(String.valueOf(TFT));
            if (TFT) {

                //    road.setText(String.valueOf(latLng.latitude)+String.valueOf(latLng.longitude));
                String maneuver = getTurnDirection(turn, latLng.latitude, latLng.longitude);
                destination.setText(maneuver);
                if (maneuver != null ) {
                    if (maneuver.contains("turn-left")) {
                        destination.setText("左轉");
                        positionModelInTheRight(modelRight);
                    } else if (maneuver.contains("turn-right")) {
                        destination.setText("右轉");
                        positionModelInTheMiddle(modelLeft);
                    }
                }else {
                    removeAllModels();  // 無轉向
                }
                String  route = getInstructions(turn, latLng.latitude, latLng.longitude);
                if (route !=null){
                    road.setText(route);
                }
            }
        }
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        public void onProviderEnabled(String provider) {}
        public void onProviderDisabled(String provider) {}
    };
    public void removeAllModels() {
        if (arFragment != null && arFragment.getArSceneView() != null && arFragment.getArSceneView().getScene() != null) {
            for (Node node : new ArrayList<>(arFragment.getArSceneView().getScene().getChildren())) {
                if (node instanceof AnchorNode) {
                    if (((AnchorNode) node).getAnchor() != null) {
                        ((AnchorNode) node).getAnchor().detach();
                    }
                }
                if (!(node instanceof Camera) ) {
                    node.setParent(null);
                }
            }
        }
    }
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnSessionConfigurationListener(this);
            arFragment.setOnViewCreatedListener(this);
        }
    }
    private Config createARSessionWithoutPlaneDetection() {
        Config config = new Config(arFragment.getArSceneView().getSession());
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
        return config;
    }
    @Override
    public void onSessionConfiguration(Session session, Config config) {
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        }
        config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
    }

    private float modelDistanceFromCamera = 1.0f; // The distance we want the model to be from the camera

    public void positionModelInTheMiddle(Renderable model) {
        if (arFragment != null && arFragment.getArSceneView() != null && arFragment.getArSceneView().getScene() != null && arFragment.getArSceneView().getArFrame() != null) {
            // Create the Anchor at a position just in front of the camera
            Pose pose = Pose.makeTranslation(0.0f, 0.0f, -modelDistanceFromCamera); // This will be 1 meter in front of the camera
            Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            // Create the transformable model and add it to the anchor.
            modelNode = new TransformableNode(arFragment.getTransformationSystem());
            modelNode.setParent(anchorNode);
            modelNode.setRenderable(model);
        }
    }
    public void positionModelInTheRight(Renderable model) {
        if (arFragment != null && arFragment.getArSceneView() != null && arFragment.getArSceneView().getScene() != null && arFragment.getArSceneView().getArFrame() != null) {
            // Create the Anchor at a position just in front of the camera
            Pose pose = Pose.makeTranslation(0.0f, 0.0f, -modelDistanceFromCamera); // This will be 1 meter in front of the camera
            Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            // Create the transformable model and add it to the anchor.
            modelNode = new TransformableNode(arFragment.getTransformationSystem());
            modelNode.setParent(anchorNode);
            modelNode.setRenderable(model);

            // Create a quaternion for a rotation of 180 degrees around the Y axis
            Quaternion rotation = Quaternion.axisAngle(new Vector3(0.0f, 1.0f, 0.0f), 180.0f);

            // Set the rotation of the model
            modelNode.setLocalRotation(rotation);
        }
    }
    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        arFragment.setOnViewCreatedListener(null);

        // Fine adjust the maximum frame rate
        arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL);

        // Load the model
        // Here you should load your two models separately
        ModelRenderable.builder()
                .setSource(this, Uri.parse("Arrow.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(model -> modelLeft = model)
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load left model", Toast.LENGTH_LONG).show();
                    return null;
                });

        ModelRenderable.builder()
                .setSource(this, Uri.parse("Arrow.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(model -> modelRight = model)
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load right model", Toast.LENGTH_LONG).show();
                    return null;
                });

        // Set an update listener on the Scene that will update the model's position each frame.
        arSceneView.getScene().addOnUpdateListener(frameTime -> {

            if (modelNode != null && arFragment.getArSceneView().getArFrame() != null) {
                // Create a position just in front of the camera
                Pose pose = arFragment.getArSceneView().getArFrame().getCamera().getPose()
                        .compose(Pose.makeTranslation(0.0f, 0.0f, -1.0f)); // 1 meter in front of the camera
                Vector3 position = new Vector3(pose.tx(), pose.ty(), pose.tz());

                // Set the world position of the Node to this position
                modelNode.setWorldPosition(position);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (arFragment.getArSceneView().getSession() != null) {
            arFragment.getArSceneView().getSession().configure(createARSessionWithoutPlaneDetection());
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private String latLngToString(LatLng latLng) {
        return latLng.latitude + "," + latLng.longitude;
    }
    interface DirectionsCallback {
        void onResult(String result);
    }

    public void getDirectionsAsync(final String origin, final String destination, final DirectionsCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String DIRECTIONS_API_BASE = "https://maps.googleapis.com/maps/api/directions/json";
                String API_KEY = "AIzaSyBfPNH8YjzDejDLGjpe1L_DOSb1iJX1y-s";
                String urlString = DIRECTIONS_API_BASE + "?origin=" + origin + "&destination=" + destination + "&key=" + API_KEY;
                String responseString = "";
                Log.d("TAG","執行getDirectionsAsync");
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    int responseCode = conn.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();
                        responseString = response.toString();
                        responseString = responseString.replaceAll("<.*?>", "");

                        // Call the callback with the result
                        callback.onResult(responseString);
                    } else {
                        System.out.println("GET request not worked");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static ArrayList<ArrayList<String>> parseDirections(String jsonData) throws JSONException {
        ArrayList<ArrayList<String>> result = new ArrayList<>();
        Log.d("TAG","開始執行parseDirections");
        Log.d("TAG",jsonData);
        try {


            JSONObject jsoObject = new JSONObject(jsonData);
            JSONArray routes = jsoObject.getJSONArray("routes");

            for (int i = 0; i < routes.length(); i++) {
                JSONObject route = routes.getJSONObject(i);
                JSONArray legs = route.getJSONArray("legs");

                for (int j = 0; j < legs.length(); j++) {
                    JSONObject leg = legs.getJSONObject(j);
                    JSONArray steps = leg.getJSONArray("steps");

                    for (int k = 0; k < steps.length(); k++) {
                        JSONObject step = steps.getJSONObject(k);

                        String lat = String.valueOf(step.getJSONObject("end_location").getDouble("lat"));
                        String lng = String.valueOf(step.getJSONObject("end_location").getDouble("lng"));
                        String instruction = step.getString("html_instructions").replaceAll("\\<.*?\\>", "");

                        JSONObject startLocation = step.getJSONObject("start_location");
                        String startLat = String.valueOf(startLocation.getDouble("lat"));
                        String startLng = String.valueOf(startLocation.getDouble("lng"));

                        ArrayList<String> stepInfo = new ArrayList<>();
                        stepInfo.add(lat);
                        stepInfo.add(lng);
                        stepInfo.add(instruction);
                        stepInfo.add(startLat);
                        stepInfo.add(startLng);

                        if (step.has("maneuver")) {
                            String maneuver = step.getString("maneuver");
                            stepInfo.add(maneuver);
                        }

                        result.add(stepInfo);
                    }
                }
            }

            return result;
        } catch (JSONException e) {
            Log.e("TAG", "JSONException occurred", e);
        }
        return result;
    }

    public static final double EARTH_RADIUS = 6371000; // 地球半徑，單位：米

    public static String getTurnDirection(ArrayList<ArrayList<String>> steps, double userLat, double userLng) {
        Log.d("TAG","判斷轉彎方向");
        for (ArrayList<String> step : steps) {
            double stepLat = Double.parseDouble(step.get(3));
            double stepLng = Double.parseDouble(step.get(4));
            String maneuver = step.size() > 5 ? step.get(5) : null;

            double distance = calculateDistance(userLat, userLng, stepLat, stepLng);

            if (distance <= 30) {
                if (maneuver != null) {
                    return maneuver;
                }
                return null;
            }
        }

        return null;
    }
    public static String getInstructions(ArrayList<ArrayList<String>> steps, double userLat, double userLng) {
        Log.d("TAG", "前往的路段");
        for (ArrayList<String> step : steps) {
            double stepLat = Double.parseDouble(step.get(3));
            double stepLng = Double.parseDouble(step.get(4));
            String instruction = step.get(2);
            double distance = calculateDistance(userLat, userLng, stepLat, stepLng);

            if (distance <= 30) {
                return instruction;
            }
        }

        return null;
    }



    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
    public void loadModels() {
        WeakReference<MapsActivity> weakActivity = new WeakReference<>(this);
        ModelRenderable.builder()
                .setSource(this, Uri.parse("Arrow.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(model -> {
                    MapsActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.model = model;
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
        ViewRenderable.builder()
                .setView(this, R.layout.view_model_title)
                .build()
                .thenAccept(viewRenderable -> {
                    MapsActivity activity = weakActivity.get();
                    if (activity != null) {
                        activity.viewRenderable = viewRenderable;
                    }
                })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show();
                    return null;
                });
    }

}
