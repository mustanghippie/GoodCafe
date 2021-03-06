package drgn.cafemap.Controller.Activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import drgn.cafemap.Model.CafeModel;
import drgn.cafemap.Model.FirebaseStorageConnection;
import drgn.cafemap.R;
import drgn.cafemap.Model.UserCafeMapModel;
import drgn.cafemap.Util.AnimationUtil;
import drgn.cafemap.Util.DBHelper;

import static com.google.android.gms.location.LocationServices.FusedLocationApi;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener, GoogleMap.OnMyLocationButtonClickListener, LocationSource,
        GoogleMap.OnMarkerDragListener, GoogleMap.OnMapClickListener, GoogleMap.InfoWindowAdapter,
        GoogleMap.OnInfoWindowClickListener, FirebaseStorageConnection.FirebaseStorageListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest locationRequest;
    private UiSettings mUiSettings;
    private Marker currentMarker = null;
    private UserCafeMapModel userCafeMapModel;
    private CafeModel cafeModel;

    private OnLocationChangedListener onLocationChangedListener = null;

    private int priority[] = {LocationRequest.PRIORITY_HIGH_ACCURACY, LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
            LocationRequest.PRIORITY_LOW_POWER, LocationRequest.PRIORITY_NO_POWER};
    private int locationPriority;
    private double defaultPosLat;
    private double defaultPosLon;
    // don't set a marker during opening cafe info
    private boolean disableClickEvent = false;
    private int displayBookmark = 1;
    private InputMethodManager inputMethodManager;
    private RelativeLayout mainLayout;

    // thumbnail
    private ImageView cafeThumbnail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // get default location after user cafe info set
        this.defaultPosLat = getIntent().getDoubleExtra("defaultPosLat", 0.0);
        this.defaultPosLon = getIntent().getDoubleExtra("defaultPosLon", 0.0);
        // Create LocationRequest and set interval
        this.locationRequest = LocationRequest.create();
        // setting positioning precision and battery priority
        this.setLocationPriority();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        // Initialize
        this.userCafeMapModel = new UserCafeMapModel(getApplicationContext());
        this.cafeModel = new CafeModel(getApplicationContext(), getResources());
        // Create database
        DBHelper dbHelper = new DBHelper(getApplicationContext());

        // setting email function
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // main layout
        this.mainLayout = (RelativeLayout) findViewById(R.id.mapsLayout);
        // Initialize ads
        MobileAds.initialize(getApplicationContext(), "ca-app-pub-3940256099942544~3347511713");
        AdView mAdView = (AdView) findViewById(R.id.ad_view);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .build();
        mAdView.loadAd(adRequest);

        // adjust position of the google maps toolbar
        View toolbar = ((View) mapFragment.getView().findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("4"));
        RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) toolbar.getLayoutParams();
        // position on right bottom
        rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        rlp.setMargins(0, 0, 0, 150);

    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // check permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Initialize
            this.mMap = googleMap;
            this.mUiSettings = mMap.getUiSettings();
            mMap.setLocationSource(this);
            // get my location button
            mMap.setMyLocationEnabled(true);
            mMap.setOnMyLocationButtonClickListener(this);
            // zoom up and zoom down buttons
            this.mUiSettings.setZoomControlsEnabled(false);
            // Sets markers and reads data from cafe_user_tbl and cafe_master_tbl
            this.userCafeMapModel.setCafeMapMarkers(mMap);
            // control keyboard object
            this.inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            // Add a marker event
            mMap.setOnMapClickListener(this);
            // drag a marker event
            mMap.setOnMarkerDragListener(this);
            // open cafe window event
            mMap.setInfoWindowAdapter(this);
            // click information window event
            mMap.setOnInfoWindowClickListener(this);

            // setting location
            if (defaultPosLat != 0.0 && defaultPosLon != 0.0) { // From detail page after set user map cafe
                LatLng position = new LatLng(defaultPosLat, defaultPosLon);

                // move camera
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 17));
            } else {
                // set first location
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                List<String> providers = locationManager.getProviders(true);
                Location bestLocation = null;
                // looking for best location provider
                for (String provider : providers) {
                    Location l = locationManager.getLastKnownLocation(provider);
                    if (l == null) {
                        continue;
                    }
                    if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                        bestLocation = l;
                    }
                }

                Location myLocate = bestLocation;

                if (myLocate != null) {
                    LatLng currentLocation = new LatLng(myLocate.getLatitude(), myLocate.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));

                } else {
                    Toast.makeText(getApplicationContext(), "Failed to connect gps", Toast.LENGTH_LONG).show();

                    // default position
                    LatLng position = new LatLng(49.285131, -123.112998);

                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
                }
            }

            // search cafe event
            final EditText searchText = (EditText) findViewById(R.id.search_text);
            searchText.setOnKeyListener(new View.OnKeyListener() {
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    // get an event when pressed button that is the enter key.
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        // close android keyboard
                        inputMethodManager.hideSoftInputFromWindow(searchText.getWindowToken(), InputMethodManager.RESULT_UNCHANGED_SHOWN);
                        // searching cafe
                        userCafeMapModel.searchCafe(mMap, searchText.getText().toString());
                        return true;
                    }

                    return false;
                }
            });

            // bookmark button event
            final ImageButton bookmarkButton = (ImageButton) findViewById(R.id.bookmark_button);
            bookmarkButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // click animation
                    new AnimationUtil().clickFadeInFadeOutAnimation(bookmarkButton);

                    // display bookmarked cafe
                    if (displayBookmark % 2 == 1) {
                        userCafeMapModel.displayBookmarkedCafe(mMap);
                    } else {
                        userCafeMapModel.setCafeMapMarkers(mMap);
                    }
                    displayBookmark++;
                }
            });

            // disable click event of adding a marker behind top icons
            ImageButton disableClickImage = (ImageButton) findViewById(R.id.disable_click_background);
            disableClickImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // do nothing
                }
            });

        } else {
            Log.d("debug", "permission error");
            Toast.makeText(getApplicationContext(), "Please enable permission to use GPS.", Toast.LENGTH_LONG).show();
            return;
        }
    }

    /**
     * Sets positioning precision and battery priority
     */
    private void setLocationPriority() {
        // setting positioning precision and battery priority
        this.locationPriority = priority[1];

        if (locationPriority == priority[0]) {
            // give priority to positioning precision
            locationRequest.setPriority(locationPriority);
            locationRequest.setInterval(5000);
            locationRequest.setFastestInterval(16);
        } else if (locationPriority == priority[1]) {
            // give priority to a battery
            locationRequest.setPriority(locationPriority);
            locationRequest.setInterval(60000);
            locationRequest.setFastestInterval(16);
        } else if (locationPriority == priority[2]) {
            // "city" level accuracy
            locationRequest.setPriority(locationPriority);
        } else {
            // 外部からのトリガーでの測位のみ
            locationRequest.setPriority(locationPriority);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // go to you're here
        Log.d("debug", "onLocationChanged");
        if (onLocationChangedListener != null) {
            // display GPS mark
            onLocationChangedListener.onLocationChanged(location);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d("Debug", "onConnected");
        // check permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            //FusedLocationApi
            FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, createLocationRequest(), this);
        } else {
            Log.d("debug", "permission error");
            return;
        }
    }

    private LocationRequest createLocationRequest() {
//        Log.d("Debug", "createLocationRequest");
        FusedLocationProviderApi fusedLocationProviderApi = LocationServices.FusedLocationApi;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Location currentLocation = fusedLocationProviderApi.getLastLocation(mGoogleApiClient);

        return new LocationRequest()
                .setInterval(1000)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d("debug", "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d("debug", "onConnectionFailed");
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Log.d("debug", "onMyLocationButtonClick");
        return false;
    }

    // OnLocationChangedListener calls activate() method
    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        this.onLocationChangedListener = onLocationChangedListener;
    }

    @Override
    public void deactivate() {
        this.onLocationChangedListener = null;
    }

    @Override
    public void onBackPressed() {
        // Don't allow back to MainActivity
    }

    @Override
    public View getInfoWindow(Marker marker) {
        disableClickEvent = true;

        // Making a new cafe data
        if (marker.getTitle().equals("Add your cafe")) {
            View view = getLayoutInflater().inflate(R.layout.info_window_new, null);
            String title = marker.getTitle();

            TextView titleUi = (TextView) view.findViewById(R.id.title);
            titleUi.setText(title);
            return view;
        }

        // Set view
        View view = getLayoutInflater().inflate(R.layout.info_window, null);
        // set up image
        cafeThumbnail = (ImageView) view.findViewById(R.id.badge);
        // get cafe image
        Bitmap image = null;
        if (marker.getTag().toString().equals("user")) {
            image = userCafeMapModel.getCafeImage(marker.getPosition().latitude, marker.getPosition().longitude);
            cafeThumbnail.setImageBitmap(image);
        } else { // in owner case
            String imageName = cafeModel.makeImageName(marker.getPosition().latitude, marker.getPosition().longitude);
            // get image cache from an android's local
            image = cafeModel.getCafeImageCache(imageName);
            if (image == null) {
                // set loading image
                try {
                    InputStream inputStream = getAssets().open("noImage.png");
                    image = BitmapFactory.decodeStream(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cafeThumbnail.setImageBitmap(image);

                // read image from firebase storage and save in a local
                // InfoWindow's gonna be reloaded after a cafe image loaded in FirebaseStorageConnection
                FirebaseStorageConnection firebaseStorageConnection = new FirebaseStorageConnection(this, getResources());
                firebaseStorageConnection.getImageFromFirebaseStorage(imageName, marker);
            } else {
                cafeThumbnail.setImageBitmap(image);
            }
        }

        // set title
        String title = marker.getTitle();
        TextView titleUi = (TextView) view.findViewById(R.id.title);
        // Spannable string allows us to edit the formatting of the text.
        SpannableString titleText = new SpannableString(title);
        titleText.setSpan(new ForegroundColorSpan(Color.BLACK), 0, titleText.length(), 0);
        titleUi.setText(titleText);

        String snippet = marker.getSnippet();
        TextView snippetUi = ((TextView) view.findViewById(R.id.snippet));
        if (snippet != null) {
            String[] snippetArray = snippet.split("\n");

            SpannableString time = new SpannableString(snippetArray[0]);
            SpannableString wifi = new SpannableString(" Wi-fi ");
            SpannableString socket = new SpannableString(" Socket ");
            SpannableStringBuilder snippetSpannable = new SpannableStringBuilder();

            // Text color
            wifi.setSpan(new ForegroundColorSpan(Color.WHITE), 0, wifi.length(), 0);
            socket.setSpan(new ForegroundColorSpan(Color.WHITE), 0, socket.length(), 0);

            if (snippetArray[1].equals("Wi-fi: Available")) {
                wifi.setSpan(new BackgroundColorSpan(0xFFF5CC5B), 0, wifi.length(), 0);
            } else {
                wifi.setSpan(new BackgroundColorSpan(0xFFD3D3D3), 0, wifi.length(), 0);
            }

            if (snippetArray[2].equals("Socket: Available ")) {
                socket.setSpan(new BackgroundColorSpan(0xFFF5CC5B), 0, socket.length(), 0);
            } else {
                socket.setSpan(new BackgroundColorSpan(0xFFD3D3D3), 0, socket.length(), 0);
            }

            snippetSpannable.append(time + "\n");
            snippetSpannable.append(wifi);
            snippetSpannable.append(" ");
            snippetSpannable.append(socket);

            snippetUi.setText(snippetSpannable);

        }

        return view;
    }

    @Override
    public View getInfoContents(Marker marker) {
        return null;
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Intent intent = new Intent(getApplication(), CafeActivity.class);
        LatLng latlng = marker.getPosition();

        // set flag that is a new location or not
        boolean existingDataFlag = true;
        if (marker.getTitle().equals("Add your cafe")) existingDataFlag = false;

        intent.putExtra("lat", latlng.latitude);
        intent.putExtra("lon", latlng.longitude);
        intent.putExtra("existingDataFlag", existingDataFlag);
        if (marker.getTag().toString().equals("owner")) {
            intent.putExtra("ownerFlag", true);
        } else {
            intent.putExtra("ownerFlag", false);
        }

        startActivity(intent);
    }

    @Override
    public void onMapClick(LatLng point) {
        // hide the keyboard
        inputMethodManager.hideSoftInputFromWindow(mainLayout.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        if (!disableClickEvent) {
            // A marker can exist only one
            if (currentMarker != null) currentMarker.remove();
            // Location
            LatLng latLng = new LatLng(point.latitude, point.longitude);
            currentMarker = mMap.addMarker(new MarkerOptions().position(latLng).title("Add your cafe").draggable(true)); // add the marker
            currentMarker.setTag("user");
            //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
            CameraPosition cameraPosition = new CameraPosition.Builder().target(latLng).zoom(17).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        } else {
            disableClickEvent = false;
        }
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
    }

    @Override
    public void onMarkerDrag(Marker marker) {
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        // update marker's LatLng
        marker.setPosition(marker.getPosition());
    }

    @Override
    public void updateInfoWindowView(Marker marker) {
        marker.showInfoWindow();
    }

}