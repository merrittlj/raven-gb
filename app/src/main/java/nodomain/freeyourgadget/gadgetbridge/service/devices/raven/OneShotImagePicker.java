package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.IOException;

public class OneShotImagePicker {

    public interface BitmapCallback {
        void onBitmapReady(Bitmap bitmap);
    }

    /**
     * Call this from anywhere (Context, Service, etc.).
     * It will start an invisible Activity to pick an image and return a Bitmap.
     */
    public static void pickImage(Context context, BitmapCallback callback) {
        InvisiblePickerActivity.start(context, callback);
    }

    // --- Invisible Activity ---
    public static class InvisiblePickerActivity extends Activity {

        private static BitmapCallback bitmapCallback;

        public static void start(Context context, BitmapCallback callback) {
            bitmapCallback = callback;
            Intent intent = new Intent(context, InvisiblePickerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        @Override
        protected void onStart() {
            super.onStart();
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, 1);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == RESULT_OK && data != null && bitmapCallback != null) {
                Uri uri = data.getData();
                try {
                    Bitmap bitmap;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                        bitmap = ImageDecoder.decodeBitmap(source);
                    } else {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    }
                    bitmapCallback.onBitmapReady(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            finish(); // close invisible activity
        }
    }
}