package nodomain.freeyourgadget.gadgetbridge.service.devices.raven;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;

public class OneShotImagePicker {
    private static int SELECT_PHOTO = 1001;
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
            startActivityForResult(intent, SELECT_PHOTO);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
            super.onActivityResult(requestCode, resultCode, intent);
            if (requestCode == SELECT_PHOTO && resultCode == RESULT_OK && intent != null && bitmapCallback != null) {
                Uri uri = intent.getData();

                // Let's read picked image path using content resolver
                String[] filePath = { MediaStore.Images.Media.DATA };
                Cursor cursor = getContentResolver().query(uri, filePath, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePath[0]);
                if (columnIndex == -1) return;
                String imagePath = cursor.getString(columnIndex);

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);

                bitmapCallback.onBitmapReady(bitmap);

                cursor.close();
            }
            finish();
        }
    }
}