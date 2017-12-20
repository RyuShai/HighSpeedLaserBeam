package vn.laser.vp9.highspeedlasershooter;

/**
 * Created by ryu on 12/11/17.
 */

import android.os.Environment;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static android.content.ContentValues.TAG;

public class XmlParser {
    public static Mat rRodC2L,tC2L,camera_intrinsics,distortion;
    public static double alphaStep,betaStep;
    public static int deltaXStep,deltaYStep;

    public static void readDataFromFile(String path)
    {
        File initialFile = new File(path);//Environment.getExternalStorageDirectory()+"/cam2laserMatrices.xml");
        XmlPullParserFactory xmlFactoryObject = null;
        XmlPullParser myParser = null;
        Log.e(TAG,"Ryu haha");
        try {
            InputStream is =  new FileInputStream(initialFile);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            Log.e(TAG,"here");

            Element element=doc.getDocumentElement();
            element.normalize();
            NodeList nList;
            if((nList= doc.getElementsByTagName("rRodC2L")).getLength()>0)
            {
                initMatrix(nList.item(0));
            }
            if((nList= doc.getElementsByTagName("tC2L")).getLength()>0)
            {
                initMatrix(nList.item(0));
            }
            if((nList= doc.getElementsByTagName("camera_intrinsics")).getLength()>0)
            {
                initMatrix(nList.item(0));
            }
            if((nList= doc.getElementsByTagName("distortion")).getLength()>0)
            {
                initMatrix(nList.item(0));
            }
            String alpha = doc.getElementsByTagName("alphaStep").item(0).getChildNodes().item(0).getNodeValue();
            String deltaX = doc.getElementsByTagName("deltaStepX").item(0).getChildNodes().item(0).getNodeValue();
            String deltaY = doc.getElementsByTagName("deltaStepY").item(0).getChildNodes().item(0).getNodeValue();
            if(alpha!=null)
                alphaStep = Double.parseDouble(alpha);
            String beta = doc.getElementsByTagName("betaStep").item(0).getChildNodes().item(0).getNodeValue();
            if(beta!=null)
                betaStep = Double.parseDouble(beta);
            if(deltaX!=null)
                deltaXStep=Integer.parseInt(deltaX);
            if(deltaY!=null)
                deltaYStep=Integer.parseInt(deltaY);
            Log.e("Test","Ryu alpha: "+alpha + " beta: "+beta);

        }catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }
    static private String getValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag).item(0).getChildNodes();
        Node node = nodeList.item(0);
        return node.getNodeValue();
    }
    static private void  initMatrix(Node node)
    {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            Log.e(TAG,"tag name: "+element.getTagName());
            Log.e(TAG,"\nrRodC2 rows : " + getValue("rows", element)+"\n");
            Log.e(TAG,"rRodC2L cols : " + getValue("cols", element)+"\n");
            Log.e(TAG,"data: "+ getValue("data",element)+"\n");
            Log.e(TAG,"-----------------------");
            int row,col;
            row = Integer.parseInt(getValue("rows", element));
            col= Integer.parseInt(getValue("cols", element));
            String data = getValue("data",element);
            data = data.replace("\r\n", "").replace("\n", "");
            while(data.contains("  "))
            {
                data = data.replace("  "," ");
            }
            data = data.trim();
            Log.e(TAG,"data: "+data);
            String[] listData  = data.split(" ");
            Log.e(TAG,String.valueOf(listData.length));
            double[] realData = new double[listData.length];
            for(int i=0;i<listData.length;i++)
            {

                realData[i] = Float.parseFloat(listData[i]);
                Log.e(TAG,"Ryu:"+realData[i]);
            }
            String mat = element.getTagName();
            switch (mat)
            {
                case "rRodC2L":
                    rRodC2L = new Mat(row,col,CvType.CV_64FC1);
                    rRodC2L.put(0,0,realData);
                    break;
                case "tC2L":
                    tC2L = new Mat(row,col, CvType.CV_64FC1);
                    tC2L.put(0,0,realData);
                    break;
                case "camera_intrinsics":
                    camera_intrinsics = new Mat(row,col,CvType.CV_64FC1);
                    camera_intrinsics.put(0,0,realData);
                    break;
                case "distortion":
                    distortion = new Mat(row,col,CvType.CV_64FC1);
                    distortion.put(0,0,realData);
                    break;
            }
        }
    }
}
