package vn.laser.vp9.highspeedlasershooter;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.w3c.dom.Text;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.Policy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private  final static String tag = "Ryu";

    CameraManager camMng;
    AutoFitTextureView textureView;
    ImageView imgView;
    CameraConstrainedHighSpeedCaptureSession cameraCaptureHighSpeedSession;

    String cameraId;
    CameraDevice camDevice;
    CameraCharacteristics characteristics;
    protected CaptureRequest.Builder captureRequestBuilder;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    UsbService usbService =null;
    MyHandler mHandler=null;

    Point sendCoordinate;

    int w=1280;
    int h=720;
    int threadCount=0;
    android.graphics.Point curLaserCoordinate= new android.graphics.Point(0,0);
    ObjectBeamer beamer;
    boolean startMove=false;
    //Test vairable


    int iFrameCount=0;
    int count = 0;
    int orientation;
    long lastTime=0;
    long currentTime=0;
    TextView txtView;

    //==============
    //============================================================================//
    static{
        if(OpenCVLoader.initDebug())
        {
            Log.d(tag,"opencv loadded");
        }else{
            Log.d(tag,"opencv load failed");

        }
    }
    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CheckPermission();//first check permission

        sendCoordinate = new Point(0,0);

        txtView = (TextView) findViewById(R.id.textView);
        imgView = (ImageView) findViewById(R.id.imageView);

        textureView = (AutoFitTextureView) findViewById(R.id.textureView);
