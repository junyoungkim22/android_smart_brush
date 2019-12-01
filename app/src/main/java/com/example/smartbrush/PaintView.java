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

    public static int BRUSH_SIZE = 20;
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
    private int initx, inity;
    private int lastx, lasty;
    private int sensitivity;

    TextView myLabel;
    EditText myTextbox;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

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
            int corx = x - initx;
            int cory = y - inity;
            int diffx = x - lastx;
            int diffy = y - lasty;
            if(Math.abs(diffx) > 30 || Math.abs(diffy) > 30)
                return;
            Log.d("DRAW", "2");

            int drawx = startx + (corx * sensitivity);
            int drawy = starty + (cory * sensitivity);

            int last_corx = lastx - initx;
            int last_cory = lasty - inity;
            int last_drawx = startx + (last_corx * sensitivity);
            int last_drawy = starty + (last_cory * sensitivity);

            mPath = new Path();
            FingerPath fp = new FingerPath(currentColor, emboss, blur, strokeWidth, mPath);
            paths.add(fp);

            mPath.reset();
            mPath.moveTo(last_drawx, last_drawy);

            mPath.lineTo(drawx, drawy);

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


        }
    };


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
    }

    public void calibrate(int x, int y, int z){
        if(times == 0){
            if(x == 0 || y == 0)
                return;
            times += 1;
            Log.d("CAL", "init");
            lastx = x;
            lasty = y;
            return;
        }

        if(x == 0 || y ==0)
            return;
        if(Math.abs(x - lastx) > 100){
            times = 0;
            return;
        }
        if(Math.abs(y - lasty) > 100){
            times = 0;
            return;
        }
        Log.d("CAL", "go");
        times += 1;

        if(times == 5){
            Calibrated = true;
            initx = lastx;
            inity = lasty;
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