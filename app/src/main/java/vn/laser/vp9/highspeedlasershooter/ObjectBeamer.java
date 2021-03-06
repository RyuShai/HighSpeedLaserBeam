package vn.laser.vp9.highspeedlasershooter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Point3;


public class ObjectBeamer{

    Mat tranMat =  new Mat(7,7, CvType.CV_64F);// {x,y,z, v_x, v_y, v_z, g}
    Mat state = new Mat(7,1, CvType.CV_64F);
    public double objectWidth;//real object width in meter
    long lastMoment = System.currentTimeMillis();

    android.graphics.Point curLaserCoordinate= new android.graphics.Point(0,0);

    public ObjectBeamer(){

    }

    /**
     *
     * @param u: horizontal coordinate of detected object in image, a.k.a x
     * @param v: vertical coordinate of detected object in image, a.k.a x
     * @param objectSizeInPixels
     */
    public void update(int u, int v, double objectSizeInPixels){
        long now = System.currentTimeMillis();
        double t = (now - lastMoment)/(double)1000;//the duration from last update

        Point3 newPos = convertImage2Location(u,v, objectSizeInPixels);
        Point3 oldPost = new Point3(state.get(0,0)[0], state.get(1,0)[0], state.get(2,0)[0]);
        Point3 speed = new Point3( (newPos.x - oldPost.x)/t, (newPos.y - oldPost.y)/t,(newPos.z - oldPost.z)/t);

        state.put(0,0, new double[]{newPos.x, newPos.y, newPos.z, speed.x, speed.y, speed. z, 9.8});
        lastMoment = now;
    }

    public void beam(UsbService usbService){
        long now = System.currentTimeMillis();
        double t = (now - lastMoment)/(double)1000;
        tranMat.put(0,0, new double[]{1,0,0,t,0,0,0,    0,1,0,0,t,0,0.5*t*t,  0,0,1,0,0,t,0,  0,0,0,1,0,0,0,  0,0,0,0,1,0,t,  0,0,0,0,0,1,0,  0,0,0,0,0,0,1});
        Mat predictedMat = UtilMatrix.multiply(tranMat, state);
        Point3 location= new Point3(predictedMat.get(0,0)[0], predictedMat.get(1,0)[0], predictedMat.get(2,0)[0]);

        double alpha =  Math.atan2(location.x, location.z  );
        double beta = Math.atan2(location.y, Math.sqrt(location.x* location.x + location.z*location.z) );

        int xStep = (int)( alpha / XmlParser.alphaStep);
        int yStep = (int)( beta / XmlParser.betaStep);
        moveLaser(new android.graphics.Point(xStep, yStep), usbService);
    }


    public void moveLaser(android.graphics.Point targetCoordinate, UsbService usbService)
    {
        targetCoordinate.x = targetCoordinate.x+XmlParser.deltaXStep;
        targetCoordinate.y = targetCoordinate.y+XmlParser.deltaYStep;
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


    private Point3 convertImage2Location(double u, double v, double pixelCount)
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
}