package com.example.smartbrush;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.EmbossMaskFilter;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;


public class PaintView extends View {

    public static int BRUSH_SIZE = 50;
    public static final int DEFAULT_COLOR = Color.BLACK;
    public static final int DEFAULT_BG_COLOR = Color.WHITE;
    private static final float TOUCH_TOLERANCE = 4;
    private float mX, mY;
    private Path mPath;
    private Paint mPaint;
    private ArrayList<FingerPath> paths = new ArrayList<>();
    private int currentColor;
    private int backgroundColor = DEFAULT_BG_COLOR;
    private int strokeWidth;
    private boolean emboss;
    private boolean blur;
    private MaskFilter mEmboss;
    private MaskFilter mBlur;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mBitmapPaint = new Paint(Paint.DITHER_FLAG);

    private int width, height;
    private boolean Calibrated;
    private int x, y;
    private int startx, starty;
    private int initx, inity, initz;
    private int lastx, lasty, lastz;
    private int last_drawx, last_drawy;
    private int sensitivity;

    private ArrayList<Integer> drawx_values = new ArrayList<>();
    private ArrayList<Integer> drawy_values = new ArrayList<>();
    private ArrayList<Integer> drawz_values = new ArrayList<>();

    private final int MAX_WIDTH = 100;
    private final int BRUSH_HEIGHT = 50;

    private ArrayList<Integer> weights = new ArrayList<>();
    int weights_sum;
    private int window;
    private int degree;

    private int currx;
    private int curry;
    private final int CIRCLE_COLOR = Color.BLUE;
    private Paint circle_paint;


    int times = 0;

    public PaintView(Context context) {
        this(context, null);
    }

    public PaintView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(DEFAULT_COLOR);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setXfermode(null);
        mPaint.setAlpha(0xff);

        circle_paint = new Paint();
        circle_paint.setColor(CIRCLE_COLOR);

