package liang.lollipop.simplerefreshlayout.models.simple;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by Lollipop on 2017/10/12.
 * 扇形的菊花加载图
 * @author Lollipop
 */
public class FanLoaderDrawable extends Drawable {

    /**
     * 花瓣数量
     */
    private int petalSize = 9;

    /**
     * 花瓣的长度百分比
     */
    private float petalHeightPercent = 0.35f;
    /**
     * 花瓣的宽度百分比
     */
    private float petalWidthPercent = 0.25f;
    /**
     * 花瓣是否使用圆角
     */
    private Paint.Cap petalCap = Paint.Cap.ROUND;
    /**
     * 画笔
     */
    private Paint paint;
    /**
     * 颜色数组
     */
    private int[] colorArray;
    /**
     * 半径
     */
    private int radius = 0;
    /**
     * 花瓣长度
     */
    private float petalHeight = 0;

    @FloatRange(from = 0,to = 1)
    private float progress = 0;

    public FanLoaderDrawable() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeCap(petalCap);
        setColors(0xFF333333,0);
    }

    public void setColors(int... colors){
        colorArray = colors;
        initColor();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        init();
    }

    private void init(){
        if(getBounds().height()<1||getBounds().width()==0){
            return;
        }
        radius = Math.min(getBounds().width()/2,getBounds().height()/2);
        paint.setStrokeWidth(petalWidthPercent*radius);
        petalHeight = petalHeightPercent*radius;
        initColor();
    }

    private void initColor(){
        if(getBounds().width()<1||getBounds().height()<1){
            return;
        }
        float cx = getBounds().centerX();
        float cy = getBounds().centerY();
        paint.setShader(new SweepGradient(cx,cy,colorArray,null));
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float step = 360.0f/petalSize;
        int size = (int) ((360*progress + step*0.5f)/step);
        float[] loc = null;
        int cx = getBounds().centerX();
        int cy = getBounds().centerY();
        for(int i = 0;i<size;i++){
            int angle = (int)((i+0.7f)*step*-1);
            angle += 90;
            loc = getScale(loc,radius*(1-petalWidthPercent*0.5f),petalHeight,angle,cx,cy);
            canvas.drawLine(loc[0],loc[1],loc[2],loc[3],paint);
        }
    }

    @Override
    public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    private float[] getScale(float[] loc,float radius,float length,int angle,int cx,int cy) {
        if(loc==null || loc.length<4){
            loc = new float[4];
        }
        loc[0] = (float) (Math.sin(2 * Math.PI / 360 * angle) * radius + cx);
        loc[1] = (float) (-Math.cos(2 * Math.PI / 360 * angle) * radius + cy);
        loc[2] = (float) (Math.sin(2 * Math.PI / 360 * angle) * (radius - length) + cx);
        loc[3] = (float) (-Math.cos(2 * Math.PI / 360 * angle) * (radius - length) + cy);
        return loc;
    }

    public void setProgress(@FloatRange(from = 0,to = 1) float progress) {
        this.progress = progress;
        invalidateSelf();
    }

    public void setPetalSize(int petalSize) {
        this.petalSize = petalSize;
        init();
        invalidateSelf();
    }

    public void setPetalHeightPercent(float petalHeightPercent) {
        this.petalHeightPercent = petalHeightPercent;
        init();
        invalidateSelf();
    }

    public void setPetalWidthPercent(float petalWidthPercent) {
        this.petalWidthPercent = petalWidthPercent;
        init();
        invalidateSelf();
    }
}
