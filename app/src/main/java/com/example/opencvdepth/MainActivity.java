package com.example.opencvdepth;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.ToneGenerator;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.opencvdepth.R;
import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;

import org.theta4j.osc.CommandResponse;
import org.theta4j.osc.CommandState;
import org.theta4j.webapi.TakePicture;
import org.theta4j.webapi.Theta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.log.MyLog;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;

//public class MainActivity extends AppCompatActivity {
//with a real camera class MainActivity extends PluginActivity
public class MainActivity extends PluginActivity {

    @Override
    protected void onResume() {
        super.onResume();
        setKeyCallback(keyCallback);
        MyLog.Log("set key callback");
        if (isApConnected()) {
            MyLog.Log("onResume Ap is Connected ");
        } else {
            MyLog.Log("onResume Ap is NOT Connected ");
        }
        MyLog.Log("OpenCV version is: "+version());
    }

    // load native library
    static {
        System.loadLibrary("opencvdepth");
    }

    ImageView thetaImageView;
    TextView statusTextView;
    String extStorageDirectory = Environment.getExternalStorageDirectory().toString();
    String basepath = extStorageDirectory + "/DCIM/100RICOH/";
    private ExecutorService thetaExecutor = Executors.newSingleThreadExecutor();
    URL inputFileUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // graphics is visible only to developer through e.g. Vysor application
        thetaImageView = findViewById(R.id.thetaImageId);
        thetaImageView.setImageResource(R.drawable.theta);

        // if external storage access shows not yet granted in log, use Vysor to toggle it on
        checkPermission();

