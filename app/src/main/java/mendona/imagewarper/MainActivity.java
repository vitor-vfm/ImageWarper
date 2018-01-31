package mendona.imagewarper;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.NumberPicker;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    static final int REQUEST_LOAD_IMAGE = 12;

    static final int DEFAULT_UNDO_MAX = 15;
    static final int MAXIMUM_UNDO_MAX = 20;
    static final int MINIMUM_UNDO_MAX = 1;

    private enum TransformType {
        BLUR,
        ZOOM,
        BLACK_AND_WHITE,
        VORTEX
    }

    private Bitmap currentBitmap;
    private ImageView currentImageView;
    private Deque<Bitmap> previousBitmaps;
    private int undoMax;

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
                doTransform(TransformType.VORTEX);
                return true;
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
        final Bitmap scaled = Bitmap.createScaledBitmap(image, image.getWidth() / 4, image.getHeight() / 4, false);
        Matrix m = new Matrix();
        m.postRotate(90);
        return Bitmap.createBitmap(scaled, 0, 0, scaled.getWidth(), scaled.getHeight(), m, true);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            try {
                currentBitmap = scaleImage(MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri));
            } catch (IOException ioe) {
                throw new RuntimeException("Could not load the file at " + selectedImageUri, ioe);
            }
        }
        currentImageView.setImageBitmap(currentBitmap);
        previousBitmaps.clear();
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

        int[] dst = new int[bm.getWidth()*bm.getHeight()];

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

                if (0 <= u &&u < bm.getWidth() && 0 <= v && v < bm.getHeight()) {
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
