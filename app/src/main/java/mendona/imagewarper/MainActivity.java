package mendona.imagewarper;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
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
        TEST,
        BLUR,
        ZOOM,
        BLACK_AND_WHITE
    }

    private Bitmap currentBitmap;
    private ImageView currentImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentImageView = (ImageView) findViewById(R.id.currentImageView);
        currentImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doTransform(TransformType.BLACK_AND_WHITE);
            }
        });
        currentImageView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                doTransform(TransformType.BLUR);
                return true;
            }
        });
    }

    private Bitmap scaleImage(Bitmap image) {
        return Bitmap.createScaledBitmap(image, currentImageView.getWidth(), currentImageView.getHeight(), false);
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
                currentBitmap = scaleImage(MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri));
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

    public Bitmap blurTransform() {
        Bitmap bm = Bitmap.createScaledBitmap(currentBitmap,
                                              Math.max(currentBitmap.getWidth()/4, 4),
                                              Math.max(currentBitmap.getHeight()/4, 4),
                                              false);

        int[] src = new int[bm.getWidth()*bm.getHeight()];
        int[] dst = new int[bm.getWidth()*bm.getHeight()];

        bm.getPixels(src, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());

        for (int x = 0; x < bm.getHeight(); x++) {
            for (int y = 0; y < bm.getWidth(); y++) {
                int alpha = 0, red = 0, green = 0, blue = 0;
                int wSize = 0;

                for (int wx = Math.max(x-1, 0); wx < Math.min(x+2, bm.getHeight()); wx++) {
                    for (int wy = Math.max(y-1, 0); wy < Math.min(y+2, bm.getWidth()); wy++) {
                        final int pixel = src[wx*bm.getWidth() + wy];
                        alpha += Color.alpha(pixel);
                        red += Color.red(pixel);
                        green += Color.green(pixel);
                        blue += Color.blue(pixel);
                        wSize++;
                    }
                }

                dst[x*bm.getWidth() + y] = Color.argb(alpha/wSize, red/wSize, green/wSize, blue/wSize);
            }
        }

        bm.setPixels(dst, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());

        return bm;
    }

    private void zoomCoordinateChange(int x, int y, int x_c, int y_c, double factor, int[] res) {
        final double x_d = (double) (x - x_c);
        final double y_d = (double) (y - y_c);

        final double r = Math.sqrt(Math.pow(x_d, 2) + Math.pow(y_d, 2));
        final double rn = r * factor;

        final double u_d = rn * (x_d / r);
        final double v_d = rn * (y_d / r);

        res[0] = ((int)Math.round(u_d)) + x_c;
        res[1] = ((int)Math.round(v_d)) + y_c;
    }

    public Bitmap zoomTransform() {
        // TODO: fix
        final double zoomFactor = 1.25;
        final Bitmap bm = currentBitmap.copy(currentBitmap.getConfig(), true);

        int[] src = new int[bm.getWidth()*bm.getHeight()];
        currentBitmap.getPixels(src, 0, currentBitmap.getWidth(), 0, 0, currentBitmap.getWidth(), currentBitmap.getHeight());

        int[] dst = new int[bm.getWidth()*bm.getHeight()];

        final int x_centre = bm.getHeight() / 2;
        final int y_centre = bm.getWidth() / 2;

        final int[] newCoord = new int[2];

        for (int x = 0; x < currentBitmap.getHeight(); x++) {
            for (int y = 0; y < currentBitmap.getWidth(); y++) {
                zoomCoordinateChange(x, y, x_centre, y_centre, zoomFactor, newCoord);
                final int u = newCoord[0];
                final int v = newCoord[1];

                if (u >= 0 && u < bm.getHeight() && v >= 0 && v < bm.getWidth()) {
                    dst[u * bm.getWidth() + v] = src[x * bm.getWidth() + y];
                }
            }
        }

        for (int u = 0; u < bm.getHeight(); u++) {
            for (int v = 0; v < bm.getWidth(); v++) {
                if (dst[u * bm.getWidth() + v] == 0) {
                    zoomCoordinateChange(u, v, x_centre, y_centre, 1/zoomFactor, newCoord);
                    final int x = newCoord[0];
                    final int y = newCoord[1];

                    dst[u * bm.getWidth() + v] = src[x * bm.getWidth() + y];
                }
            }
        }

        bm.setPixels(dst, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
        return bm;
    }

    public Bitmap blackAndWhiteTransform() {
        final Bitmap bm = currentBitmap.copy(currentBitmap.getConfig(), true);
        int[] buf = new int[bm.getWidth()*bm.getHeight()];
        bm.getPixels(buf, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());

        for (int p = 0; p < bm.getWidth()*bm.getHeight(); p++) {
            int luma = (int)(0.299*Color.red(buf[p]) + 0.587*Color.green(buf[p]) + 0.114*Color.blue(buf[p]));
            buf[p] = Color.argb(Color.alpha(buf[p]), luma, luma, luma);
        }

        bm.setPixels(buf, 0, bm.getWidth(), 0, 0, bm.getWidth(), bm.getHeight());
        return bm;
    }

    public Bitmap testTransform() {
        final Bitmap bm = currentBitmap.copy(currentBitmap.getConfig(), true);
        for (int x = 0; x < currentBitmap.getWidth(); x++) {
            for (int y = 0; y < currentBitmap.getHeight(); y++) {
                int u = x + 30;
                int v = y + 40;
                if (u >= 0 && u < bm.getWidth() && v >= 0 && v < bm.getHeight()) {
                    bm.setPixel(u, v, currentBitmap.getPixel(x, y));
                }
            }
        }
        return bm;
    }

    public void doTransform(final TransformType type) {

        final ProgressDialog loading = ProgressDialog.show(MainActivity.this, null, "Loading...", true, false);

        Thread th = new Thread() {
            @Override
            public void run() {
                switch (type) {
                    case TEST:
                        currentBitmap = testTransform();
                        break;
                    case BLUR:
                        currentBitmap = blurTransform();
                        break;
                    case ZOOM:
                        currentBitmap = zoomTransform();
                        break;
                    case BLACK_AND_WHITE:
                        currentBitmap = blackAndWhiteTransform();
                        break;
                }
                loading.dismiss();
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
