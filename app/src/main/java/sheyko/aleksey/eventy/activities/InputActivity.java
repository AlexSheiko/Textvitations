package sheyko.aleksey.eventy.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import sheyko.aleksey.eventy.R;
import sheyko.aleksey.eventy.util.IabHelper;
import sheyko.aleksey.eventy.util.IabResult;
import sheyko.aleksey.eventy.util.Inventory;
import sheyko.aleksey.eventy.util.Purchase;

//import com.aviary.android.feather.cds.billing.util.IabHelper;
//import com.aviary.android.feather.cds.billing.util.IabResult;
//import com.aviary.android.feather.cds.billing.util.Inventory;
//import com.aviary.android.feather.cds.billing.util.Purchase;
//import com.aviary.android.feather.library.Constants;
//import com.aviary.android.feather.sdk.FeatherActivity;


public class InputActivity extends Activity {

    public static final String TAG = "TextvitationsTag";

    public static final String SKU_EXTRA_TOOLS = "extra_tools";

    // The helper object
    IabHelper mHelper;

    // Does the user have extra tools?
    boolean mHaveExtraTools = false;

    private ImageView imagePreview;
    protected String mImagePath;

    private static final int AVIARY_EDITOR_REQUEST = 2;

    private TextView locationButton;

    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    public InputActivity() {
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input);

        final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkGfnRZnnEzmGYHYnKad6i/YsKjNA8e1JFdQGm2yzbgfeC8h6Gk/4CIrRu3fWgbutuUbir00NZS7oU659AEfH7LdHVyhOjFb65/XSF0atmsxVUmtm56CDYppPrOzh8XZ6nGDTGwq+nrW47xwigJas7gXe/BVFqumJ9gIiyjy/nWHlttD55B34xs4Qp9B9g+rgRuK5Il3dlWboan/Du8FsCkP2jZHrSXatipYyVMWSwc/m0Vwyo//sbmYsB00aOhbLho7/+iX8fsIJ6o5sGRZj71YK7MVJnWSzrU3m5llRqLQy4bmjZ46z+/nktlV6wFEnAt8+OmSKJgUfMfmIBJRXpwIDAQAB";

        sharedPref = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_APPEND);

        mImagePath = sharedPref.getString("image_path", null);


        locationButton = (TextView) findViewById(R.id.locationButton);
        imagePreview = (ImageView) findViewById(R.id.imagePreview);


        if (mImagePath != null) {
            imagePreview.setImageBitmap(BitmapFactory.decodeFile(mImagePath));
        }

        imagePreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mHelper = new IabHelper(InputActivity.this, base64EncodedPublicKey);

                Log.d(TAG, "Starting setup.");
                mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                    public void onIabSetupFinished(IabResult result) {
                        Log.d(TAG, "Setup finished.");

                        if (!result.isSuccess()) {
                            // Oh noes, there was a problem.
                            Log.d(TAG, "Problem setting up in-app billing: " + result);
                            return;
                        }

                        // Have we been disposed of in the meantime? If so, quit.
                        if (mHelper == null) return;

                        // IAB is fully set up. Now, let's get an inventory of stuff we own.
                        Log.d(TAG, "Setup successful. Querying inventory.");
                        mHelper.queryInventoryAsync(mGotInventoryListener);
                    }
                });

            }
        });

    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                Log.d(TAG, "Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(SKU_EXTRA_TOOLS);
            mHaveExtraTools = (premiumPurchase != null/* && verifyDeveloperPayload(premiumPurchase)*/);
            Log.d(TAG, "User " + (mHaveExtraTools ? "HAVE extra tools" : "DOESN'T have extra tools"));

            callAviaryIntent();
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    public void callAviaryIntent() {

        String[] tools;
        if (mHaveExtraTools) {
            tools = new String[]{"TEXT", "DRAW", "CROP", "EFFECTS", "STICKERS", "CONTRAST", "SATURATION", "BORDERS", "BLUR"};
        } else {
            tools = new String[]{"TEXT", "DRAW", "CROP"};
        }

        Intent aviaryIntent = new Intent(InputActivity.this, FeatherActivity.class);
        aviaryIntent.putExtra(Constants.EXTRA_TOOLS_LIST, tools);
        aviaryIntent.putExtra(Constants.EXTRA_HIDE_EXIT_UNSAVE_CONFIRMATION, true);
        //Здесь шаредпрефс не нужны, т.к. исходник хранится с вызова из Cat или Temp
        aviaryIntent.setData(Uri.parse(mImagePath));
        aviaryIntent.putExtra(Constants.EXTRA_IN_API_KEY_SECRET, "85c246dd9c3b9289");
        aviaryIntent.putExtra(Constants.EXTRA_OUTPUT_QUALITY, 100);
        startActivityForResult(aviaryIntent, AVIARY_EDITOR_REQUEST);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mImagePath = sharedPref.getString("image_path", null);
        imagePreview.setImageBitmap(BitmapFactory.decodeFile(mImagePath));

        String mStreet = sharedPref.getString("street", null);
        String mCity = sharedPref.getString("city", null);

        if (mStreet != null && mCity != null)
            locationButton.setText(mStreet + ", " + mCity);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            mImagePath = data.getDataString();

            //Ложу редактированное изображение
            editor = sharedPref.edit();
            editor.putString("image_path", mImagePath);
            editor.apply();
        }
    }

    public void pickLocation(View view) {

        boolean network_enabled = false;

        try {
            ConnectivityManager connectivityManager
                    = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                network_enabled = true;
            }

        } catch (Exception ignored) {
        }

        if (!network_enabled) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("Wi-fi needed");
            dialog.setMessage("You need a network connection to load maps. Please turn it on in Settings");
            dialog.setIcon(R.drawable.ic_dialog_network);
            dialog.setPositiveButton("Open settings", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    // Open GPS settings
                    Intent myIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                    startActivity(myIntent);
                }
            });

            dialog.show();

        } else {

            int isAvailable = GooglePlayServicesUtil
                    .isGooglePlayServicesAvailable(this);


            if (isAvailable == ConnectionResult.SUCCESS) {

                Intent intent = new Intent(InputActivity.this, MapActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please install the latest Google Play Services.", Toast.LENGTH_SHORT).show();
            }


        }
    }

    public void composeInvitation(View view) {
        startActivity(new Intent(this,
                PreviewActivity.class));
    }
}
