package liang.lollipop.simplerefreshlayout.models;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.ColorFilter;
import android.os.Build;
import android.support.annotation.IntRange;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.TextView;

import liang.lollipop.simplerefreshlayout.R;
import liang.lollipop.simplerefreshlayout.SimpleRefreshLayout;
import liang.lollipop.simplerefreshlayout.models.simple.FanLoaderDrawable;

/**
 * Created by Lollipop on 2017/10/12.
 * 简单的下拉模式
 * @author Lollipop
 */
public class SimplePullModel extends SimpleRefreshLayout.BaseRefreshView {
    /**
     * 旋转动画的动画时间
     */
    private long rotatingAnimationDuration = 1600;
    /**
     * 收起动画的动画时间
     */
    private long moveToTopAnimationDuration = 300;
    /**
     * 展开动画的动画时间
     */
    private long moveToTargetAnimationDuration = 300;
    /**
     * 旋转的View
     */
    private ImageView progressView;
    /**
     * 旋转View的内容绘制
     */
    private FanLoaderDrawable progressDrawable;
    /**
     * 文本提示的View
     */
    private TextView hintView;
    /**
     * 旋转动画的动画本身
     */
    private Animation rotatingAnimation;
    /**
     * 下拉的提示语
     */
    private CharSequence pullDownHint = "下拉进行刷新";
    /**
     * 提示松手的描述
     */
    private CharSequence pullReleaseHint = "松手开始刷新";
    /**
     * 刷新过程中的描述语
     */
    private CharSequence refreshingHint = "正在刷新";
    /**
     * 触发刷新的百分比
     */
    private float triggerPercent = 0.8f;
    /**
     * 当前的刷新状态
     */
    private boolean thisRefreshingStatus = mRefreshing;
    /**
     * 移动View用的动画
     */
    private ValueAnimator moveAnimation;
    /**
     * Y坐标偏移
     */
    private float offsetY = 0;
    /**
     * 进度条的方向
     */
    private boolean progressDirection = true;

    public SimplePullModel(Context context) {
        this(context,null);
    }