        // pictures storage location seems hardcoded in THETHA V.
        File thetaMediaDir = new File(basepath);
        if (!thetaMediaDir.exists()) {
            thetaMediaDir.mkdirs();
        }
    }

    // Declaration of native functions for calculating depth map (disparity map between two stereo images)
    public native String version();
    public native byte[] disparity(int width, int height, byte[] srcL, byte[] srcR);
    public native byte[] rgba2gray(int width, int height, byte[] array);
    public native byte[] gray2rgba(int width, int height, byte[] array);

    /**
         This is the main routine of this plugin that calculate the depth map of a scene from a pair
         of stereo images of the same scene:
         1) the latest two images on ThetaV camera are taken as the stereo pair
         2) they are converted to monochrome (gray) for disparity calculation
         3) disparity (depth map) is calculated
         4) the resulting depth map is stored in ThetaV cam, under a name pattern of
            Rxxxxxxx_depth.JPG, e.g. R0010053_depth.JPG
         5) the monochrome depth map picture can be retrieved through the USB connection to the cam,
            together with the original pair of images.
            Each pixel value is depth map, in the range of 0-255, corresponds to the distance between
            the camera and the same pixel on the actual object in the original image pair.
     */
    public void processImage(String thetaPicturePathL, String thetaPicturePathR) {
        if (thetaPicturePathL == null || thetaPicturePathL.isEmpty()) {
            MyLog.Log("processImage() left-view photo is missing ! ");
            return;
        }
        if (thetaPicturePathR == null || thetaPicturePathR.isEmpty()) {
            MyLog.Log("processImage() right-view photo is missing ! ");
            return;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;   // down-sampling of picture for faster processing
        //options.inSampleSize = 2; // better precision but the longest processing time
        //options.inSampleSize = 1; // actual image size does not work: processing time > cam sleep timer
        byte[] byteBufferL, byteBufferR;
        int img_w,img_h;
        {
            Bitmap imgL = BitmapFactory.decodeFile(thetaPicturePathL, options);
            String cfg="unknown";
            switch (imgL.getConfig()) {
                case ALPHA_8:   cfg = "ALPHA_8"; break;
                case ARGB_4444: cfg = "ARGB_4444"; break;
                case ARGB_8888: cfg = "ARGB_8888"; break; // this is the logged configuration
                case RGB_565:   cfg = "RGB_565"; break;
            }
            img_w = imgL.getWidth();
            img_h = imgL.getHeight();
            MyLog.Log("L img: ByteCnt "+imgL.getByteCount()+
                    ",W "+img_w+",H "+img_h+ ",RowBytes "+imgL.getRowBytes()+
                    ",hasAlpha "+imgL.hasAlpha()+ ",config "+cfg);
            // get the byte array from the Bitmap instance
            ByteBuffer byteBuffer = ByteBuffer.allocate(imgL.getByteCount());
            imgL.copyPixelsToBuffer(byteBuffer);
            // reduce to grey 8-bit channel calling openCV
            byteBufferL = rgba2gray(img_w, img_h, byteBuffer.array());
            MyLog.Log("byteBufferL.size = "+byteBufferL.length);
            // free up camera memory in order to process the other image of the pair
            imgL.recycle();
            imgL = null;
            byteBuffer = null;
            System.gc();
        }
        {
            Bitmap imgR = BitmapFactory.decodeFile(thetaPicturePathR, options);
            // get the byte array from the Bitmap instance
            ByteBuffer byteBuffer = ByteBuffer.allocate(imgR.getByteCount());
            imgR.copyPixelsToBuffer(byteBuffer);
            // reduce to grey 8-bit channel calling openCV
            byteBufferR = rgba2gray(img_w, img_h, byteBuffer.array());
            imgR.recycle();
            imgR = null;
            byteBuffer = null;
            System.gc();
        }
        // compute the disparity map calling the native library
        byte[] disparityGray = disparity(img_w, img_h, byteBufferL, byteBufferR);
        MyLog.Log("dstGray.length = "+disparityGray.length);

        // convert 1-channel disparity map (grayscale image) to RBGA in order to work with normal viewer
        byte[] dst = gray2rgba(img_w, img_h, disparityGray);
        disparityGray = null; // force memory release
        System.gc();

        // display the output image on camera's virtual screen visible in Vysor only
        Bitmap bmp = Bitmap.createBitmap(img_w, img_h, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(dst));
        thetaImageView.setImageBitmap(bmp);

        // save the depth-map image in camera storage to be retrieved like normal picture
        // The distinction with a normal picture is in its file name (Rxxxxxxx_depth.JPG)
        try {
            saveBmp(bmp, getDepthImagePath(thetaPicturePathR));
            MyLog.Log("processImage() depth-map saved as picture at : "+getDepthImagePath(thetaPicturePathR));
        } catch (Throwable e) {
            MyLog.Log("processImage() exception calling saveBmp: "+e.toString());
        }
    }
    private String getDepthImagePath(String filePath) {
        int lastIndex = filePath.lastIndexOf('.');
        return filePath.substring(0,lastIndex)+"_depth."+filePath.substring(lastIndex+1);
    }
    private void saveBmp(Bitmap bmp, String outName) throws IOException {
        OutputStream out = new FileOutputStream(outName);
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out);
        //bmp.compress(Bitmap.CompressFormat.PNG, 95, out);
        out.flush();
        out.close();
        out = null;

        // initiate media scan and put the new things into the path array to
        // make the scanner aware of the location and the files you want to see
        MediaScannerConnection.scanFile(this, new String[] {outName.toString()}, null, null);
    }

    /**
     * User interface
     * - shuter button to take a picture as usual ..
     *   .. but the shooting is delay by a fix time about 10s, signaled in camera beeps,
     *   so the operator has time to hide himself and to leave a clean scene in picture.
     * - wifi-on-off button is deviated to start the post-processing to calculate a depth map
     *   on the lastest two pictures (supposed to be a stereo-pair of images of the same scene)
     */
    public final int DELAY_BEFORE_SHOOT = 6; // unit second

    private KeyCallback keyCallback = new KeyCallback() {
        Theta theta = Theta.createForPlugin();
        @Override
        public void onKeyDown(int keyCode, KeyEvent keyEvent) {
            if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                MyLog.Log("onKeyDown keyCode=KeyReceiver.KEYCODE_CAMERA / "+keyCode);
                delay(DELAY_BEFORE_SHOOT);
                thetaExecutor.submit(() -> {
                    CommandResponse<TakePicture.Result> response = null;
                    try {
                        response = theta.takePicture();
                    } catch (IOException e) {
                        e.printStackTrace();
                        MyLog.Log("theta.takePicture exception:"+e.toString());
                    }
                    while (response.getState() != CommandState.DONE) {
                        try {
                            response = theta.commandStatus(response);
                            Thread.sleep(100);
                        } catch (IOException e) {
                            e.printStackTrace();
                            MyLog.Log("theta.commandStatus exception:"+e.toString());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            MyLog.Log("theta.commandStatus exception:"+e.toString());
                        }
                    }
                    inputFileUrl = response.getResult().getFileUrl();
                    MyLog.Log("KeyCallback() picture taken at URL: "+inputFileUrl);
                });
            } else {
                MyLog.Log("onKeyDown() is not using keyCode="+keyCode);
            }
        }

        // On the RICOH THETA V, there is no function button.
        // Pressing WiFi button on the side of the camera will start process images for depth
        @Override
        public void onKeyUp(int keyCode, KeyEvent keyEvent) {
            if (keyCode == KeyReceiver.KEYCODE_WLAN_ON_OFF) {
                String[] latest = getLatestTwoPict();
                MyLog.Log("KEYCODE_WLAN_ON_OFF: processImage("+latest[0]+","+latest[1]+")");
                notificationLedBlink(LedTarget.LED7, LedColor.WHITE, 300);
                processImage(latest[0], latest[1]);
                notificationLedHide(LedTarget.LED7);
            }
        }
        @Override
        public void onKeyLongPress(int keyCode, KeyEvent keyEvent) {
        }
    };

    // stall the current thread for the specified duration in seconds
    private void delay(int sec) {
        ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        // 1 tone per second ..
        for (int i=0;i<sec-2;i++) {
            tone.startTone(ToneGenerator.TONE_DTMF_S, 500);
            try {
                Thread.sleep(1000);
            } catch (Throwable e) {}
        }
        // except during the last 2 seconds there are 2 tones per second
        for (int i=0;i<10;i++) { // 2 sec = 10 x 200ms
            tone.startTone(ToneGenerator.TONE_DTMF_S, 100);
            try {
                Thread.sleep(200);
            } catch (Throwable e) {}
        }
    }
    // Incidentally, the last two files in storage are also the latest two files in time.
    private String[] getLatestTwoPict() {
        String path = Environment.getExternalStorageDirectory().toString()+"/DCIM/100RICOH/";
        MyLog.Log("Path: " + path);
        File directory = new File(path);
        File[] files = directory.listFiles();
        MyLog.Log("Size: "+ files.length);
        if (files.length<2) {
            return new String[] {"one left-view photo is required", "one right-view photo is required"};
        } else {
            return new String[]{
                    path + files[files.length - 2].getName(),
                    path + files[files.length - 1].getName()};
        }
    }

    // WRITE_EXTERNAL_STORAGE permission is required to save image on device.
    // This routine will log a message about whether the plugin has that permission.
    public void checkPermission() {
        statusTextView = findViewById(R.id.statusViewId);
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED)) {
            statusTextView.setText("Ready");
            Toast.makeText(this, "storage permission  is granted", Toast.LENGTH_SHORT).show();
            MyLog.Log("storage permission is granted");
        } else {
            Toast.makeText(this, "WARNING: Need to enable storage permission",
                    Toast.LENGTH_LONG).show();
            MyLog.Log("WARNING: You Need to enable storage permission");
            statusTextView.setText("Check Permissions");
        }
    }

}