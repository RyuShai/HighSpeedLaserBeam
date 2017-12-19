package vn.laser.vp9.highspeedlasershooter;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static vn.laser.vp9.highspeedlasershooter.Config.moveStep;
import static vn.laser.vp9.highspeedlasershooter.Config.tag;

/**
 * Created by ryu on 12/12/17.
 */

public class UtilMatrix {
    static Point currentLaserCoordinate=null;
    static int state = 0;

    static Mat convertBitmap2Mat(Bitmap bmp)
    {
        Mat mat = new Mat();
        Bitmap bmp32 = bmp.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, mat);
        Log.e(tag,"ConvertTime: " +System.currentTimeMillis());
        return mat;
    }

    static void MoveLaser(Point targetCoordinate, UsbService usbService)
    {
        if(currentLaserCoordinate== null || !currentLaserCoordinate.equals(usbService))
        {
            if (targetCoordinate.x < 0) targetCoordinate.x = 0;
            if (targetCoordinate.x > 4000) targetCoordinate.x= 4000;
            if (targetCoordinate.y < 0) targetCoordinate.y = 0;
            if (targetCoordinate.y > 4000) targetCoordinate.y = 4000;

            final String data = "X" + String.valueOf(targetCoordinate.x) + " Y" + String.valueOf(targetCoordinate.y) + " ";
            usbService.write(data.getBytes());
            currentLaserCoordinate = targetCoordinate;
        }
    }

    /**
     *
     * @param rgbInput scaled - BGR format
     * @param objColor
     */
    static float[]  DetectBall(Mat rgbInput , Config.OBJECT_COLOR objColor)
    {
        Mat processMat = rgbInput.clone();
        Imgproc.GaussianBlur(processMat,processMat,new Size(5,5),3.0,3.0);

        Imgproc.cvtColor(processMat,processMat,Imgproc.COLOR_BGR2HSV);
        // <<<<< Noise smoothing         // >>>>> HSV conversion
        List<Mat> hsvPlanes = new ArrayList<Mat>(3);
        Core.split(rgbInput, hsvPlanes);
        Log.e("Ryu", "Split: "+hsvPlanes.size());
        Mat hue = hsvPlanes.get(0);
        Log.e("Ryu", "hue: "+ hue.width()+ " "+ hue.height());

        // Note: change parameters for different colors
        Mat rangeRes = Mat.zeros(rgbInput.size(), CvType.CV_8UC1);
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hirecy = new Mat();
        switch (objColor)
        {
            case ORANGE_BALL:
            {
                Scalar hsv_l = new Scalar(16, 70, 87);
                Scalar hsv_h = new Scalar(24, 255, 255);
                Core.inRange(processMat,hsv_l,hsv_h,rangeRes);

                Imgproc.erode(rangeRes,rangeRes,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5)));
                Imgproc.dilate( rangeRes, rangeRes, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5, 5)) );

                Imgproc.dilate( rangeRes, rangeRes, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5, 5)) );
                Imgproc.erode(rangeRes,rangeRes,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5)));

                Imgproc.findContours(rangeRes,contours,hirecy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
                Log.e("Ryu", "contour: "+contours.size());
                break;
            }
        }

        float[] retCircle = new float[]{-1,-1,-1};
        int minColorDistance = 180;
        for(int i = 0; i<contours.size();i++)
        {
            Rect bBox = Imgproc.boundingRect(contours.get(i));
            float ratio = (float) bBox.width/bBox.height;
            if(ratio > 1.0f) ratio = 1.0f/ratio;
            Log.e("Ryu", "ratio: "+ ratio + " "+bBox.area()) ;
            //searching for bBox almost square
            if(ratio>0.4f && bBox.area()>=400)
            {
                if(processMat.empty() || processMat == null){
                    Log.e("Ryu", "hue is empty");
                    return new float[]{-1,-1,-1};
                }
                Log.e("Ryu","bOX: "+ bBox.x + " "+bBox.y + " " + bBox.width + " "+ bBox.height + " "+(bBox.x+bBox.width/2)  + " "+(bBox.y+ bBox.height/2));
                Log.e("Ryu"," process: " + processMat.width() + " "+ processMat.height());
                double[] pixelValue = processMat.get(bBox.y+bBox.height/2,bBox.x+bBox.width/2);
                int hueAtPoint=190;
                if(pixelValue!=null)
                {
                    Log.e("Ryu","hueAtPoint size: "+ pixelValue.length);
                     hueAtPoint =(int) pixelValue[0];
                }
                else Log.e("Ryu"," is null fuck");

                int colorDistance = Math.abs(hueAtPoint-20);
                if(colorDistance<minColorDistance)
                    retCircle = new float[]{bBox.x+bBox.width/2, bBox.y+bBox.height/2, bBox.width/2};
            }
        }

        return  retCircle;
    }

    public static void DrawRectangle(UsbService usbService, Point sendCoordinate)
    {
        if(state == 1){
            if(sendCoordinate.x < Config.MAX){
                sendCoordinate.x += Config.moveStep;
               MoveLaser(sendCoordinate,usbService);
            }
            else if(sendCoordinate.y < Config.MAX){
                sendCoordinate.x = Config.MAX;
                sendCoordinate.y += Config.moveStep;
                MoveLaser(sendCoordinate,usbService);
            }
            else state = 0;
        }else{

            if(sendCoordinate.y == Config.MAX && sendCoordinate.x > 0){
                sendCoordinate.x -= Config.moveStep;
                sendCoordinate.y = Config.MAX;
                MoveLaser(sendCoordinate,usbService);
            }
            else if(sendCoordinate.x == 0 && sendCoordinate.y > 0){
                sendCoordinate.y -= moveStep;
                MoveLaser(sendCoordinate,usbService);
            }
            else  state = 1;

        }
    }

    public static Mat multiply(Mat a, Mat b) {
        Mat ret = new Mat();
        Core.gemm(a, b, 1, new Mat(), 0, ret);
        return ret;
    }
}
