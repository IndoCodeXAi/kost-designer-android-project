package com.kostdesigner.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class WebAppInterface {

    private final Activity activity;

    WebAppInterface(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void shareImage(final String base64Png) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    File imagesDir = new File(activity.getCacheDir(), "images");
                    if (!imagesDir.exists()) {
                        imagesDir.mkdirs();
                    }
                    File imageFile = new File(imagesDir, "kost_design_" + System.currentTimeMillis() + ".png");

                    byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
                    FileOutputStream fos = new FileOutputStream(imageFile);
                    fos.write(bytes);
                    fos.close();

                    Uri contentUri = FileProvider.getUriForFile(
                            activity,
                            activity.getPackageName() + ".fileprovider",
                            imageFile
                    );

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/png");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    activity.startActivity(Intent.createChooser(shareIntent, "Bagikan Desain Kamar"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