        mEmboss = new EmbossMaskFilter(new float[] {1, 1, 1}, 0.4f, 6, 3.5f);
        mBlur = new BlurMaskFilter(5, BlurMaskFilter.Blur.NORMAL);
    }

    public Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            //int x = msg.arg1;
            //int y= msg.arg2;
            //byte[] readBuf = (byte[])msg.obj;
            //String strIncom = new String(readBuf, 0, 7);
            String strIncom = (String)msg.obj;
            //Log.d("VIEW", strIncom);
            String[] disVals = strIncom.split("\t");
            if(disVals.length != 3)
                return;
            int x = 0, y = 0 , z = 0;
            try {
                x = (int) Float.parseFloat(disVals[0]);
                y = (int) Float.parseFloat(disVals[1]);
                z = (int) Float.parseFloat(disVals[2]);
            } catch (Exception e) {
                Log.d("DEBUG", "Parse Exception");
            }

            /*
            mCanvas.drawCircle(x, y, 10, mPaint);
            //onDraw(mCanvas);
            invalidate();
             */

            if(!Calibrated){
                calibrate(x, y, z);
                return;
            }

            /*
            touchStart(x, y);
            invalidate();
            touchMove(x, y + 5);
            invalidate();
            touchUp();
            invalidate();

             */

            if(x == 0 || y == 0 || z ==0)
                return;
            Log.d("DRAW", "1");
            //int corx = x - initx;
            //int cory = y - inity;
            int diffx = x - lastx;
            int diffy = y - lasty;
            if(Math.abs(diffx) > 30 || Math.abs(diffy) > 30) {
                Log.d("DRAW", strIncom);
                return;
            }
            Log.d("DRAW", "2");

            //int drawx = startx + (corx * sensitivity);
            //int drawy = starty + (cory * sensitivity);
            int drawx = get_drawx_coord(x);
            int drawy = get_drawy_coord(y);

            currx = drawx;
            curry = drawy;

            drawx_values.add(drawx);
            drawy_values.add(drawy);
            drawz_values.add(z);

            if(drawx_values.size() < window)
                return;

            /*
            int last_corx = lastx - initx;
            int last_cory = lasty - inity;
            int last_drawx = startx + (last_corx * sensitivity);
            int last_drawy = starty + (last_cory * sensitivity);
             */
            //int last_drawx = get_drawx_coord(lastx);
            //int last_drawy = get_drawy_coord(lasty);
            drawx = getSmooth(drawx_values);
            drawy = getSmooth(drawy_values);

            int drawz = getSmooth(drawz_values);
            if(drawz < initz) {
                int width = getWidth(drawz);
                Log.d("WIDTH", Integer.toString(width));

                mPath = new Path();
                //FingerPath fp = new FingerPath(currentColor, emboss, blur, strokeWidth, mPath);
                FingerPath fp = new FingerPath(currentColor, emboss, blur, width, mPath);
                paths.add(fp);

                mPath.reset();
                mPath.moveTo(last_drawx, last_drawy);

                mPath.lineTo(drawx, drawy);
            }

            invalidate();

            /*
            touchStart(drawx, drawy);
            invalidate();
            touchMove(drawx, drawy + 5);
            invalidate();
            touchUp();
            invalidate();
             */

            Log.d("DRAW", "3");

            lastx = x;
            lasty = y;
            last_drawx = drawx;
            last_drawy = drawy;

        }
    };

    public int getWidth(int z){
        if(initz - z > BRUSH_HEIGHT){
            return MAX_WIDTH;
        }
        float ratio = (float)(initz - z) / 50;
        return (int) (ratio * MAX_WIDTH);
    }

    public int get_drawx_coord(int x){
        int corx = x - initx;
        return startx + (corx * sensitivity);
    }

    public int get_drawy_coord(int y){
        int cory = y - inity;
        return starty + (cory * sensitivity);
    }

    public int getSmooth(ArrayList<Integer> arr){
        int last = arr.size() - 1;
        float sum = 0;
        for(int i = 0; i < window; i++){
            sum += weights.get(i) * arr.get(arr.size() - window + i);
        }
        sum /= weights_sum;
        return (int) sum;
    }


    public void init(DisplayMetrics metrics) {
        int height = metrics.heightPixels;
        int width = metrics.widthPixels;

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        currentColor = DEFAULT_COLOR;
        strokeWidth = BRUSH_SIZE;
        Calibrated = false;
        sensitivity = 7;
        startx = 830;
        starty = 430;

        degree = 3;
        window = degree*2 - 1;
        for(int i = 1; i < 2*degree; i++){
            weights.add(degree - Math.abs(degree - i));
        }
        weights_sum = 0;
        for(int i = 0; i < weights.size(); i++){
            weights_sum += weights.get(i);
        }
    }

    public void calibrate(int x, int y, int z){
        if(times == 0){
            if(x == 0 || y == 0)
                return;
            times += 1;
            Log.d("CAL", "init");
            lastx = x;
            lasty = y;
            lastz = z;
            return;
        }

        if(x == 0 || y ==0 || z == 0)
            return;
        if(Math.abs(x - lastx) > 100){
            times = 0;
            return;
        }
        if(Math.abs(y - lasty) > 100){
            times = 0;
            return;
        }
        if(Math.abs(z - lastz) > 100){
            times = 0;
            return;
        }
        Log.d("CAL", "go");
        times += 1;

        if(times == 5){
            Calibrated = true;
            initx = lastx;
            inity = lasty;
            initz = lastz;
            last_drawx = startx;
            last_drawy = starty;
            Log.d("CAL", "done");
            return;
        }
    }

    public void normal() {
        emboss = false;
        blur = false;
    }

    public void emboss() {
        emboss = true;
        blur = false;
    }

    public void blur() {
        emboss = false;
        blur = true;
    }

    public void clear() {
        backgroundColor = DEFAULT_BG_COLOR;
        paths.clear();
        normal();
        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        mCanvas.drawColor(backgroundColor);

        for (FingerPath fp : paths) {
            mPaint.setColor(fp.color);
            mPaint.setStrokeWidth(fp.strokeWidth);
            mPaint.setMaskFilter(null);

            if (fp.emboss)
                mPaint.setMaskFilter(mEmboss);
            else if (fp.blur)
                mPaint.setMaskFilter(mBlur);

            mCanvas.drawPath(fp.path, mPaint);

        }
        mCanvas.drawCircle(currx, curry, 10, circle_paint);

        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.restore();
    }



    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        this.width = w;
        this.height = h;
        this.startx = w / 4;
        this.starty = h / 4;
        super.onSizeChanged(w, h, oldw, oldh);
    }


    public void touchStart(float x, float y) {
        mPath = new Path();
        FingerPath fp = new FingerPath(currentColor, emboss, blur, strokeWidth, mPath);
        paths.add(fp);

        mPath.reset();
        mPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    public void touchMove(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
            mX = x;
            mY = y;
        }
    }

    private void touchUp() {
        mPath.lineTo(mX, mY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        Log.d("COORD", Float.toString(x) + " " + Float.toString(y));

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN :
                touchStart(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE :
                touchMove(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP :
                touchUp();
                invalidate();
                break;
        }

        return true;
    }

}