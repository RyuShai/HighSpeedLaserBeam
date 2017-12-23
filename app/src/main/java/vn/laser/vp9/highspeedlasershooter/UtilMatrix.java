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
    static ArrayList<Integer> recentRadii = new ArrayList<Integer>();//for average radius computation

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
     * @param objColor
     */
    static int[] DetectBall(Mat rgbInput , Config.OBJECT_COLOR objColor)
    {
        Mat processMat = rgbInput.clone();
        Imgproc.GaussianBlur(processMat,processMat,new Size(5,5),3.0,3.0);

        Imgproc.cvtColor(processMat,processMat,Imgproc.COLOR_BGR2HSV);
        Mat rangeRes = Mat.zeros(rgbInput.size(), CvType.CV_8UC1);
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Mat hirecy = new Mat();
        switch (objColor)
        {
            case GREEN_BALL:
            {
                Scalar hsv_l = new Scalar(30, 50, 87);
                Scalar hsv_h = new Scalar(68, 255, 255);
                Core.inRange(processMat,hsv_l,hsv_h,rangeRes);

                Imgproc.erode(rangeRes,rangeRes,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5)));
                Imgproc.dilate( rangeRes, rangeRes, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5, 5)) );

                Imgproc.dilate( rangeRes, rangeRes, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5, 5)) );
                Imgproc.erode(rangeRes,rangeRes,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5)));

                Imgproc.findContours(rangeRes,contours,hirecy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
                Log.e("Detector", "contour size: "+contours.size());
                break;
            }
            case ORANGE_BALL:
            {
                Scalar hsv_l = new Scalar(16, 80, 87);
                Scalar hsv_h = new Scalar(24, 255, 255);
                Core.inRange(processMat,hsv_l,hsv_h,rangeRes);

                Imgproc.erode(rangeRes,rangeRes,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5)));
                Imgproc.dilate( rangeRes, rangeRes, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5, 5)) );

                Imgproc.dilate( rangeRes, rangeRes, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5, 5)) );
                Imgproc.erode(rangeRes,rangeRes,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5)));

                Imgproc.findContours(rangeRes,contours,hirecy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
                Log.e("Detector", "contour size: "+contours.size());
                break;
            }
        }
        Log.e("Detector", "Contour size: "+contours.size());

        int[] retCircle = new int[]{-1,-1,-1};
        int minColorDistance = 180;
        for(int i = 0; i<contours.size();i++)
        {
            Rect bBox = Imgproc.boundingRect(contours.get(i));
            float ratio = (float) bBox.width/bBox.height;
            if(ratio > 1.0f) ratio = 1.0f/ratio;
            Log.e("Detector", "ratio: "+ ratio + " "+bBox.area()) ;
            //searching for bBox almost square
            if(ratio>0.63 /*&& bBox.area()>=200*/)
            {
                if(processMat.empty() || processMat == null){
                   Log.e("Detector", "hue is empty");
                    return new int[]{-1,-1,-1};
                }
                double[] pixelValue = processMat.get(bBox.y+bBox.height/2,bBox.x+bBox.width/2);
                int hueAtPoint=190;
                if(pixelValue!=null)
                {
                     hueAtPoint =(int) pixelValue[0];
                }else
                    Log.e("Detector", "Cannot read color at this pixel");

                int hueStandard = 0;
                switch (objColor){
                    case GREEN_BALL:
                        hueStandard = 65;
                        break;
                    case ORANGE_BALL:
                        hueStandard = 20;
                        break;
                }

                int colorDistance = Math.abs(hueAtPoint-hueStandard);
                Log.e("Detector", "colorDistance:" + colorDistance);
                if(colorDistance<minColorDistance) {
                    retCircle = new int[]{bBox.x + bBox.width / 2, bBox.y + bBox.height / 2, bBox.width / 2};
                }
            }
        }

        //compute the average radius to avoid fluctuating radius
        recentRadii.add(retCircle[2]);
        if(recentRadii.size() > 10) recentRadii.remove(0);

        int total = 0, totalMulti= 0;
        for(int i=0, multi = 11;i< recentRadii.size();i++, multi--) {
            total += recentRadii.get(i)*multi;
            totalMulti += multi;
        }
        retCircle[2] = total/totalMulti;

        Log.e("Detector", "RetCircle X: " + retCircle[0] + " Y:" + retCircle[1] + " Rad" + retCircle[2] + "radii size" + recentRadii.size() );
        return  retCircle;
    }

    static int[] DetectBall(Mat rgbInput , Config.OBJECT_COLOR objColor,int scale){
        int retCircle[] = DetectBall(rgbInput, objColor);
        retCircle[0] = retCircle[0]*scale;
        retCircle[1] = retCircle[1]*scale;
        retCircle[2] = retCircle[2]*scale;
        return retCircle;
    }



    public static Mat multiply(Mat a, Mat b) {

        Mat ret = new Mat();
        Core.gemm(a, b, 1, new Mat(), 0, ret);

        return ret;
    }

}
