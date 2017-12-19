package vn.laser.vp9.highspeedlasershooter;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Point3;

/**
 * Created by ryu on 12/11/17.
 */


public class ObjectState {
    public Point3 location;
    public Point3 speed;
    public long timeStamp;
    public double objectWidth;

    public ObjectState()
    {
        location = new Point3();
        speed = new Point3();
        timeStamp = 0;
//        objectWidth = 0.069;
        objectWidth = 0.037;//bong ban
//        objectWidth = 0.062;
    }

    public android.graphics.Point convertLocation2LaserStep()
    {
        double alpha =  Math.atan2(location.x, location.z  );
        double beta = Math.atan2(location.y, Math.sqrt(location.x* location.x + location.z*location.z) );
        return new android.graphics.Point((int)(alpha/XmlParser.alphaStep),(int)(beta/XmlParser.betaStep));
    }

    public void convertImage2Location(Point target, double objectSize)
    {
        //update location here
        Mat midleOfRightEdge = new Mat(3,1, CvType.CV_64F);
        midleOfRightEdge.put(0,0, new double[]{objectWidth, 0, 0});//P_midleEdge - P_center

        Mat pMultipledByLambda = multiply(XmlParser.camera_intrinsics, midleOfRightEdge);//pMultipledByLambda = K(P_midleEdge - P_center)
        double lambda = pMultipledByLambda.get(0,0)[0] / objectSize;

        Mat point2d_vec = new Mat(3, 1, CvType.CV_64F);
        point2d_vec.put(0, 0, new double[]{target.x * lambda, target.y * lambda, lambda});

        // Center face in camera coordinates
        Mat X_c = multiply(XmlParser.camera_intrinsics.inv(), point2d_vec);

        Mat rMulXC = multiply(XmlParser.rRodC2L, X_c);
        Mat mPointInLaser = new Mat();
        Core.add(rMulXC, XmlParser.tC2L, mPointInLaser);
        location= new Point3(mPointInLaser.get(0,0)[0],
                mPointInLaser.get(1,0)[0],
                mPointInLaser.get(2,0)[0]);
    }

    Mat multiply(Mat a, Mat b) {
        Mat ret = new Mat();
        Core.gemm(a, b, 1, new Mat(), 0, ret);
        return ret;
    }
}