//        textureView.setRotation(270.0f);
//        textureView = new AutoFitTextureView(this);

        textureView.setSurfaceTextureListener(textureListener);
        beamer = new ObjectBeamer();
        //run over time in other thread every delayTimer
        new Timer().schedule(timerTask,500,Config.delayTimer);
    }

    @Override
    protected void onResume(){
        super.onResume();
        setFilters();  // Start listening notifications from UsbService
        Log.e("Ryu", "onresume1");
        startService(UsbService.class,usbConnection,null); // Start UsbService(if it was not started before) and Bind it
        Log.e("Ryu", "onresume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mUsbReceiver);
        unbindService(usbConnection);
    }

    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
        if(usbService!=null && !usbService.isWriting)
        {
            beamer.beam(usbService);
        }
            }

    };

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e("Ryu","width: "+ width + " height "+ height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        public void processFrame(Bitmap[] params ){
            Bitmap bmp32 = params[0].copy(Bitmap.Config.ARGB_8888, true);
            Mat mat = new Mat();
            Utils.bitmapToMat(bmp32, mat);

            mat = mat.t();
            Core.flip(mat,mat,0);
            Imgproc.resize(mat, mat,new org.opencv.core.Size(mat.height(),mat.width()));

            Imgproc.cvtColor(mat,mat, Imgproc.COLOR_RGB2BGR);
            Mat drawMat = mat.clone();
            Imgproc.resize(mat, mat,new org.opencv.core.Size(mat.width()/Config.scaleSize,mat.height()/Config.scaleSize));
            int[] retBall = UtilMatrix.DetectBall(mat,Config.OBJECT_COLOR.ORANGE_BALL, Config.scaleSize);//retBall[0->2]: x,y, and radius respectively
            beamer.update(retBall[0]*3/2,retBall[1]*3/2,retBall[2]*3/2);
            //beamer.beamImmediately(retBall[0]*3/2,retBall[1]*3/2,retBall[2]*3/2, usbService);

            if(retBall[0] >0 && retBall[1]>0 && retBall[2]>0)
            Imgproc.circle(drawMat,new org.opencv.core.Point((int)retBall[0],(int)retBall[1]),(int)retBall[2],new Scalar(0,255,0),2);

            Bitmap bmp = null;
            bmp = Bitmap.createBitmap(drawMat.width(),drawMat.height(),Bitmap.Config.RGB_565);
            Utils.matToBitmap(drawMat,bmp);
            imgView.setImageBitmap(bmp);
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//            Mat mat = new Mat();
//            Bitmap[] params = {textureView.getBitmap()};
//            Bitmap bmp32 = params[0].copy(Bitmap.Config.ARGB_8888, true);
//            Utils.bitmapToMat(bmp32, mat);
//
//            mat = mat.t();
//            Core.flip(mat,mat,0);
//            Imgproc.resize(mat, mat,new org.opencv.core.Size(mat.height(),mat.width()));
//
//            Imgproc.cvtColor(mat,mat, Imgproc.COLOR_RGB2BGR);
//            Mat drawMat = mat.clone();
//            Imgproc.resize(mat, mat,new org.opencv.core.Size(mat.width()/Config.scaleSize,mat.height()/Config.scaleSize));
//            int[] retBall = UtilMatrix.DetectBall(mat,Config.OBJECT_COLOR.ORANGE_BALL, Config.scaleSize);//retBall[0->2]: x,y, and radius respectively
//            //int[] retBall = UtilMatrix.DetectBall(mat,Config.OBJECT_COLOR.ORANGE_BALL);//retBall[0->2]: x,y, and radius respectively
////            Log.e("Ryu", "X: " + retBall[0] + " Y:" + retBall[1] + " Rad" + retBall[2] );
//            beamer.update(retBall[0],retBall[1],retBall[2]);
//            //beamer.beamImmediately(retBall[0]*3/2,retBall[1]*3/2,retBall[2]*3/2, usbService);
//            if(startMove)
//            {
//                beamer.DrawRectangle(usbService);
//                startMove=false;
//            }
//
//
//            if(retBall[0] >0 && retBall[1]>0 && retBall[2]>0)
//            Imgproc.circle(drawMat,new org.opencv.core.Point((int)retBall[0],(int)retBall[1]),(int)retBall[2],new Scalar(0,255,0),2);
//
//            Bitmap bmp = null;
//            bmp = Bitmap.createBitmap(drawMat.width(),drawMat.height(),Bitmap.Config.RGB_565);
//            Utils.matToBitmap(drawMat,bmp);
//            imgView.setImageBitmap(bmp);

            if(threadCount<10)
            {
                threadCount++;
                new ImageProcess().execute(textureView.getBitmap());
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case Config.PERMISSION_EXTERNAL_STORAGE:{
                if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {

                }
                else
                {

                }
            }
            break;
            case Config.PERMISSION_CAMERA:{
                if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {

                }
                else
                {

                }
            }
            break;
        }
    }

    Camera.PreviewCallback previewCallback  = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if(iFrameCount<60)
//                Log.e(tag,"IframeCaount: "+iFrameCount + " "+System.currentTimeMillis());
            iFrameCount++;
        }
    };

    public void CheckPermission()
    {
        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE )!= PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},Config.PERMISSION_EXTERNAL_STORAGE);
        }

        if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA )!= PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA},Config.PERMISSION_CAMERA);
        }
    }

    /*
   * Notifications from UsbService will be received here.
   */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                break;
            case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                break;
            case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                break;
            case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                break;
            case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                break;
        }
        }
    };


    void openCamera()
    {
        Log.e("Ryu", "opencamera");
        textureView.setAspectRatio(w,h);
        configureTransform(w,h);
        camMng =(CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{
            cameraId = camMng.getCameraIdList()[0];

            characteristics = camMng.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Log.e("Ryu","oriend: "+ orientation);
//            Size[] sizes =configs.getOutputSizes(SurfaceTexture.class);
//            for (Size size :
//                    sizes) {
//                Log.e("Ryu","Size: "+ size.toString());
//            }
            
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            camMng.openCamera(cameraId,deviceStateCallback,null);
        }catch (Exception ex)
        {
        }
    }

    CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {

            camDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camDevice.close();
            camDevice = null;
        }
    };

    void createCameraPreview()
    {
        try{
            SurfaceTexture texture =textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(w,h);

            captureRequestBuilder = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,orientation);
            Surface previewSurface = new Surface(texture);
            captureRequestBuilder.addTarget(previewSurface);
            Log.e("Ryu", "createCameraPreview");
            camDevice.createConstrainedHighSpeedCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    cameraCaptureHighSpeedSession = (CameraConstrainedHighSpeedCaptureSession) session;
                    Log.e("Ryu", "onConfigur");
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },null);

        }catch (Exception ex)
        {

        }
    }

    void updatePreview()
    {
        if (null == camDevice) {
            return;
        }
        try {
            Log.e("Ryu", "updatePreview");
            setUpCaptureRequestBuilder(captureRequestBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            List<CaptureRequest> capList = cameraCaptureHighSpeedSession.createHighSpeedRequestList(captureRequestBuilder.build());
            //final long tic = System.currentTimeMillis();
            cameraCaptureHighSpeedSession.setRepeatingBurst(capList, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //long toc = System. currentTimeMillis();
                    //float fps = (toc-tic)/result.getFrameNumber();
//                    Log.i("Completed", "fps:" + result.getFrameNumber());
                    //120shutter/s
                    if(!startMove)
                    {
                        Log.e("Ryu","capturecomplete");
                        beamer.DrawRectangle(usbService);
                        startMove=true;
                    }

                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {

//        builder.set(CaptureRequest.JPEG_ORIENTATION, 0 );

        builder.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO );
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(120, 120));
    }

    void convert2Mat(Bitmap bmp)
    {

        Mat mat = new Mat();
        Bitmap bmp32 = bmp.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, mat);
//        currentTime = System.currentTimeMillis();
//        Log.e(tag,"Count: "+count + " " +(currentTime-lastTime));
//        lastTime = currentTime;
//        count++;
    }


    class ImageProcess extends AsyncTask<Bitmap,Void,Void>
    {
        Mat mat = new Mat();
        Bitmap bmp = null;
        @Override
        protected Void doInBackground(Bitmap... params) {
            Bitmap bmp32 = params[0].copy(Bitmap.Config.ARGB_8888, true);
            Utils.bitmapToMat(bmp32, mat);
            mat = mat.t();
            Core.flip(mat,mat,0);
            Imgproc.resize(mat, mat,new org.opencv.core.Size(mat.height(),mat.width()));

            Imgproc.cvtColor(mat,mat, Imgproc.COLOR_RGB2BGR);
            Imgproc.resize(mat, mat,new org.opencv.core.Size(mat.width()/Config.scaleSize,mat.height()/Config.scaleSize));
            int[] retBall = UtilMatrix.DetectBall(mat,Config.OBJECT_COLOR.ORANGE_BALL);
            beamer.update(retBall[0]*3/2,retBall[1]*3/2,retBall[2]*3/2);
//            if(startMove)
//            {
//                beamer.DrawRectangle(usbService);
//                startMove=false;
//            }


            if(retBall[0] >0 && retBall[1]>0 && retBall[2]>0)
                Imgproc.circle(mat,new org.opencv.core.Point((int)retBall[0],(int)retBall[1]),(int)retBall[2],new Scalar(0,255,0),2);

            bmp = Bitmap.createBitmap(mat.width(),mat.height(),Bitmap.Config.RGB_565);
            Utils.matToBitmap(mat,bmp);
//            Log.e("Ryu","bitMap: "+ bmp.getWidth() + " "+bmp.getHeight());
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
//            Log.e("Ryu", "den day la ok");
            imgView.setImageBitmap(bmp);
            threadCount--;
            iFrameCount++;
            Log.e("Ryu","current "+(iFrameCount*1000/(System.currentTimeMillis()-lastTime)));
            bmp=null;
            mat=null;
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = this;
        if (null == textureView || null == textureView || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, textureView.getHeight(), textureView.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / textureView.getHeight(),
                    (float) viewWidth / textureView.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    /*
    * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
    */
    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UsbService.MESSAGE_FROM_SERIAL_PORT:
//                    String data = (String) msg.obj;
//                    mActivity.get().display.append(data);
                    break;
                case UsbService.CTS_CHANGE:
                    Toast.makeText(mActivity.get(), "CTS_CHANGE",Toast.LENGTH_LONG).show();
                    break;
                case UsbService.DSR_CHANGE:
                    Toast.makeText(mActivity.get(), "DSR_CHANGE",Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
    //create service
    private void startService(Class<?> service, ServiceConnection serviceConnection, Bundle extras) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            if (extras != null && !extras.isEmpty()) {
                Set<String> keys = extras.keySet();
                for (String key : keys) {
                    String extra = extras.getString(key);
                    startService.putExtra(key, extra);
                }
            }
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    void moveLaser(android.graphics.Point targetCoordinate)
    {
//        targetCoordinate.x = targetCoordinate.x+XmlParser.deltaXStep;
//        targetCoordinate.y = targetCoordinate.y+XmlParser.deltaYStep;
        if(targetCoordinate.x<0 || targetCoordinate.x>4000 ||targetCoordinate.y<0 || targetCoordinate.y>4000)
        {
        }
        if(!curLaserCoordinate.equals(targetCoordinate))
        {
            if (targetCoordinate.x < 0) targetCoordinate.x = 0;
            if (targetCoordinate.x > 4000) targetCoordinate.x= 4000;
            if (targetCoordinate.y < 0) targetCoordinate.y = 0;
            if (targetCoordinate.y > 4000) targetCoordinate.y = 4000;

            final String data = "X" + String.valueOf(targetCoordinate.x) + " Y" + String.valueOf(targetCoordinate.y) + " ";
            usbService.write(data.getBytes());
            curLaserCoordinate = targetCoordinate;
        }
    }
}
