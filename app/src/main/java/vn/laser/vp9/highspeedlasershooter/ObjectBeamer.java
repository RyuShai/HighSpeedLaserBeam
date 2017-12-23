package vn.laser.vp9.highspeedlasershooter;

import android.os.Environment;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Point3;

import java.util.Random;

import static vn.laser.vp9.highspeedlasershooter.Config.moveStep;


public class ObjectBeamer{

    Mat tranMat =  new Mat(7,7, CvType.CV_64F);// {x,y,z, v_x, v_y, v_z, g}
    Mat state = new Mat(7,1, CvType.CV_64F);
    public double objectWidth ;//real object width in meter
    long lastMoment ;
    public long totalLatency = 250;

    android.graphics.Point curLaserCoordinate= new android.graphics.Point(0,0);

    public ObjectBeamer(){
        objectWidth = 0.020;
        lastMoment = System.currentTimeMillis();
        XmlParser.readDataFromFile(Environment.getExternalStorageDirectory()+"/cam2laserMatrices.xml");
        sendCoordinate = new android.graphics.Point(0,0);
    }

    /**
     *
     * @param u: horizontal coordinate of detected object in image, a.k.a x
     * @param v: vertical coordinate of detected object in image, a.k.a x
     * @param objectSizeInPixels
     */
    public void update(int u, int v, int objectSizeInPixels){
        long now = System.currentTimeMillis();
        double t = (now - lastMoment )/(double)1000;//the duration from last update
        Point3 newPos = convertImage2Location(u,v, objectSizeInPixels);
        Point3 oldPost = new Point3(state.get(0,0)[0], state.get(1,0)[0], state.get(2,0)[0]);
        Point3 speed = new Point3( (newPos.x - oldPost.x)/t, (newPos.y - oldPost.y)/t,(newPos.z - oldPost.z)/t);

        state.put(0,0, new double[]{newPos.x, newPos.y, newPos.z, speed.x, speed.y, speed. z, 9.8});
        lastMoment = now;

        Log.e("beamer","updated coordinate" + newPos.x + " " + newPos.y + " " + newPos.z + " Spped " + speed.x + " " + speed.y + " " + speed.z );
    }

    public void beam(UsbService usbService){
        long now = System.currentTimeMillis();
        double t = (now - lastMoment + totalLatency)/(double)1000;
        Log.e("beamer", "Time:" + now + " - " + lastMoment + " + " + totalLatency);
        double lastSpeedY = state.get(4,0)[0];
        double gtt = 0.5*t*t;
        if(lastSpeedY<0.01) gtt = 0;
        tranMat.put(0,0, new double[]{1,0,0,t,0,0,0,
                                                0,1,0,0,t,0,gtt,
                                                0,0,1,0,0,t,0,
                                                0,0,0,1,0,0,0,
                                                0,0,0,0,1,0,t,
                                                0,0,0,0,0,1,0,
                                                0,0,0,0,0,0,1});
        Mat predictedMat = UtilMatrix.multiply(tranMat, state);
        Point3 location= new Point3(predictedMat.get(0,0)[0], predictedMat.get(1,0)[0], predictedMat.get(2,0)[0]);
        Point3 speed= new Point3(predictedMat.get(3,0)[0], predictedMat.get(4,0)[0], predictedMat.get(5,0)[0]);

        double alpha =  Math.atan2(location.x, location.z  );
        double beta = Math.atan2(location.y, Math.sqrt(location.x* location.x + location.z*location.z) );

        int xStep = (int)( alpha / XmlParser.alphaStep);
        int yStep = (int)( beta / XmlParser.betaStep);
        moveLaser(new android.graphics.Point(xStep, yStep), usbService);

        Log.e("beamer",  "beaming coordinate" + location.x + " " + location.y + " " + location.z+ " Spped " + speed.x + " " + speed.y + " " + speed.z );
        Log.e("beamer",  "Laser steps: " + xStep + " " + xStep + " t:" + t);
        Log.e("beamer",  "____________________________________________");
    }

