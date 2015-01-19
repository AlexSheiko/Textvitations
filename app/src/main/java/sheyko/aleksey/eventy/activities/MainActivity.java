package sheyko.aleksey.eventy.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.purplebrain.adbuddiz.sdk.AdBuddiz;

import sheyko.aleksey.eventy.R;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AdBuddiz.setPublisherKey("TEST_PUBLISHER_KEY");
        AdBuddiz.setTestModeActive();
        AdBuddiz.cacheAds(this); // this = current Activity
    }

    public void navigateToInputActivity(View view) {
        startActivity(new Intent(this,
                CategoryActivity.class));
    }
}
