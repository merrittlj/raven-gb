package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import java.io.IOException;

public class OneShotImagePicker {
    private static final int SELECT_PHOTO = 1001;

    public interface BitmapCallback {
        void onBitmapReady(Bitmap bitmap);
    }

    public static void pickImage(Context context, BitmapCallback callback) {
        InvisiblePickerActivity.start(context, callback);
    }

    public static class InvisiblePickerActivity extends Activity {
        private static BitmapCallback bitmapCallback;

        public static void start(Context context, BitmapCallback callback) {
            bitmapCallback = callback;
            Intent intent = new Intent(context, InvisiblePickerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, SELECT_PHOTO);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == SELECT_PHOTO && resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null && bitmapCallback != null) {
                    try {
                        Bitmap bitmap = loadBitmapFromUri(uri);
                        bitmapCallback.onBitmapReady(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                        bitmapCallback.onBitmapReady(null);
                    }
                }
            }
            finish();
        }

        private Bitmap loadBitmapFromUri(Uri uri) throws IOException {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Android 9+ (API 28+) - Use ImageDecoder
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    // Force software bitmap (not hardware)
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                // Older versions - Use MediaStore
                return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
        }
    }
}