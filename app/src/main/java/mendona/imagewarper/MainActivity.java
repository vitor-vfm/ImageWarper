package mendona.imagewarper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    static final int REQUEST_LOAD_IMAGE = 12;

    private class Coord {
        int x;
        int y;

        Coord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        Coord(float x, float y) {
            this.x = Math.round(x);
            this.y = Math.round(y);
        }
    }

    private enum TransformType {
        BLUR,
        TEST,
        BULGE_FILTER
    }

    private Bitmap currentBitmap;
    private ImageView currentImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentImageView = (ImageView) findViewById(R.id.currentImageView);
        currentImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    doTransform(TransformType.TEST);
                }
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            currentBitmap = (Bitmap) extras.get("data");
        } else if (requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            try {
                currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
            } catch (IOException ioe) {
                throw new RuntimeException("Could not load the file at " + selectedImageUri, ioe);
            }
        }
        currentImageView.setImageBitmap(currentBitmap);
    }

    public void takePicture(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    public void loadPicture(View view) {
        Intent loadPictureIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (loadPictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(loadPictureIntent, REQUEST_LOAD_IMAGE);
        }
    }

    public Coord screenCoord2BitmapCoord(Coord original) {
        ImageView imageView = (ImageView) findViewById(R.id.currentImageView);

        int xInsideView = original.x - imageView.getLeft();
        int yInsideView = original.y - imageView.getTop();

        int x = xInsideView * currentBitmap.getWidth() / imageView.getWidth();
        int y = yInsideView * currentBitmap.getHeight() / imageView.getHeight();

        return new Coord(x, y);
    }

    public void bulgeFilterTransform(Bitmap bm) {
        for (int x = 0; x < currentBitmap.getWidth(); x++) {
            final double x_d = (double) x;
            for (int y = 0; y < currentBitmap.getHeight(); y++) {
                final double y_d = (double) y;

                double r = Math.sqrt(Math.pow(x_d - 0.5, 2) + Math.pow(y_d - 0.5, 2));
                double rn = Math.pow(r, 2.5) / 0.5;

                double u_d = rn * (x_d - 0.5) / r + 0.5;
                double v_d = rn * (y_d - 0.5) / r + 0.5;

                int u = (int) Math.round(u_d);
                int v = (int) Math.round(v_d);

                if (u >= 0 && u <= bm.getWidth() && v >= 0 && v <= bm.getHeight()) {
                    bm.setPixel(u, v, currentBitmap.getPixel(x, y));
                }
            }
        }
    }

    public void testTransform(Bitmap bm) {
        for (int x = 0; x < currentBitmap.getWidth(); x++) {
            for (int y = 0; y < currentBitmap.getHeight(); y++) {
                int u = x + 30;
                int v = y + 40;
                if (u >= 0 && u < bm.getWidth() && v >= 0 && v < bm.getHeight()) {
                    bm.setPixel(u, v, currentBitmap.getPixel(x, y));
                }
            }
        }
    }

    public void doTransform(final TransformType type) {

        final Bitmap bm = currentBitmap.copy(currentBitmap.getConfig(), true);
        final ProgressDialog loading = ProgressDialog.show(MainActivity.this, "Loading", "Loading...", true, false);

        Thread th = new Thread() {
            @Override
            public void run() {
                switch (type) {
                    case TEST:
                        testTransform(bm);
                        break;
                    case BULGE_FILTER:
                        bulgeFilterTransform(bm);
                        break;
                }
                loading.dismiss();
                currentBitmap = bm;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentImageView.setImageBitmap(currentBitmap);
                    }
                });
            }
        };

        th.start();
    }
}
