package maulik.sendanything1;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class DriveUploadActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    FileContent fileMain;
    GoogleAccountCredential mCredential;
    ProgressDialog mProgress;
    static String fileDownloadLink = "";
    static ClipData oldClip;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {DriveScopes.DRIVE};

    static final int REQUEST_SELECT_FILE = 1004;
    Uri fileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drive_upload);

        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        oldClip = manager.getPrimaryClip();

        selectFile();
    }

    private void selectFile() {
        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        fileIntent.setType("*/*"); // intent type to filter application based on your requirement
        startActivityForResult(Intent.createChooser(fileIntent, "Select file"), REQUEST_SELECT_FILE);
    }

    public void init() {
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        connect();
    }

    public void connect() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            Toast.makeText(DriveUploadActivity.this, "No internet connection", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            new DriveUploadActivity.DriveUploadAsync(mCredential).execute(fileUri);
        }
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                connect();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
            Toast.makeText(DriveUploadActivity.this, "There is a problem with the permission, please allow the permission from settings.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_SELECT_FILE:
                if (resultCode == RESULT_OK) {
                    fileUri = data.getData();
                    init();
                }
                else
                {
                    finish();
                }
                break;
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this,
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    connect();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        connect();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    connect();
                }
                break;
        }
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                DriveUploadActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    public boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }

    private class DriveUploadAsync extends AsyncTask<Uri, Integer, String> {

        private NotificationManager mNotifyManager;
        private NotificationCompat.Builder mBuilder;
        int id = 6296;

        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String fileName, fileType;
        private long fileSize;

        DriveUploadAsync(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("DriveUploadExample")
                    .build();

            mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(DriveUploadActivity.this);
            mBuilder.setContentTitle("Upload")
                    .setContentText("Upload in progress")
                    .setSmallIcon(R.drawable.ic_cloud_upload_black_24dp);
        }

        @Override
        protected void onPreExecute() {
            mBuilder.setProgress(100, 0, false);
            mBuilder.setOngoing(true);
            mNotifyManager.notify(id, mBuilder.build());
            Toast.makeText(getApplicationContext(),"Uploading...",Toast.LENGTH_SHORT).show();
            onBackPressed();
        }

        @Override
        protected String doInBackground(Uri... params) {
            Uri uri = params[0];
            fileName = getFilename(uri);
            fileType = getFileType(uri);
            fileSize = getFileSize(uri);
            java.io.File ioFile = null;
            try {
                ioFile = StreamUtil.stream2file(getContentResolver().openInputStream(uri));
            } catch (IOException e) {
                Log.i("Problem", "in Stream Util");
                e.printStackTrace();
            }

            File fileMetaData = new File();
            fileMetaData.setName(fileName);

            fileMain = new FileContent(fileType, ioFile);

            //Start Upload

            try {
                Drive.Files.Create create = mService.files().create(fileMetaData, fileMain).setFields("id");
                MediaHttpUploader uploader = create.getMediaHttpUploader();
                uploader.setDirectUploadEnabled(false);
                uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                uploader.setProgressListener(new MediaHttpUploaderProgressListener() {
                    public void progressChanged(MediaHttpUploader mediaHttpUploader) throws IOException {
                        switch (mediaHttpUploader.getUploadState()) {
                            case INITIATION_STARTED:
                                Log.i("init", "yes");

                                break;
                            case INITIATION_COMPLETE:
                                Log.i("initfin", "yes");
                                break;
                            case MEDIA_IN_PROGRESS:
                                int progress = (int) (mediaHttpUploader.getProgress() * 100);
                                Log.i("progress", String.valueOf(progress));
                                publishProgress(progress);
                                break;
                            case MEDIA_COMPLETE:
                                Log.i("prog", "y");
                                break;
                        }
                    }
                });
                File fileToBeUploaded = create.execute();
                Permission permission = new Permission();
                permission.setType("anyone");
                permission.setRole("reader");
                mService.permissions().create(fileToBeUploaded.getId(),permission).execute();
                return fileToBeUploaded.getId();
            } catch (IOException e) {
                e.printStackTrace();
                return "error";
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mBuilder.setProgress(100, values[0], false);
            mNotifyManager.notify(id, mBuilder.build());
        }

        @Override
        protected void onPostExecute(String s) {
            if (!s.equals("error")) {
                Toast.makeText(DriveUploadActivity.this, "Upload complete", Toast.LENGTH_SHORT).show();
                fileDownloadLink = "File Name: *"+ fileName+"*\nFile Type: *"+fileType+"*\nFile Size: *"+readableFileSize(fileSize)+"*\nhttps://drive.google.com/uc?export=download&id="+s;
                SendAnythingService.setLinkText();
                mBuilder.setContentText("Upload complete");

            } else {
                Toast.makeText(DriveUploadActivity.this, s, Toast.LENGTH_SHORT).show();
                mBuilder.setContentText("Upload failed");
            }
            mBuilder.setOngoing(false);
            // Removes the progress bar
            mBuilder.setProgress(0, 0, false);
            mNotifyManager.notify(id, mBuilder.build());
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    Log.i("hi", mLastError.getMessage());
                }
            } else {
                Log.i("ok", "Request cancelled.");
            }
        }
    }


    public String getFilename(Uri uri) {
        String path = "";
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};

        ContentResolver cr = getApplicationContext().getContentResolver();
        Cursor metaCursor = cr.query(uri, projection, null, null, null);
        if (metaCursor != null) {
            try {
                if (metaCursor.moveToFirst()) {
                    path = metaCursor.getString(0);
                }
            } finally {
                metaCursor.close();
            }
        }
        Log.i("gg", path);
        return path;
    }

    public String getFileType(Uri uri) {
        return getContentResolver().getType(uri);
    }

    public long getFileSize(Uri uri) {
        Cursor returnCursor =
                getContentResolver().query(uri, null, null, null, null);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();

        Log.i("size", Long.toString(returnCursor.getLong(sizeIndex)));
        return returnCursor.getLong(sizeIndex);
    }

    public static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
