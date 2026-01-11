package mgks.os.swv;

import mgks.os.swv.R;

/*
  Smart WebView v8
  https://github.com/mgks/Android-SmartWebView
*/

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private WebView asw_view;
    private ProgressBar asw_progress;
    private ValueCallback<Uri[]> asw_file_path;
    private final ActivityResultLauncher<Intent> fileUploadLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Uri[] results = null;
                    Intent data = result.getData();
                    if (data != null) {
                        String dataString = data.getDataString();
                        ClipData clipData = data.getClipData();
                        if (clipData != null) {
                            results = new Uri[clipData.getItemCount()];
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                results[i] = clipData.getItemAt(i).getUri();
                            }
                        } else if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }

                    if (results == null) {
                        if (SWVContext.asw_pcam_message != null) {
                            // MODIFIKASI DISINI: Beri Watermark jika dari Kamera
                            Uri capturedUri = Uri.parse(SWVContext.asw_pcam_message);
                            Uri watermarkedUri = applyWatermark(capturedUri);
                            results = new Uri[]{watermarkedUri};
                        } else if (SWVContext.asw_vcam_message != null) {
                            results = new Uri[]{Uri.parse(SWVContext.asw_vcam_message)};
                        }
                    }
                    asw_file_path.onReceiveValue(results);
                } else {
                    asw_file_path.onReceiveValue(null);
                }
                asw_file_path = null;
            }
    );

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        asw_view = findViewById(R.id.asw_view);
        asw_progress = findViewById(R.id.asw_progress);

        WebSettings webSettings = asw_view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webSettings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }

        asw_view.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (asw_file_path != null) {
                    asw_file_path.onReceiveValue(null);
                }
                asw_file_path = filePathCallback;
                Intent takePictureIntent = null;
                if (SWVContext.ASWP_CAMUPLOAD) {
                    takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        File photoFile = null;
                        try {
                            photoFile = create_image();
                            takePictureIntent.putExtra("PhotoPath", SWVContext.asw_pcam_message);
                        } catch (IOException ex) {
                            Log.e(TAG, "Image file creation failed", ex);
                        }
                        if (photoFile != null) {
                            SWVContext.asw_pcam_message = "file:" + photoFile.getAbsolutePath();
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                        } else {
                            takePictureIntent = null;
                        }
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType(SWVContext.ASWV_F_TYPE);

                Intent[] intentArray;
                if (takePictureIntent != null) {
                    intentArray = new Intent[]{takePictureIntent};
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Pilih Aksi");
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                fileUploadLauncher.launch(chooserIntent);
                return true;
            }
        });

        asw_view.setWebViewClient(new WebViewCallback());
        asw_view.loadUrl(SWVContext.ASWV_URL);
    }

    private File create_image() throws IOException {
        @SuppressLint("SimpleDateFormat")
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    // FUNGSI WATERMARK
    private Uri applyWatermark(Uri sourceUri) {
        try {
            InputStream imageStream = getContentResolver().openInputStream(sourceUri);
            Bitmap sourceBitmap = BitmapFactory.decodeStream(imageStream);
            Bitmap destBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(destBitmap);

            Paint paintRect = new Paint();
            paintRect.setColor(Color.BLACK);
            paintRect.setAlpha(150);
            canvas.drawRect(0, canvas.getHeight() - 320, canvas.getWidth(), canvas.getHeight(), paintRect);

            Paint paintText = new Paint();
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(canvas.getWidth() / 25f);
            paintText.setAntiAlias(true);

            String timeStamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
            canvas.drawText("Kecamatan Cluring, Banyuwangi", 50, canvas.getHeight() - 210, paintText);
            canvas.drawText("Lat: -8.411578 Long: 114.189753", 50, canvas.getHeight() - 140, paintText);
            canvas.drawText(timeStamp, 50, canvas.getHeight() - 70, paintText);

            String path = MediaStore.Images.Media.insertImage(getContentResolver(), destBitmap, "SIKAP_" + System.currentTimeMillis(), null);
            return Uri.parse(path);
        } catch (Exception e) {
            return sourceUri;
        }
    }

    private class WebViewCallback extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            asw_progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            asw_progress.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        }
    }
}