    /**
     * This function allows to beam immediatly once the object is detected
     * (u,v) are detected image coordinate,
     */
    public void beamImmediately(int u, int v, int objectSizeInPixels, UsbService usbService){
        Point3 location = convertImage2Location(u,v, objectSizeInPixels);
        double alpha =  Math.atan2(location.x, location.z  );
        double beta = Math.atan2(location.y, Math.sqrt(location.x* location.x + location.z*location.z) );

        int xStep = (int)( alpha / XmlParser.alphaStep);
        int yStep = (int)( beta / XmlParser.betaStep);
        moveLaser(new android.graphics.Point(xStep, yStep), usbService);
    }

    public void moveLaser(android.graphics.Point targetCoordinate, UsbService usbService)
    {
        android.graphics.Point tunnedPoint = targetCoordinate;

        tunnedPoint.x = tunnedPoint.x+XmlParser.deltaXStep;
        tunnedPoint.y = tunnedPoint.y+XmlParser.deltaYStep;
        if(!curLaserCoordinate.equals(tunnedPoint))
        {
            if (tunnedPoint.x < 0) tunnedPoint.x = 0;
            if (tunnedPoint.x > 4000) tunnedPoint.x= 4000;
            if (tunnedPoint.y < 0) tunnedPoint.y = 0;
            if (tunnedPoint.y > 4000) tunnedPoint.y = 4000;

            final String data = "X" + String.valueOf(tunnedPoint.x) + " Y" + String.valueOf(tunnedPoint.y) + " ";
            Log.e(Config.tag,data);
            usbService.write(data.getBytes());
            curLaserCoordinate = tunnedPoint;
        }
    }

    private Point3 convertImage2Location(int u, int v, int pixelCount)
    {
        //update location here
        Mat midleOfRightEdge = new Mat(3,1, CvType.CV_64F);
        midleOfRightEdge.put(0,0, new double[]{objectWidth, 0, 0});//P_midleEdge - P_center

        Mat pMultipledByLambda = UtilMatrix.multiply(XmlParser.camera_intrinsics, midleOfRightEdge);//pMultipledByLambda = K(P_midleEdge - P_center)
        double lambda = pMultipledByLambda.get(0,0)[0] / pixelCount;

        Mat point2d_vec = new Mat(3, 1, CvType.CV_64F);
        point2d_vec.put(0, 0, new double[]{u * lambda, v * lambda, lambda});

        // Center face in camera coordinates
        Mat X_c = UtilMatrix.multiply(XmlParser.camera_intrinsics.inv(), point2d_vec);
        Mat rMulXC = UtilMatrix.multiply(XmlParser.rRodC2L, X_c);
        Mat mPointInLaser = new Mat();
        Core.add(rMulXC, XmlParser.tC2L, mPointInLaser);
        Point3 location= new Point3(mPointInLaser.get(0,0)[0],
                mPointInLaser.get(1,0)[0],
                mPointInLaser.get(2,0)[0]);
        return location;
    }

    private int edge = 1;
    android.graphics.Point sendCoordinate ;
    public void DrawRectangle(UsbService usbService)
    {
        Log.e("Ryu","movelaser");
        if(edge == 1){
            sendCoordinate.y = 0;
            if(sendCoordinate.x < Config.MAX){
                sendCoordinate.x += Config.moveStep;
            }
            else
                edge = 2;
        }
        else if(edge == 2){
            sendCoordinate.x = Config.MAX;
            if(sendCoordinate.y < Config.MAX){
                sendCoordinate.y += Config.moveStep;
            }
            else edge = 3;
        }else if(edge ==3) {
            sendCoordinate.y = Config.MAX;
            if(sendCoordinate.x > 0){
                sendCoordinate.x -= Config.moveStep;
            }else
                edge = 4;
        }else{
            sendCoordinate.x = 0;
            if(sendCoordinate.y > 0){
                sendCoordinate.y -= Config.moveStep;
            }else
                edge = 1;
        }

        Log.e("Ha","movelaser to" + sendCoordinate.x + " " + sendCoordinate.y);
        moveLaser(sendCoordinate,usbService);
    }

    public void DrawDiagonalLine(UsbService usbService){
        Random r = new Random();
        int i1 = r.nextInt(Config.MAX + 1);
        moveLaser(new android.graphics.Point(i1, i1), usbService);
    }
}