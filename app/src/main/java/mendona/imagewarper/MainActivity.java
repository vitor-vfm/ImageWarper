package mendona.imagewarper;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    static final int REQUEST_LOAD_IMAGE = 12;

    static final int DEFAULT_UNDO_MAX = 15;
    static final int MAXIMUM_UNDO_MAX = 20;
    static final int MINIMUM_UNDO_MAX = 1;

    static final String PROVIDER = "mendona.imagewarper.fileprovider";

    private enum TransformType {
        BLUR,
        ZOOM,
        BLACK_AND_WHITE,
        VORTEX
    }

    private Bitmap currentBitmap;
    private ImageView currentImageView;
    private Uri currentImageUri;
    private Deque<Bitmap> previousBitmaps;
    private int undoMax;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentImageView = (ImageView) findViewById(R.id.currentImageView);
        currentImageView.setOnTouchListener(new OnSwipeTouchListener(MainActivity.this) {
            @Override
            public void onSwipeUp() {
                doTransform(TransformType.ZOOM);
            }

            @Override
            public void onSwipeDown() {
                doTransform(TransformType.BLACK_AND_WHITE);
            }

            @Override
            public void onSwipeRight() {
                doTransform(TransformType.VORTEX);
            }

            @Override
            public void onSwipeLeft() {
                doTransform(TransformType.BLUR);
            }
        });

        undoMax = DEFAULT_UNDO_MAX;
        previousBitmaps = new ArrayDeque<>();
    }

    public void undoTransform(View view) {
        if (previousBitmaps.isEmpty())
            return;

        currentBitmap = previousBitmaps.peekFirst();
        previousBitmaps.removeFirst();
        currentImageView.setImageBitmap(currentBitmap);
    }

    private void pushUndo(Bitmap bitmap) {
        if (bitmap == null)
            return;

        while (previousBitmaps.size() >= undoMax) {
            previousBitmaps.removeLast();
        }

        previousBitmaps.addFirst(bitmap);
    }

    public void changeNumberUndos(View view) {
        final AlertDialog.Builder d = new AlertDialog.Builder(MainActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.undo_number_picker, null);
        d.setTitle("Change number of possible undos");
        d.setView(dialogView);
        final NumberPicker numberPicker = (NumberPicker) dialogView.findViewById(R.id.undoNumberPickerDialog);
        numberPicker.setMaxValue(MAXIMUM_UNDO_MAX);
        numberPicker.setMinValue(MINIMUM_UNDO_MAX);
        numberPicker.setWrapSelectorWheel(false);
        numberPicker.setValue(undoMax);
        d.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                undoMax = numberPicker.getValue();
            }
        });
        d.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        AlertDialog alertDialog = d.create();
        alertDialog.show();
    }

    private Bitmap scaleImage(Bitmap image) {
        if (image == null)
            return null;

        if (image.getWidth() > image.getHeight()) {
            // rotate
            final Matrix m = new Matrix();
            m.postRotate(90);
            image = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), m, true);
        }

        // downscale
        int newWidth = image.getWidth();
        int newHeight = image.getHeight();

        while (newWidth > currentImageView.getMaxWidth() || newHeight > currentImageView.getMaxHeight()) {
            newWidth /= 2;
            newWidth /= 2;
        }

        return newWidth == image.getWidth() ? image : Bitmap.createScaledBitmap(image, newWidth, newHeight, false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK) {
            currentImageUri = data.getData();
        }

        try {
            if (currentImageUri != null) {
                currentBitmap = scaleImage(MediaStore.Images.Media.getBitmap(this.getContentResolver(), currentImageUri));
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Could not load the file at " + currentImageUri, ioe);
        }
        currentImageView.setImageBitmap(currentBitmap);
        previousBitmaps.clear();
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.CANADA).format(new Date());
        try {
            final File directoryPictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(timeStamp, ".jpg", directoryPictures);
        } catch (IOException ioe) {
            throw new RuntimeException("Could not create temp file for image", ioe);
        }
    }

    public void takePicture(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            final File imageFile = createImageFile();
            final Uri imageUri = FileProvider.getUriForFile(MainActivity.this, PROVIDER, imageFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            currentImageUri = imageUri;
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                    1);
        }
    }

    public void loadPicture(View view) {
        final Intent loadPictureIntent = new Intent(Intent.ACTION_GET_CONTENT);
        loadPictureIntent.setType("image/*");
        if (loadPictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(loadPictureIntent, REQUEST_LOAD_IMAGE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.save_button, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.saveItem) {
            final ContentResolver cr = MainActivity.this.getContentResolver();
            MediaStore.Images.Media.insertImage(cr, currentBitmap, "warp", "Image saved from ImageWarp");

            Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
        }
        return true;
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

    public Bitmap vortexTransform() {
        final Bitmap bm = currentBitmap.copy(currentBitmap.getConfig(), true);

        int[] src = new int[bm.getWidth()*bm.getHeight()];
        currentBitmap.getPixels(src, 0, currentBitmap.getWidth(), 0, 0, currentBitmap.getWidth(), currentBitmap.getHeight());

        int[] dst = new int[src.length];

        final int x_c = bm.getHeight() / 2;
        final int y_c = bm.getWidth() / 2;

        final double maxRadius = Math.min(bm.getHeight(), bm.getWidth()) / 2;

        for (int x = 0; x < bm.getHeight(); x++) {
            for (int y = 0; y < bm.getWidth(); y++) {
                final double x_d = (double) (x - x_c);
                final double y_d = (double) (y - y_c);

                final double r = Math.sqrt(Math.pow(x_d, 2) + Math.pow(y_d, 2));
                final double normalizedR = r / maxRadius;

                final double angle = Math.atan2(y_d, x_d);
                final double newAngle = angle + (1/normalizedR) * Math.PI / 4;

                final double u_d = Math.cos(newAngle) * r;
                final double v_d = Math.sin(newAngle) * r;

                final int u = ((int) Math.round(u_d)) + x_c;
                final int v = ((int) Math.round(v_d)) + y_c;

                if (0 <= u && u < bm.getWidth() && 0 <= v && v < bm.getHeight()
                    && 0 <= x && x < bm.getWidth() && 0 <= y && y < bm.getHeight()) {
                    dst[x * bm.getWidth() + y] = src[u * bm.getWidth() + v];
                }
            }
        }

        bm.setPixels(dst, 0, bm.getWidth(), 0, 0, bm.getWidth(),bm.getHeight());
        return bm;
    }

    public void doTransform(final TransformType type) {

        final ProgressDialog loading = ProgressDialog.show(MainActivity.this, null, "Loading...", true, false);

        Thread th = new Thread() {
            @Override
            public void run() {
                pushUndo(currentBitmap);
                switch (type) {
                    case BLUR:
                        currentBitmap = blurTransform();
                        break;
                    case ZOOM:
                        currentBitmap = zoomTransform();
                        break;
                    case BLACK_AND_WHITE:
                        currentBitmap = blackAndWhiteTransform();
                        break;
                    case VORTEX:
                        currentBitmap = vortexTransform();
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
