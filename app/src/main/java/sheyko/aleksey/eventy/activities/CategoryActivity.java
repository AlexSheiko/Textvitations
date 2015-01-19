package sheyko.aleksey.eventy.activities;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.sdk.FeatherActivity;

import sheyko.aleksey.eventy.R;
import sheyko.aleksey.eventy.adapters.CategoryListAdapter;
import sheyko.aleksey.eventy.util.IabHelper;
import sheyko.aleksey.eventy.util.IabHelper.OnIabSetupFinishedListener;
import sheyko.aleksey.eventy.util.IabResult;
import sheyko.aleksey.eventy.util.Inventory;
import sheyko.aleksey.eventy.util.Purchase;


public class CategoryActivity extends ListActivity {

    public static final String TAG = "TextvitationsTag";
    public static final String SKU_EXTRA_TOOLS = "extra_tools";

    // The helper object
    IabHelper mHelper;

    // Does the user have extra tools?
    boolean mHaveExtraTools = false;

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int AVIARY_EDITOR_REQUEST = 2;

    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    private static final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkGfnRZnnEzmGYHYnKad6i/YsKjNA8e1JFdQGm2yzbgfeC8h6Gk/4CIrRu3fWgbutuUbir00NZS7oU659AEfH7LdHVyhOjFb65/XSF0atmsxVUmtm56CDYppPrOzh8XZ6nGDTGwq+nrW47xwigJas7gXe/BVFqumJ9gIiyjy/nWHlttD55B34xs4Qp9B9g+rgRuK5Il3dlWboan/Du8FsCkP2jZHrSXatipYyVMWSwc/m0Vwyo//sbmYsB00aOhbLho7/+iX8fsIJ6o5sGRZj71YK7MVJnWSzrU3m5llRqLQy4bmjZ46z+/nktlV6wFEnAt8+OmSKJgUfMfmIBJRXpwIDAQAB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);
        sharedPref = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_APPEND);

        String[] values = new String[]{"Party", "Birthday", "Holidays", "Kids", "Wedding Anniversary", "Baby Shower"};
        setListAdapter(new CategoryListAdapter(this, values));
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        String item = (String) getListAdapter().getItem(position);

        editor = sharedPref.edit();
        editor.putString("category", item);
        editor.apply();

        startActivity(new Intent(CategoryActivity.this, TemplateActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.categories, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // Pick image from menu action
    public boolean pickImageFromGallery(MenuItem item) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
        return true;
    }

    // Pick image from button
    public void pickImageFromGallery(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                Uri mSelectedImageUri = data.getData();
                callAviaryIntent(mSelectedImageUri);

                mHelper = new IabHelper(CategoryActivity.this, base64EncodedPublicKey);

                Log.d(TAG, "Starting setup.");
                mHelper.startSetup(new OnIabSetupFinishedListener() {
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

            } else if (requestCode == AVIARY_EDITOR_REQUEST) {
                String mImagePath = data.getDataString();

                //А теперь уже и редактированное изображение ложу
                editor = sharedPref.edit();
                editor.putString("image_path", mImagePath);
                editor.apply();

                startActivity(new Intent(this,
                        InputActivity.class));
            }
        }
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
            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(SKU_EXTRA_TOOLS);
            mHaveExtraTools = (premiumPurchase != null/* && verifyDeveloperPayload(premiumPurchase)*/);
            Log.d(TAG, "User " + (mHaveExtraTools ? "HAVE extra tools" : "DOESN'T have extra tools"));
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    public void callAviaryIntent(Uri imageUri) {
        String[] tools;
        if (mHaveExtraTools) {
            tools = new String[]{"TEXT", "DRAW", "CROP", "EFFECTS", "STICKERS", "CONTRAST", "SATURATION", "BORDERS", "BLUR"};
        } else {
            tools = new String[]{"TEXT", "DRAW", "CROP"};
        }

        Intent aviaryIntent = new Intent(CategoryActivity.this, FeatherActivity.class);
        aviaryIntent.setData(imageUri);
        aviaryIntent.putExtra(Constants.EXTRA_TOOLS_LIST, tools);
        aviaryIntent.putExtra(Constants.EXTRA_HIDE_EXIT_UNSAVE_CONFIRMATION, true);
        aviaryIntent.putExtra(Constants.EXTRA_IN_API_KEY_SECRET, "85c246dd9c3b9289");
        aviaryIntent.putExtra(Constants.EXTRA_OUTPUT_QUALITY, 100);
        startActivityForResult(aviaryIntent, AVIARY_EDITOR_REQUEST);
    }
}