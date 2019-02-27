package net.opendasharchive.openarchive;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.coursion.freakycoder.mediapicker.galleries.Gallery;

import net.i2p.android.ext.floatingactionbutton.FloatingActionButton;
import net.opendasharchive.openarchive.db.Media;
import net.opendasharchive.openarchive.fragments.MediaListFragment;
import net.opendasharchive.openarchive.onboarding.LoginActivity;
import net.opendasharchive.openarchive.onboarding.OAAppIntro;
import net.opendasharchive.openarchive.services.PirateBoxSiteController;
import net.opendasharchive.openarchive.util.Globals;
import net.opendasharchive.openarchive.util.Prefs;
import net.opendasharchive.openarchive.util.Utility;

import org.witness.proofmode.ProofMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Date;

import io.cleaninsights.sdk.piwik.Measurer;

import static net.opendasharchive.openarchive.util.Utility.getOutputMediaFile;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private CharSequence mTitle;

    private MediaListFragment mCurrentMediaList;

    private ViewPager mPager;
    private MediaProjectPagerAdapter mPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        //otherwise go right into this app;

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle(R.string.main_activity_title);

        mPager = findViewById(R.id.pager);
        mPagerAdapter = new MediaProjectPagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);

        final FloatingActionButton fabMenu = (FloatingActionButton) findViewById(R.id.floating_menu);
        fabMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                importMedia();
            }
        });

        SharedPreferences sharedPref = this.getSharedPreferences(Globals.PREF_FILE_KEY, Context.MODE_PRIVATE);
        if (sharedPref.getBoolean(Globals.PREF_FIRST_TIME_KEY,true))
        {
            Intent intent = new Intent(this, OAAppIntro.class);
            startActivity(intent);

            sharedPref.edit().putBoolean(Globals.PREF_FIRST_TIME_KEY,false).commit();
        }

        //check for any queued uploads and restart
        ((OpenArchiveApp)getApplication()).uploadQueue();

    }

    private void initTabs ()
    {


    }

    @Override
    protected void onResume() {
        super.onResume();

      //  if (fragmentMediaList != null)
        //    fragmentMediaList.refresh();

        /**
        if (Media.getAllMediaAsList().size() == 0)
        {
            findViewById(R.id.media_list).setVisibility(View.GONE);
            findViewById(R.id.media_hint).setVisibility(View.VISIBLE);


        }
        else
        {
            findViewById(R.id.media_list).setVisibility(View.VISIBLE);
            findViewById(R.id.media_hint).setVisibility(View.GONE);

        }**/

        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(INTENT_FILTER_NAME));

        if (getIntent() != null && getIntent().getData() != null) {

            final Snackbar bar = Snackbar.make(mPager, getString(R.string.importing_media), Snackbar.LENGTH_INDEFINITE);
            Snackbar.SnackbarLayout snack_view = (Snackbar.SnackbarLayout)bar.getView();
            snack_view.addView(new ProgressBar(this));
            // The Very Basic
            new AsyncTask<Void, Void, Media>() {
                protected void onPreExecute() {
                    bar.show();

                }
                protected Media doInBackground(Void... unused) {
                    return handleOutsideMedia(getIntent());
                }
                protected void onPostExecute(Media media) {
                    // Post Code
                    if (media != null) {
                        Intent reviewMediaIntent = new Intent(MainActivity.this, ReviewMediaActivity.class);
                        reviewMediaIntent.putExtra(Globals.EXTRA_CURRENT_MEDIA_ID, media.getId());
                        startActivity(reviewMediaIntent);
                    }

                    bar.dismiss();

                    setIntent(null);
                }
            }.execute();



            // handle if started from outside app
        }

        //when the app pauses do a private, randomized-response based tracking of the number of media files
      //  MeasureHelper.track().privateEvent("OpeNArchive", "media imported", Integer.valueOf(fragmentMediaList.getCount()).floatValue(), getMeasurer())
        //        .with(getMeasurer());

    }

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    public final static String INTENT_FILTER_NAME = "MEDIA_UPDATED";

    // Our handler for received Intents. This will be called whenever an Intent
