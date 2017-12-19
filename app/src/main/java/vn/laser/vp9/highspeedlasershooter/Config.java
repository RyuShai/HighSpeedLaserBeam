package vn.laser.vp9.highspeedlasershooter;

import android.util.Log;

/**
 * Created by ryu on 12/11/17.
 */

public class Config {
        public static String tag = "Ryu";
        public static final int PERMISSION_EXTERNAL_STORAGE =0;
        public static final int PERMISSION_CAMERA = 1;
        public static int MAX=4000,moveStep=1000;
        public static long delayTimer = 3;
        public static int scaleSize = 4;

        public enum OBJECT_COLOR{
                RED_BALL,
                GREEN_BALL,
                ORANGE_BALL
        }

        public enum DETECT_OPTION{
                CHECKER_BOARD,
                FACE,
                SINGLE_BALL,
                DOUBLE_BALL,
                LASER_RECTANGLE
        }

        void funtion()
        {
                Log.e("Ryu", "haha");
        }
}
