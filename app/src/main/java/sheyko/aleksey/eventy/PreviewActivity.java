package sheyko.aleksey.eventy;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.services.urlshortener.Urlshortener;
import com.google.api.services.urlshortener.model.Url;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public class PreviewActivity extends Activity {

    private String mImagePath;

    private String mStreet;
    private String mCity;

    private TextView locationIndicator;
    private double lat;
    private double lng;

    private String url;
    private String shortUrl;
    private String invitationText;

    private Intent shareIntent;

    SharedPreferences sharedPref;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preview);

        sharedPref = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_APPEND);

        lat = getDouble(sharedPref, "latitude", -33.867);
        lng = getDouble(sharedPref, "longitude", 151.206);

        try {
            new ShortenUrlTask().execute("http://maps.google.com/?q=" + lat + "," + lng).get(1000, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}

        locationIndicator = (TextView) findViewById(R.id.locationIndicator);
        locationIndicator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String label = "Event";
                String uriBegin = "geo:" + lat + "," + lng;
                String query = lat + "," + lng + "(" + label + ")";
                String encodedQuery = Uri.encode(query);
                String uriString = uriBegin + "?q=" + encodedQuery + "&z=16";
                Uri uri = Uri.parse(uriString);
                shareIntent.putExtra("EXTRA_LOCATION", uri);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(PreviewActivity.this,
                            "Please install Google Maps to see the map", Toast.LENGTH_LONG).show();

                    final String appPackageName = "com.google.android.apps.maps";
                    try {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                        } catch (android.content.ActivityNotFoundException anfe) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + appPackageName)));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        });


        mImagePath = sharedPref.getString("image_path", null);

        ImageView imagePreview = (ImageView) findViewById(R.id.imagePreview);

        if (mImagePath != null)
            imagePreview.setImageBitmap(BitmapFactory.decodeFile(mImagePath));

    }

    double getDouble(final SharedPreferences prefs, final String key, final double defaultValue) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(defaultValue)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        mStreet = sharedPref.getString("street", null);
        mCity = sharedPref.getString("city", null);

        if (mStreet == null || mCity == null) {
            locationIndicator.setVisibility(View.GONE);
        } else {
            locationIndicator.setText(mStreet + " " + mCity);
        }

    }


    public void saveImage(View view) {

        // Get image folder on SD card
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + File.separator + "Eventy");

        String timeStamp = new SimpleDateFormat("MMdd_HHmmss").format(new Date());

        //Create image file
        String fileName = "Event_card_" + timeStamp + ".png";
        File file = new File(myDir.getPath(), fileName);

        try {
            FileOutputStream out = new FileOutputStream(file);

            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(mImagePath));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "Image saved to SD card", Toast.LENGTH_SHORT).show();

    }


    private Intent createShareIntent() {

        mStreet = sharedPref.getString("street", null);
        mCity = sharedPref.getString("city", null);
        mImagePath = sharedPref.getString("image_path", null);


        shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setType("text/plain");
        shareIntent.setType("image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Invitation");
        shareIntent.putExtra(Intent.EXTRA_TEXT, invitationText);
        shareIntent.putExtra(Intent.EXTRA_STREAM, saveMediaEntry(mImagePath));
        shareIntent.putExtra("sms_body", invitationText);


        return shareIntent;
    }


    Uri saveMediaEntry(String imagePath) {

        ContentValues v = new ContentValues();
        v.put(Images.Media.MIME_TYPE, "image/jpeg");

        File f = new File(imagePath);
        File parent = f.getParentFile();
        String path = parent.toString().toLowerCase();
        String name = parent.getName().toLowerCase();
        v.put(Images.ImageColumns.BUCKET_ID, path.hashCode());
        v.put(Images.ImageColumns.BUCKET_DISPLAY_NAME, name);
        v.put(Images.Media.SIZE, f.length());

        v.put("_data", imagePath);
        ContentResolver c = getContentResolver();

        return c.insert(Images.Media.INTERNAL_CONTENT_URI, v);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preview, menu);

        MenuItem item = menu.findItem(R.id.action_share);
        ShareActionProvider mShareActionProvider = (ShareActionProvider) item.getActionProvider();

        if (mShareActionProvider != null) {
            mShareActionProvider.setShareIntent(createShareIntent());
        }

        return super.onCreateOptionsMenu(menu);
    }

    public void goBackToEdit(View view) {
        super.onBackPressed();
    }


    private class ShortenUrlTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {


                Urlshortener.Builder builder = new Urlshortener.Builder(AndroidHttp.newCompatibleTransport(), AndroidJsonFactory.getDefaultInstance(), null);
                Urlshortener urlshortener = builder.build();

                com.google.api.services.urlshortener.model.Url url = new Url();
                url.setLongUrl(params[0]);
                try {
                    url = urlshortener.url().insert(url).execute();
                } catch (Exception ignored) {}

                shortUrl = url.getId();

            return shortUrl;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            invitationText = null;
            if (mStreet != null && mCity != null) {
                if (shortUrl != null) {
                    invitationText = "You're invited!" + "\n\n"
                            + "Location: " + mStreet + " " + mCity + "\n"
                            + shortUrl + "\n\n"
                            + "#Textvitations";
                } else {
                    invitationText = "You're invited!" + "\n\n"
                            + "Location: " + mStreet + " " + mCity + "\n"
                            + "http://maps.google.com/?q=" + lat + "," + lng + "\n\n"
                            + "#Textvitations";
                }
            } else {
                invitationText = "You're invited!" + "\n\n"
                        + "#Textvitations";
            }
        }
    }
}