// with an action named "custom-event-name" is broadcasted.
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            Log.d("receiver", "Updating media");

            if (mCurrentMediaList != null)
                mCurrentMediaList.refresh();

        }
    };


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (item.getItemId()) {

            case android.R.id.home:
                return true;
            case R.id.action_settings:
                Intent firstStartIntent = new Intent(this, SettingsActivity.class);
                startActivity(firstStartIntent);
                return true;
            case R.id.action_about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_add_space:
                setupSpace();
                return true;
            case R.id.action_nearby:
                startNearby();
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    private void setupSpace ()
    {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
    }

    /**
    private void logout ()
    {
        new AlertDialog.Builder(this)
                .setTitle(R.string.alert_lbl_logout)
                .setMessage(R.string.alert_logout)
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //nothing
                    }
                })
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Account account = new Account(MainActivity.this, ArchiveSiteController.SITE_NAME);
                        account.setAuthenticated(false);
                        account.setUserName("");
                        account.setCredentials("");
                        account.saveToSharedPrefs(MainActivity.this, ArchiveSiteController.SITE_NAME);

                        account = new Account(MainActivity.this, WebDAVSiteController.SITE_NAME);
                        account.setAuthenticated(false);
                        account.setUserName("");
                        account.setCredentials("");
                        account.saveToSharedPrefs(MainActivity.this, WebDAVSiteController.SITE_NAME);

                        Intent firstStartIntent = new Intent(MainActivity.this, FirstStartActivity.class);
                        startActivity(firstStartIntent);
                    }
                }).create().show();



    }**/

    private boolean mediaExists (Uri uri)
    {

        if (uri.getScheme() == null || uri.getScheme().equals("file"))
        {
            return new File(uri.getPath()).exists();
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult, requestCode:" + requestCode + ", resultCode: " + resultCode);

        // Check which request we're responding to
        if (requestCode == Globals.REQUEST_FILE_IMPORT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK && data != null) {
                final ArrayList<String> selectionResult = data.getStringArrayListExtra("result");

                final Snackbar bar = Snackbar.make(mCurrentMediaList.getView(), R.string.importing_media, Snackbar.LENGTH_INDEFINITE);
                Snackbar.SnackbarLayout snack_view = (Snackbar.SnackbarLayout)bar.getView();
                snack_view.addView(new ProgressBar(this));

                for (String result : selectionResult)
                {

                    String mimeType = Utility.getMediaType(result);

                    new AsyncTask<String, Void, Media>() {
                        protected void onPreExecute() {
                            bar.show();
                        }
                        protected Media doInBackground(String... params) {
                            return  importMedia(new File(params[0]), params[1]);
                        }
                        protected void onPostExecute(Media media) {
                            // Post Code
                            if (media != null && selectionResult.size() == 1) {
                                Intent reviewMediaIntent = new Intent(MainActivity.this, ReviewMediaActivity.class);
                                reviewMediaIntent.putExtra(Globals.EXTRA_CURRENT_MEDIA_ID, media.getId());
                                reviewMediaIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(reviewMediaIntent);
                            }

                            bar.dismiss();

                            mCurrentMediaList.refresh();

                        }
                    }.execute(result,mimeType);

                }
            }
        }

        /**
        String mimeType = null;

        Uri uri = null;

        if (intent != null)
            uri = intent.getData();

        if (uri == null) {
            if (requestCode == Globals.REQUEST_IMAGE_CAPTURE) {
                uri = mCameraUri;
                mimeType = "image/jpeg";
                if (!mediaExists(uri))
                    return;
            } else if (requestCode == Globals.REQUEST_AUDIO_CAPTURE) {
                uri = mAudioUri;
                mimeType = "audio/wav";
                if (!mediaExists(uri))
                    return;
            }


        }


        if (uri != null) {

            if (mimeType == null)
                mimeType = getContentResolver().getType(uri);

            // Will only allow stream-based access to files

            try {
                if (uri.getScheme().equals("content") && Build.VERSION.SDK_INT >= 19) {
                    grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (SecurityException se) {
                Log.d("OA", "security exception accessing URI", se);
            }

        }

        if (resultCode == RESULT_OK) {



            if (null == mimeType) {
                Log.d(TAG, "onActivityResult: Invalid Media Type");
                Toast.makeText(getApplicationContext(), R.string.error_invalid_media_type, Toast.LENGTH_SHORT).show();
            } else {

                final Snackbar bar = Snackbar.make(fragmentMediaList.getView(), R.string.importing_media, Snackbar.LENGTH_INDEFINITE);
                Snackbar.SnackbarLayout snack_view = (Snackbar.SnackbarLayout)bar.getView();
                snack_view.addView(new ProgressBar(this));

                new AsyncTask<String, Void, Media>() {
                    protected void onPreExecute() {
                        bar.show();

                    }
                    protected Media doInBackground(String... params) {
                        return  importMedia(Uri.parse(params[0]), params[1]);
                    }
                    protected void onPostExecute(Media media) {
                        // Post Code
                        if (media != null) {
                            Intent reviewMediaIntent = new Intent(MainActivity.this, ReviewMediaActivity.class);
                            reviewMediaIntent.putExtra(Globals.EXTRA_CURRENT_MEDIA_ID, media.getId());
                            reviewMediaIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(reviewMediaIntent);
                        }

                        bar.dismiss();

                        setIntent(null);
                    }
                }.execute(uri.toString(),mimeType);

            }
        }
        **/


    }

    private Media importMedia (File fileSource, String mimeType)
    {
        String title = fileSource.getName();
        File fileImport = getOutputMediaFile(title);
        boolean success = fileImport.getParentFile().mkdirs();
        Log.d(TAG,"create parent folders, success=" + success);

        try {
            boolean imported = Utility.writeStreamToFile(new FileInputStream(fileSource),fileImport);
            if (!imported)
                return null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        // create media
        Media media = new Media();
        media.setOriginalFilePath(Uri.fromFile(fileImport).toString());
        media.setMimeType(mimeType);
        media.setCreateDate(new Date(fileSource.lastModified()));
        media.setUpdateDate(media.getCreateDate());
        media.status = Media.STATUS_LOCAL;

        if (title != null)
            media.setTitle(title);
        media.save();

        //if not offline, then try to notarize
        if (!PirateBoxSiteController.isPirateBox(this)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("autoNotarize", false).commit();
        }

        /**
        String hash = ProofMode.generateProof(this, Uri.fromFile(fileSource));
        if (!TextUtils.isEmpty(hash))
        {
            media.setMediaHash(hash.getBytes());
            media.save();
        }**/


        return media;
    }

    private Media importMedia (Uri uri, String mimeType)
    {
        String title = Utility.getUriDisplayName(this,uri);
        File fileImport = getOutputMediaFile(title);
        try {
            boolean imported = Utility.writeStreamToFile(getContentResolver().openInputStream(uri),fileImport);
            if (!imported)
                return null;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        // create media
        Media media = new Media();
        media.setOriginalFilePath(Uri.fromFile(fileImport).toString());
        media.setMimeType(mimeType);
        media.setCreateDate(new Date());
        media.status = Media.STATUS_LOCAL;

        if (title != null)
            media.setTitle(title);
        media.save();

        //if not offline, then try to notarize
        if (!PirateBoxSiteController.isPirateBox(this)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("autoNotarize", false).commit();
        }

        String hash = ProofMode.generateProof(this, uri);
        if (!TextUtils.isEmpty(hash))
        {
            media.setMediaHash(hash.getBytes());
            media.save();
        }


        return media;
    }

    private Media handleOutsideMedia(Intent intent) {

        Media media = null;

        if (intent != null && intent.getAction()!= null
          && intent.getAction().equals(Intent.ACTION_SEND)) {

            String mimeType = intent.getType();

            Uri uri = intent.getData();


            if (uri == null)
            {
                if (Build.VERSION.SDK_INT >= 16 && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                    uri = intent.getClipData().getItemAt(0).getUri();
                }
                else {
                    return null;
                }
            }


            media = importMedia(uri, mimeType);

        }

        return media;
    }


    private void startNearby ()
    {
        if (checkNearbyPermissions()) {
            Intent intent = new Intent(this, NearbyActivity.class);
            startActivity(intent);
        }
    }

    private void importMedia ()
    {
        if (!askForPermission(Manifest.permission.READ_EXTERNAL_STORAGE,1)) {

            Intent intent = new Intent(this,Gallery.class);

// Set the title for toolbar
            intent.putExtra("title", getString(R.string.menu_import_media));
// Mode 1 for both images and videos selection, 2 for images only and 3 for videos!
            intent.putExtra("mode", 1);
            startActivityForResult(intent, Globals.REQUEST_FILE_IMPORT);

        }
    }



    private boolean askForPermission(String permission, Integer requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, permission)) {

                //This is called if user has denied the permission before
                //In this case I am just asking the permission again
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, requestCode);

            } else {

                ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, requestCode);
            }

            return true;
        }

        return false;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                askForPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 2);
                break;
            case 2:
                break;
        }

    }


    private Measurer getMeasurer() {
        return ((OpenArchiveApp) getApplication()).getCleanInsightsApp().getMeasurer();
    }



    private boolean checkNearbyPermissions ()
    {
        boolean allowed = false;

        allowed = !askForPermission("android.permission.ACCESS_FINE_LOCATION", 3);

        if (!allowed)
            return false;

        if (Prefs.getNearbyUseBluetooth()) {
            allowed = !askForPermission("android.permission.BLUETOOTH", 1);
            if (!allowed)
                return false;

            allowed = !askForPermission("android.permission.BLUETOOTH_ADMIN", 2);
            if (!allowed)
                return false;
        }

        if (Prefs.getNearbyUseWifi()) {
            allowed = !askForPermission("android.permission.ACCESS_WIFI_STATE", 4);
            if (!allowed)
                return false;
            allowed = !askForPermission("android.permission.CHANGE_WIFI_STATE", 5);
            if (!allowed)
                return false;
            allowed = !askForPermission("android.permission.ACCESS_NETWORK_STATE", 6);
            if (!allowed)
                return false;
            allowed = !askForPermission("android.permission.CHANGE_NETWORK_STATE", 7);
            if (!allowed)
                return false;
        }

        return allowed;
    }

    // Since this is an object collection, use a FragmentStatePagerAdapter,
// and NOT a FragmentPagerAdapter.
    public class MediaProjectPagerAdapter extends FragmentStatePagerAdapter {
        public MediaProjectPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            Fragment fragment = new MediaListFragment();
            Bundle args = new Bundle();
            // Our object is just an integer :-P
           // args.putInt(MediaListFragment.ARG_OBJECT, i + 1);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "Project " + (position + 1);
        }
    }

}
