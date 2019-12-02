package com.example.smartbrush;

import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class ShootAndCropActivity extends AppCompatActivity implements OnClickListener {
    //keep track of camera capture intent
    final int CAMERA_CAPTURE = 1;
    final int RESULT_LOAD_IMG = 2;
    //captured picture uri
    private Uri picUri;

    //keep track of cropping intent
    final int PIC_CROP = 3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shoot_and_crop);

        //retrieve a reference to the UI button
        Button captureBtn = (Button)findViewById(R.id.capture_btn);
        //handle button clicks
        captureBtn.setOnClickListener(this);

        Button chooseBtn = (Button)findViewById(R.id.choose_btn);
        chooseBtn.setOnClickListener(this);
    }

    public void onClick(View v) {
        if (v.getId() == R.id.capture_btn) {
            try {
                //use standard intent to capture an image
                Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                //we will handle the returned data in onActivityResult
                startActivityForResult(captureIntent, CAMERA_CAPTURE);
            } catch(ActivityNotFoundException anfe){
                //display an error message
                String errorMessage = "Whoops - your device doesn't support capturing images!";
                Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
                toast.show();
            }
        }
        else if(v.getId() == R.id.choose_btn){
            try {
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, RESULT_LOAD_IMG);
            } catch(ActivityNotFoundException anfe){
                //display an error message
                String errorMessage = "Whoops - your device doesn't support choosing images!";
                Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if(requestCode == CAMERA_CAPTURE || requestCode == RESULT_LOAD_IMG){
                picUri = data.getData();
                performCrop();
            }
            else if(requestCode == PIC_CROP){
                //get the returned data
                Bundle extras = data.getExtras();
                //get the cropped bitmap
                Bitmap thePic = extras.getParcelable("data");
                //retrieve a reference to the ImageView
                ImageView picView = (ImageView)findViewById(R.id.picture);
                //display the returned cropped image
                picView.setImageBitmap(thePic);
            }
        }
    }

    private void performCrop(){
        try {
            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            //indicate image type and Uri
            cropIntent.setDataAndType(picUri, "image/*");
            //set crop properties
            cropIntent.putExtra("crop", "true");
            //indicate aspect of desired crop
            cropIntent.putExtra("aspectX", 1);
            cropIntent.putExtra("aspectY", 1);
            //indicate output X and Y
            cropIntent.putExtra("outputX", 256);
            cropIntent.putExtra("outputY", 256);
            //retrieve data on return
            cropIntent.putExtra("return-data", true);
            //start the activity - we handle returning in onActivityResult
            startActivityForResult(cropIntent, PIC_CROP);

        }
        catch(ActivityNotFoundException anfe){
            //display an error message
            String errorMessage = "Whoops - your device doesn't support the crop action!";
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }
    }
}
