package mendona.imagewarper;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
}
