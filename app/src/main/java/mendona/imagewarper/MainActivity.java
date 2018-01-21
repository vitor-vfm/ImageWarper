package mendona.imagewarper;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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

    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView imageView = (ImageView) findViewById(R.id.currentImageView);
        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    testTransformation(new Coord(event.getX(), event.getY()));
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
            updateScreenImage();
        }

        if (requestCode == REQUEST_LOAD_IMAGE && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            try {
                currentBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
            } catch (IOException ioe) {
                throw new RuntimeException("Could not load the file at " + selectedImageUri, ioe);
            }
            updateScreenImage();
        }
    }

    private void updateScreenImage() {
        ImageView currentImageView = (ImageView) findViewById(R.id.currentImageView);
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

    public void testTransformation(Coord clickPoint) {
        Coord point = screenCoord2BitmapCoord(clickPoint);
        Bitmap nextBitmap = currentBitmap.copy(currentBitmap.getConfig(), true);

        for (int i = 0; i < nextBitmap.getHeight(); i++) {
            nextBitmap.setPixel(point.x, i, Color.RED);
        }

        for (int j = 0; j < nextBitmap.getWidth(); j++) {
            nextBitmap.setPixel(j, point.y, Color.RED);
        }

        currentBitmap = nextBitmap;
        updateScreenImage();
    }
}
