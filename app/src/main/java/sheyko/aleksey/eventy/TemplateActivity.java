package sheyko.aleksey.eventy;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.sdk.FeatherActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;

import sheyko.aleksey.eventy.util.IabHelper;
import sheyko.aleksey.eventy.util.IabResult;
import sheyko.aleksey.eventy.util.Inventory;
import sheyko.aleksey.eventy.util.Purchase;


public class TemplateActivity extends Activity {

    public static final String TAG = "TextvitationsTag";

    public static final String SKU_EXTRA_TOOLS = "extra_tools";

    // The helper object
    IabHelper mHelper;

    // Does the user have extra tools?
    boolean mHaveExtraTools = false;

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int AVIARY_EDITOR_REQUEST = 2;

    private String mCategory;
    private int mTemplate;
    private String mImagePath;

    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_template);

        final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkGfnRZnnEzmGYHYnKad6i/YsKjNA8e1JFdQGm2yzbgfeC8h6Gk/4CIrRu3fWgbutuUbir00NZS7oU659AEfH7LdHVyhOjFb65/XSF0atmsxVUmtm56CDYppPrOzh8XZ6nGDTGwq+nrW47xwigJas7gXe/BVFqumJ9gIiyjy/nWHlttD55B34xs4Qp9B9g+rgRuK5Il3dlWboan/Du8FsCkP2jZHrSXatipYyVMWSwc/m0Vwyo//sbmYsB00aOhbLho7/+iX8fsIJ6o5sGRZj71YK7MVJnWSzrU3m5llRqLQy4bmjZ46z+/nktlV6wFEnAt8+OmSKJgUfMfmIBJRXpwIDAQAB";

        sharedPref = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_APPEND);

        mCategory = sharedPref.getString("category", null);


        GridView gridView = (GridView) findViewById(R.id.templatesGrid);
        gridView.setAdapter(new TemplateGridAdapter(TemplateActivity.this, mCategory));

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                mTemplate = position + 1;

                editor = sharedPref.edit();
                editor.putInt("template", mTemplate);
                editor.apply();

                if (mTemplate != -1) {
                    int templateId = getResources().getIdentifier(
                            mCategory.replaceAll(" ", "_").toLowerCase() + "_template_" + mTemplate,
                            "drawable",
                            "sheyko.aleksey.eventy");

                    Bitmap loadedBitmap = BitmapFactory.decodeResource(getResources(), templateId);
                    mImagePath = getImagePath(TemplateActivity.this, loadedBitmap);


                    //Ложу исходное изображение — вдруг захочется сохранить без редактирования
                    editor = sharedPref.edit();
                    editor.putString("image_path", mImagePath);
                    editor.apply();
                }

                mHelper = new IabHelper(TemplateActivity.this, base64EncodedPublicKey);

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

        mImagePath = sharedPref.getString("image_path", null);

        String[] tools;
        if (mHaveExtraTools) {
            tools = new String[]{"TEXT", "DRAW", "CROP", "EFFECTS", "STICKERS", "CONTRAST", "SATURATION", "BORDERS", "BLUR"};
        } else {
            tools = new String[]{"TEXT", "DRAW", "CROP"};
        }

        Intent aviaryIntent = new Intent(TemplateActivity.this, FeatherActivity.class);
        aviaryIntent.putExtra(Constants.EXTRA_TOOLS_LIST, tools);
        aviaryIntent.putExtra(Constants.EXTRA_HIDE_EXIT_UNSAVE_CONFIRMATION, true);
        aviaryIntent.setData(Uri.parse(mImagePath));

        aviaryIntent.putExtra(Constants.EXTRA_IN_API_KEY_SECRET, "85c246dd9c3b9289");
        aviaryIntent.putExtra(Constants.EXTRA_OUTPUT_QUALITY, 100);
        startActivityForResult(aviaryIntent, AVIARY_EDITOR_REQUEST);
    }

    public String getImagePath(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String folder_dcim = "DCIM";

        File f = new File(Environment.getExternalStorageDirectory(),
                folder_dcim);
        if (!f.exists()) {
            f.mkdirs();
        }

        File f1 = new File(Environment.getExternalStorageDirectory() + "/"
                + folder_dcim, "Camera");
        if (!f1.exists()) {
            f1.mkdirs();
        }
        return MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            String mImagePath;
            if (requestCode == PICK_IMAGE_REQUEST) {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = this.getContentResolver().query(
                        selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                mImagePath = cursor.getString(columnIndex);

                //Ложу исходное изображение — вдруг захочется сохранить без редактирования
                editor = sharedPref.edit();
                editor.putString("image_path", mImagePath);
                editor.apply();

                callAviaryIntent();

            } else if (requestCode == AVIARY_EDITOR_REQUEST) {
                mImagePath = data.getDataString();

                //А теперь уже и редактированное изображение ложу
                editor = sharedPref.edit();
                editor.putString("image_path", mImagePath);
                editor.apply();

                startActivity(new Intent(this,
                        InputActivity.class));

            }
        }
    }

    public void pickImageFromGallery(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onResume() {
        super.onResume();

        editor = sharedPref.edit();

        editor.putInt("template", -1);
        editor.putString("address", null);
        editor.putString("city", null);
        editor.putString("location", null);
        editor.apply();
    }

}



