package hk.hkucs.ParkingProximiter;

import static com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    TextView textView, textViewL2, textViewSub, infoTextView;
    private hk.hkucs.ParkingProximiter.GpsTracker GpsTracker;
    Double gps_lat, gps_long, ext_lat, ext_long;
    private static final String TAG = "MainActivity";
    ArrayList<String>[] cp = new ArrayList[50];
    ArrayList<String>[] vcp = new ArrayList[50];
    PyObject csvData;
    //OSM
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView map = null;

    String lastUpdate;
    int count = 0;
    boolean runValid = true;
    //FusedLocationProviderClient fusedLocationClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkPermission();

        //GpsEventListener
        //fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        //OSM Content
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);
        map = (MapView) findViewById(R.id.map);
        runValid = true;
        map.setTileSource(TileSourceFactory.MAPNIK);
        requestPermissionsIfNecessary(new String[]{
                // if you need to show the current location, uncomment the line below
                // Manifest.permission.ACCESS_FINE_LOCATION,
                // WRITE_EXTERNAL_STORAGE is required in order to show the map
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        });
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);
        IMapController mapController = map.getController();


        textView = (TextView) findViewById(R.id.textview);
        infoTextView = (TextView) findViewById(R.id.infoview);

        //Obtain GPS location
        System.out.println("Obtaining GPS Location:...");
        textViewL2 = findViewById(R.id.textViewL2);
        textViewSub = findViewById(R.id.sub);

        try {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        getLocation();
        ext_lat=gps_lat;
        ext_long=gps_long;
        GeoPoint currentPosition = new GeoPoint(gps_lat, gps_long);
        mapController.setCenter(currentPosition);
        System.out.println("GPS Obtained:" + gps_lat + ' ' + gps_long);
        startPython();
        getVacancyCSV();
        obtainNearbyCP();
        updateMarker(currentPosition);


        getListView(mapController);
        setButton(currentPosition, mapController, infoTextView);
        //startMap();
        int d = Integer.parseInt(cp[40].get(1));
        double zoomRange = 19 - Math.log10(d / 6);
        mapController.setZoom(zoomRange);
        //System.out.println("zoom range: "+ String.valueOf(d) + ' ' + String.valueOf(zoomRange));

        final Handler handler = new Handler();
        final int delay2 = 5000; //milliseconds
        handler.postDelayed(new Runnable() {
            public void run() {
                if (runValid) {
                    System.out.println("count="+String.valueOf(count)+" map is null: "+String.valueOf(map==null));
                    //System.out.println("map" + String.valueOf(map));
                    if (count % 12!=0 && map != null) {
                        System.out.println("~recalculate...");
                        reCalculate(currentPosition, mapController);
                    } else {
                        if (map != null) {
                            System.out.println("~updateAll...");
                            updateAll(currentPosition, mapController);
                        }
                    }
                    count+=1;
                    handler.postDelayed(this, delay2);
                }
            }
        }, delay2);
    }


    // Main Functions
    public void getLocation() {
        System.out.println("getLocation...");

        gps_lat=0.0;
        int n = 0;
        while (gps_lat.equals(0.0) && n<10) {

            GpsTracker = new GpsTracker(MainActivity.this);
            if (GpsTracker.canGetLocation()) {
                gps_lat = GpsTracker.getLatitude();
                gps_long = GpsTracker.getLongitude();
            } else {
                GpsTracker.showSettingsAlert();
            }
            n+=1;
        }


        /*
        //gps_lat = 22.283541;
        //gps_long = 114.135374;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>(){
            @Override
            public void onSuccess(Location location){
                if (location != null) {
                    gps_lat = location.getLatitude();
                    gps_long = location.getLongitude();
                }
            }
        });
        System.out.println("Location1: "+ String.valueOf(gps_lat)+','+String.valueOf(gps_long));
        fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // Got last known location. In some rare situations this can be null.
                System.out.println("Location2: "+ String.valueOf(location));
                if (location != null) {
                    gps_lat = location.getLatitude();
                    gps_long = location.getLongitude();
                }
            }

        });

        //System.out.println("aaaaaLocation: "+  String.valueOf(fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, null)));

         */



        System.out.println("Location3: "+ String.valueOf(gps_lat)+','+String.valueOf(gps_long));
        textViewSub.setText("sys:"+gps_lat+", "+gps_long);

        if (Math.abs(23-gps_lat)>1 || Math.abs(114-gps_long)>1) {
            System.out.println("Outside Hong Kong. Use default location "+String.valueOf(Math.abs(23-gps_lat)));
            gps_lat = 22.283541;
            gps_long = 114.135374;
        }
    }

    public void startPython() {
        //start python
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
    }
    public void getVacancyCSV(){
        Python py = Python.getInstance();
        PyObject pyobj = py.getModule("pyscript");
        csvData = pyobj.callAttr("getVacancyCSV");
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        lastUpdate = "last update at "+ String.valueOf(sdf.format(timestamp));
        textViewL2.setText(lastUpdate);
    }
    public void obtainNearbyCP(){
        //now create python instance
        Python py = Python.getInstance();
        //System.out.println("GPS data present:"+gps_lat+' '+gps_long);
        PyObject pyobj = py.getModule("pyscript");
        List<PyObject> obj = pyobj.callAttr("main",csvData, gps_lat,gps_long).asList();

        int n = 50;
        int k=0;

        for (int i = 0; i<n; i++){
            cp[i] = new ArrayList<String>();
            for (int j = 0; j<8;j++){
                cp[i].add(String.valueOf(obj.get(i*8+j)));
            }
            if (cp[i].get(6).equals("V")){
                vcp[k] = new ArrayList<String>();
                for (int j = 0; j<8;j++){
                    vcp[k].add(String.valueOf(obj.get(i*8+j)));
                }
                //System.out.println(vcp[k]);
                k+=1;
            }

        }
        //now set returned text to textview
    }
    public void getListView(IMapController mapController){
        ArrayList<String> listItem = new ArrayList<String>();
        ListView listView;
        int n = 1;
        for (int i=0;i<50;i++){
            if (cp[i].get(6).equals("V")) {
                listItem.add('(' + String.valueOf(n) + ") " + cp[i].get(4) + ' ' + cp[i].get(5) + "   [" + cp[i].get(1) + "m]");
                n+=1;
            }
        }
        listView=(ListView)findViewById(R.id.listView);
        textView=(TextView)findViewById(R.id.textView);
        listView.setClickable(true);
        //listItem = getResources().getStringArray(R.array.array_technology);
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, listItem){
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view =super.getView(position, convertView, parent);

                TextView textView=(TextView) view.findViewById(android.R.id.text1);
                textView.setTextColor(Color.rgb(70,70,70));

                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                int i = position;
                GeoPoint pos = new GeoPoint(Double.parseDouble(vcp[i].get(2)), Double.parseDouble(vcp[i].get(3)));
                mapController.animateTo(pos);
                String text = '('+String.valueOf(i+1)+") ParkingSpaceID:"+vcp[i].get(0)+"  LastStatusChange:"+vcp[i].get(7);
                infoTextView.setText(text);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int position, long id) {
                int i = position;
                String callParameter = "google.navigation:q="+vcp[i].get(2)+ ','+vcp[i].get(3);
                Uri gmmIntentUri = Uri.parse(callParameter);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                //if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
                //}

                return true;
            }
        });


    }
    public void setButton(GeoPoint currentPosition,IMapController mapController,TextView infoTextView){
        Button button = (Button) findViewById(R.id.refreshBuutton);
        button.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                mapController.animateTo(currentPosition);
                updateAll(currentPosition,mapController);
                infoTextView.setText("");
            }
        });
    }
    public void updateMarker(GeoPoint currentPosition){
        map.getOverlays().clear();

        ArrayList<Marker> mMarker = new ArrayList<Marker>();
        for (int i = 0; i < 50; i++) {
            GeoPoint pos = new GeoPoint(Double.parseDouble(cp[i].get(2)), Double.parseDouble(cp[i].get(3)));
            //System.out.println(cp[i].get(6));
            mMarker.add(new Marker(map));
            if (cp[i].get(6).equals("V")) {
            } else {
                mMarker.get(i).setIcon(getResources().getDrawable(R.drawable.marker3));
                //System.out.println(String.valueOf(i)+' '+String.valueOf(cp[i]));
                mMarker.get(i).setPosition(pos);
                mMarker.get(i).setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                mMarker.get(i).setInfoWindow(null);
                map.getOverlays().add(mMarker.get(i));
            }

        }
        for (int i = 0; i < 50; i++) {
            GeoPoint Pos = new GeoPoint(Double.parseDouble(cp[i].get(2)), Double.parseDouble(cp[i].get(3)));
            //System.out.println(cp[i].get(6));
            mMarker.add(new Marker(map));
            if (cp[i].get(6).equals("V")) {
                mMarker.get(i).setIcon(getResources().getDrawable(R.drawable.marker2));
                mMarker.get(i).setPosition(Pos);
                mMarker.get(i).setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                mMarker.get(i).setInfoWindow(null);
                map.getOverlays().add(mMarker.get(i));
            }
        }

        Marker startMarker = new Marker(map);
        startMarker.setIcon(getResources().getDrawable(R.drawable.marker1));
        GeoPoint pos = new GeoPoint(gps_lat, gps_long);
        startMarker.setPosition(pos);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(startMarker);
    }
    public void updateAll(GeoPoint currentPosition,IMapController mapController){
        getLocation();
        getVacancyCSV();
        obtainNearbyCP();
        getListView(mapController);
        updateMarker(currentPosition);
    }
    public void reCalculate(GeoPoint currentPosition,IMapController mapController){
        getLocation();
        System.out.println(String.valueOf(ext_lat)+' '+String.valueOf(ext_long)+' '+String.valueOf(gps_lat)+' '+String.valueOf(gps_long));
        if (!gps_lat.equals(ext_lat) || !gps_long.equals(ext_long)){
            obtainNearbyCP();
            getListView(mapController);
            updateMarker(currentPosition);
            ext_lat=gps_lat;
            ext_long=gps_long;
        }

    }

    //
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.info:
                runValid = false;
                sendMessage();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void sendMessage() {
        Intent intent = new Intent(this, InfoActivity.class);
        startActivity(intent);
    }

    public void checkPermission(){
        Context mContext;
        mContext = MainActivity.this;
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 101);
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{android.Manifest.permission.INTERNET}, 101);
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{android.Manifest.permission.ACCESS_WIFI_STATE}, 101);
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{android.Manifest.permission.ACCESS_NETWORK_STATE}, 101);
        }
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }


    }



    //OSM Functions
    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }
    @Override
    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            permissionsToRequest.add(permissions[i]);
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }
    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }
    //@Override
    //public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    //    MapView mMapView = new MapView(inflater.getContext(), 256, getContext());
    //    return mMapView;
    //}
    //

    public void onProviderEnabled(@NonNull String provider) {
    }
    public void onProviderDisabled(@NonNull String provider) {
    }
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }
};