    public SimplePullModel(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SimplePullModel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //引入布局
        LayoutInflater.from(context).inflate(R.layout.head_simple_pull,this,true);
        //找到进度显示View
        progressView = (ImageView) findViewById(R.id.head_simple_pull_pro);
        //此处做一下简单的版本兼容，并且将进度显示Drawable实例化
        progressView.setImageDrawable(progressDrawable = new FanLoaderDrawable());
        //找到文本提示view
        hintView = (TextView) findViewById(R.id.head_simple_pull_text);
        //初始化旋转动画
        rotatingAnimation = new Animation(){
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                rotatingProgress(interpolatedTime);
            }
        };
        //设置旋转动画的时间
        rotatingAnimation.setDuration(rotatingAnimationDuration);
        //设置旋转动画为无限循环
        rotatingAnimation.setRepeatCount(Animation.INFINITE);
        //设置循环模式为重新开始
        rotatingAnimation.setRepeatMode(Animation.RESTART);
        //设置动画状态监听器，用于重置一些状态
        rotatingAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                progressDirection = true;
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                progressDirection = !progressDirection;
            }
        });
        //实例化刷新头位置移动的View
        moveAnimation = new ValueAnimator();
        //为移动动画设置动画监听器
        moveAnimation.addUpdateListener(this);
        //重置
        post(new Runnable() {
            @Override
            public void run() {
                reset();
            }
        });
    }
    /**
     * 旋转进度条
     */
    private void rotatingProgress(float pro){
        //此处做一个判断，用于显示进度的缩放，因为如果单纯的旋转，动画会有短暂的停止
        //所以此处通过进度条的缩放，减少动画重复执行时停止的影响
        if(progressDirection){
            progressDrawable.setProgress(1-pro);
        }else{
            progressDrawable.setProgress(pro);
        }
        //旋转进度View，吃醋设置为逆时针旋转，一次动画旋转2圈，因为旋转的过程中，
        //进度条也在缩放，如果设置为1圈，那么看到的效果为进度条没有旋转
        progressView.setRotation(-720*pro);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        //在测量结束后，立刻设置下拉标准高度为View的高度
        setTotalDragDistance(getHeight());
    }

    /**
     * 重置方法，用于重置一些View的状态，比如清除动画，View复位等
     */
    @Override
    protected void reset() {
        super.reset();
        clearAnimation();
        moveAnimation.cancel();
        progressView.clearAnimation();
        progressView.setRotation(0);
        hintView.setAlpha(0);
        moveView(0);
    }

    @Override
    protected void onPullToRefresh(float pullPixels, float totalDragDistance, float originalDragPercent) {
        //下拉时候，我们需要让View停止动画，因此调用动画清理方法
        progressView.clearAnimation();
        //停止位置移动动画
        moveAnimation.cancel();
        //将进度View的旋转状态重置为0
        progressView.setRotation(0);
//        float maxLength = totalDragDistance*2;
        //得到下拉位置
        float yLoc = pullPixels;
        //如果下拉超过最大位置了，需要增加阻尼，减少下拉速度
        //此处为将多余部分除以当前长度百分比，用以持续增加阻尼效果
        if(originalDragPercent>1){
            yLoc = (pullPixels-totalDragDistance)/originalDragPercent + totalDragDistance;
        }
        //移动View，展现状态
        moveView(yLoc);
    }

    @Override
    protected void onFinishPull(float pullPixels, float totalDragDistance, float originalDragPercent) {
        //停止下拉时，如果未触发刷新
        if(originalDragPercent<=triggerPercent){
            //移动View到顶部
            startMoveToTop();
        }
    }

    /**
     * 通过动画移动View到顶部，
     * 此方法会在刷新状态修改，或者松手时调用
     */
    private void startMoveToTop(){
        //清理现有移动动画
        moveAnimation.cancel();
        //清除进度View的动画
        progressView.clearAnimation();
        //设置移动范围为：当前位置-顶部
        moveAnimation.setFloatValues(offsetY,0);
        //计算动画时间：移动的距离/总距离%最大移动时间，尽量保持速度一致的同时防止过长的距离导致动画时间过长
        long d = (long) (moveToTopAnimationDuration*Math.abs(offsetY/getHeight()))%moveToTopAnimationDuration;
        moveAnimation.setDuration(d);
        //开始执行动画
        moveAnimation.start();
    }

    /**
     * 通过动画移动View到目标地址
     * 此方法会在刷新状态改变，或者松手时触发
     */
    private void startMoveToTarget(){
        //清理现有移动动画
        progressView.clearAnimation();
        //清除进度View的动画
        moveAnimation.cancel();
        //设置移动范围为：当前位置-目标位置（设置为head底部）
        moveAnimation.setFloatValues(offsetY,getHeight());
        //计算动画时间：移动的距离/总距离%最大移动时间，尽量保持速度一致的同时防止过长的距离导致动画时间过长
        long d = (long) (moveToTargetAnimationDuration*Math.abs(offsetY/getHeight()))%moveToTargetAnimationDuration;
        moveAnimation.setDuration(d);
        //开始执行动画
        moveAnimation.start();
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        super.onAnimationUpdate(animation);
        //这是ValueAnimator的动画更新回调
        //判断如果是自己的移动动画发生的回掉
        if(animation==moveAnimation){
            //那么移动位置到动画位置
            moveView((float)animation.getAnimatedValue());
        }
    }

    /**
     * 移动View到指定位置，同时关联View的状态属性变化
     * @param y Y轴位置
     */
    private void moveView(float y){
        //记录当前位置
        offsetY = y;
        //得到View高度（即定义的滑动标准距离）
        int height = getHeight();
        //设置当前滑动进度，即总滑动范围的80%
        float pro = y/height/0.8f;
        //判断当前记录的刷新状态
        if(thisRefreshingStatus){
            hintView.setText(refreshingHint);
        }else if(pro>1){
            hintView.setText(pullReleaseHint);
        }else {
            hintView.setText(pullDownHint);
        }
        //如果超过了触发标准，不再继续增加进度
        if(pro>1){
            pro = 1;
        }
        //设置进度显示
        progressDrawable.setProgress(pro);
        //设置描述文字透明度
        hintView.setAlpha(pro);
        //移动当前head
        setTranslationY((y-getHeight())/2);
        //移动列表内容
        targetScrollTo(y);
    }

    /**
     * 这是用于指定内容主体的位置变化
     * @param pullPixels 下拉的实际距离
     * @param totalDragDistance 设定的触发刷新距离
     * @param originalDragPercent 下拉距离与目标距离间的百分比
     * @return 返回内容体的Y坐标
     * 此处本方法意义不大，因为位置移动已经被重写
     */
    @Override
    protected float targetViewOffset(float pullPixels, float totalDragDistance, float originalDragPercent) {
        return (pullPixels-totalDragDistance)/originalDragPercent + totalDragDistance;
    }

    /**
     * 松手时，用于确定内容主体的最后位置
     * @param pullPixels 下拉的实际距离
     * @param totalDragDistance 设定的触发刷新距离
     * @param originalDragPercent 下拉距离与目标距离间的百分比
     * @return 返回内容体的Y坐标
     */
    @Override
    protected float targetViewFinishOffset(float pullPixels, float totalDragDistance, float originalDragPercent) {
        if(pullPixels==0&&totalDragDistance==0&&originalDragPercent==0){
            return getHeight();
        }
        return pullPixels>totalDragDistance?totalDragDistance:pullPixels;
    }


    @Override
    protected void setRefreshing(boolean refreshing) {
        super.setRefreshing(refreshing);
        if(refreshing){
            hintView.setText(refreshingHint);
        }else{
            hintView.setText(pullDownHint);
        }
        if(refreshing && !thisRefreshingStatus){
            startMoveToTarget();
            progressView.setRotation(1);
            progressView.startAnimation(rotatingAnimation);
        }else if(thisRefreshingStatus != refreshing){
            startMoveToTop();
        }
        thisRefreshingStatus = refreshing;
    }

    @Override
    protected boolean canRefresh(float pullPixels, float totalDragDistance, float originalDragPercent) {
        return originalDragPercent>triggerPercent;
    }

    public SimplePullModel setMoveToTopAnimationDuration(long moveToTopAnimationDuration) {
        this.moveToTopAnimationDuration = moveToTopAnimationDuration;
        return this;
    }

    public SimplePullModel setMoveToTargetAnimationDuration(long moveToTargetAnimationDuration) {
        this.moveToTargetAnimationDuration = moveToTargetAnimationDuration;
        return this;
    }

    public SimplePullModel setPullDownHint(CharSequence pullDownHint) {
        this.pullDownHint = pullDownHint;
        return this;
    }

    public SimplePullModel setPullReleaseHint(CharSequence pullReleaseHint) {
        this.pullReleaseHint = pullReleaseHint;
        return this;
    }

    public SimplePullModel setRefreshingHint(CharSequence refreshingHint) {
        this.refreshingHint = refreshingHint;
        return this;
    }

    public SimplePullModel setHintColor(int color){
        hintView.setTextColor(color);
        return this;
    }

    public SimplePullModel setProgressColors(int... colors){
        progressDrawable.setColors(colors);
        return this;
    }

    public SimplePullModel setProgressAlpha(@IntRange(from = 0, to = 255)int alpha){
        progressDrawable.setAlpha(alpha);
        return this;
    }

    public SimplePullModel setProgressColorFilter(@Nullable ColorFilter colorFilter) {
        progressDrawable.setColorFilter(colorFilter);
        return this;
    }

    public SimplePullModel setPetalSize(int petalSize) {
        progressDrawable.setPetalSize(petalSize);
        return this;
    }

    public SimplePullModel setPetalHeightPercent(float petalHeightPercent) {
        progressDrawable.setPetalHeightPercent(petalHeightPercent);
        return this;
    }

    public SimplePullModel setPetalWidthPercent(float petalWidthPercent) {
        progressDrawable.setPetalWidthPercent(petalWidthPercent);
        return this;
    }

}
