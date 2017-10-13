package liang.lollipop.simplerefreshlayout;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;

import liang.lollipop.simplerefreshlayout.models.CircleMaterialModel;

/**
 * Created by lollipop on 2017/10/11.
 * 一个简单的刷新Layout
 */
public class SimpleRefreshLayout
        extends ViewGroup
        implements NestedScrollingParent,
        NestedScrollingChild,
        ScrollCallBack{

    @IntDef({CircleMaterialModel,SimplePullModel})
    public @interface HeadStyleModel {}

    //圆形Material加载头，原生风格
    public static final int CircleMaterialModel = 0;
    public static final int SimplePullModel = 1;

    private static final String LOG_TAG = SimpleRefreshLayout.class.getSimpleName();

    // Default offset in dips from the top of the view to where the progress spinner should stop
    private static final int DEFAULT_PULL_TARGET = 64;

    private static final int INVALID_POINTER = -1;

    private OnChildScrollUpCallback mChildScrollUpCallback;
    private View mTarget; // the target of the gesture
    private RefreshView mRefreshView;
    private int mRefreshViewIndex = -1;

    private int mActivePointerId = INVALID_POINTER;

    private OnRefreshListener mListener;
    // Target is returning to its start offset because it was cancelled or a
    // refresh was triggered.
    private boolean mReturningToStart;
    // If nested scrolling is enabled, the total amount that needed to be
    // consumed by this as the nested scrolling parent is used in place of the
    // overscroll determined by MOVE events in the onTouch handler
    private float mTotalUnconsumed;
    private boolean mNestedScrollInProgress;
    private boolean mIsBeingDragged;
    private float mInitialDownY;
    private float mInitialMotionY;
//    private int mTargetAnimationDuration;

    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];

    private int mTouchSlop;

    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;

    private int targetViewOffset = 0;

    public SimpleRefreshLayout(Context context) {
        this(context,null);
    }

    public SimpleRefreshLayout(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SimpleRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
//        mTargetAnimationDuration = getResources().getInteger(
//                android.R.integer.config_mediumAnimTime);
        setWillNotDraw(false);

        ViewCompat.setChildrenDrawingOrderEnabled(this, true);

        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
    }

    void reset() {
        mRefreshView.reset();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            reset();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reset();
    }

    // NestedScrollingParent

    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return isEnabled() && !mReturningToStart && !mRefreshView.isRefreshing()
                && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        // Dispatch up to the nested parent
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL);
        mTotalUnconsumed = 0;
        mNestedScrollInProgress = true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = dy - (int) mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }
            pullRefresh(mTotalUnconsumed);
        }

        // Now let our nested parent consume the leftovers
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        mNestedScrollInProgress = false;
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        if (mTotalUnconsumed > 0) {
            finishPull(mTotalUnconsumed);
            mTotalUnconsumed = 0;
        }
        // Dispatch up our nested parent
        stopNestedScroll();
    }

    @Override
    public void onNestedScroll(final View target, final int dxConsumed, final int dyConsumed,
                               final int dxUnconsumed, final int dyUnconsumed) {
        // Dispatch up to the nested parent first
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                mParentOffsetInWindow);

        // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
        // sometimes between two nested scrolling views, we need a way to be able to know when any
        // nested scrolling parent has stopped handling events. We do that by using the
        // 'offset in window 'functionality to see if we have been moved from the event.
        // This is a decent indication of whether we should take over the event stream or not.
        final int dy = dyUnconsumed + mParentOffsetInWindow[1];
        if (dy < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(dy);
            pullRefresh(mTotalUnconsumed);
        }
    }

     public RefreshView build(@HeadStyleModel int model){
         switch (model){
             case CircleMaterialModel:
             default:
                 return setRefreshView(new CircleMaterialModel(getContext()));
         }
     }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX,
                                    float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY,
                                 boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    private void pullRefresh(float overscrollTop){
        mRefreshView.pullRefresh(overscrollTop);
    }

    private void finishPull(float overscrollTop){
        if(mRefreshView.finishPull(overscrollTop)&&mListener!=null)
            mListener.onRefresh();
    }

    public void setRefreshing(boolean refreshing){
        mRefreshView.setRefreshing(refreshing);
    }

    public boolean isRefreshing(){
        return mRefreshView.isRefreshing();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();
        ensureRefreshView();

        final int action = ev.getAction();
        int pointerIndex;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp()
                || mRefreshView.isRefreshing() || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mRefreshView.reset();
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                mInitialDownY = ev.getY(pointerIndex);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return false;
                }

                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                startDragging(y);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsBeingDragged;
    }

    private void startDragging(float y) {
        final float yDiff = y - mInitialDownY;
        if (yDiff > mTouchSlop && !mIsBeingDragged) {
            mInitialMotionY = mInitialDownY + mTouchSlop;
            mIsBeingDragged = true;
        }
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if ((android.os.Build.VERSION.SDK_INT < 21 && mTarget instanceof AbsListView)
                || (mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget))) {
            // Nope.
        } else {
            super.requestDisallowInterceptTouchEvent(b);
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        int pointerIndex = -1;

        if (mReturningToStart && action == MotionEvent.ACTION_DOWN) {
            mReturningToStart = false;
        }

        if (!isEnabled() || mReturningToStart || canChildScrollUp()
                || mRefreshView.isRefreshing() || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                break;

            case MotionEvent.ACTION_MOVE: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                startDragging(y);

                if (mIsBeingDragged) {
                    final float overscrollTop = (y - mInitialMotionY);
                    if (overscrollTop > 0) {
                        pullRefresh(overscrollTop);
                    } else {
                        return false;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                pointerIndex = ev.getActionIndex();
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG,
                            "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(LOG_TAG, "Got ACTION_UP event but don't have an active pointer id.");
                    return false;
                }

                if (mIsBeingDragged) {
                    final float y = ev.getY(pointerIndex);
                    final float overscrollTop = (y - mInitialMotionY);
                    mIsBeingDragged = false;
                    finishPull(overscrollTop);
                }
                mActivePointerId = INVALID_POINTER;
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                return false;
        }

        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }
        if(mRefreshView==null){
            ensureRefreshView();
        }
        if(mRefreshView==null){
            throw new RuntimeException("RefreshView is Null");
        }
        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        int circleWidth = mRefreshView.getMeasuredWidth();
        int circleHeight = mRefreshView.getMeasuredHeight();
        mRefreshView.layout((width / 2 - circleWidth / 2), 0,
                (width / 2 + circleWidth / 2), circleHeight);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mTarget == null) {
            ensureTarget();
        }
        if (mTarget == null) {
            return;
        }

        if(mRefreshView==null){
            ensureRefreshView();
        }
        if(mRefreshView==null){
            throw new RuntimeException("RefreshView is Null");
        }

        mTarget.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        mRefreshView.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.AT_MOST));
        mRefreshViewIndex = -1;
        // Get the index of the circleview.
        for (int index = 0; index < getChildCount(); index++) {
            if (getChildAt(index) == mRefreshView) {
                mRefreshViewIndex = index;
                break;
            }
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mRefreshViewIndex < 0) {
            return i;
        } else if (i == childCount - 1) {
            // Draw the selected child last
            return mRefreshViewIndex;
        } else if (i >= mRefreshViewIndex) {
            // Move the children after the selected child earlier one
            return i + 1;
        } else {
            // Keep the children before the selected child the same
            return i;
        }
    }

    public <T extends RefreshView> T setRefreshView(T view) {
        //如果已经存在刷新头
        if(mRefreshView!=null){
            //那么去掉刷新控件的引用
            mRefreshView.refreshListener = null;
            mRefreshView.targetViewScroll = null;
            removeView(mRefreshView);
        }
        mRefreshView = view;
        mRefreshView.refreshListener = mListener;
        mRefreshView.targetViewScroll = this;
        addView(mRefreshView);
        return view;
    }

    public RefreshView getParams(){
        return mRefreshView;
    }

    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(mRefreshView)) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    private void ensureRefreshView() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mRefreshView == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof RefreshView && !child.equals(mTarget)) {
                    setRefreshView((RefreshView) child);
                    break;
                }
            }
        }
    }

    @Override
    public void scrollTo(float offsetY) {
        mTarget.setTranslationY(offsetY);
    }

    @Override
    public void scrollWith(float offsetY) {
        mTarget.setTranslationY(mTarget.getTranslationX()+offsetY);
    }

    @Override
    public void lockedScroll() {
        targetViewOffset = (int) mTarget.getTranslationX();
        ViewCompat.offsetTopAndBottom(mTarget, targetViewOffset);
    }

    @Override
    public void resetScroll() {
        ViewCompat.offsetTopAndBottom(mTarget, -targetViewOffset);
    }

    public interface OnRefreshListener {
        /**
         * Called when a swipe gesture triggers a refresh.
         */
        void onRefresh();
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     *         scroll up. Override this if the child view is a custom view.
     */
    public boolean canChildScrollUp() {
        if (mChildScrollUpCallback != null) {
            return mChildScrollUpCallback.canChildScrollUp(this, mTarget);
        }
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (mTarget instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) mTarget;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(mTarget, -1) || mTarget.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(mTarget, -1);
        }
    }

    /**
     * Set a callback to override {@link SimpleRefreshLayout#canChildScrollUp()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    public void setOnChildScrollUpCallback(@Nullable OnChildScrollUpCallback callback) {
        mChildScrollUpCallback = callback;
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe
     * gesture.
     */
    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    /**
     * Classes that wish to override {@link SimpleRefreshLayout#canChildScrollUp()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when {@link SimpleRefreshLayout#canChildScrollUp()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent SwipeRefreshLayout that this callback is overriding.
         * @param child The child view of SwipeRefreshLayout.
         *
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        boolean canChildScrollUp(SimpleRefreshLayout parent, @Nullable View child);
    }

    /**
     * 下拉刷新的显示View
     * 此View同时也控制下拉刷新的属性以及状态
     */
    public static abstract class RefreshView extends FrameLayout implements ValueAnimator.AnimatorUpdateListener{

        protected long targetViewAnimatorDuration = 200;

        protected float mTotalDragDistance = -1;
        protected float mTargetViewOffset = 0;
        protected OnRefreshListener refreshListener;
        protected boolean mRefreshing = false;
        protected ScrollCallBack targetViewScroll;
        protected boolean autoLockView = true;

        protected ValueAnimator targetViewAnimator;

        protected void setTotalDragDistance(float mTotalDragDistance) {
            this.mTotalDragDistance = mTotalDragDistance;
        }

        public RefreshView(Context context) {
            this(context,null);
        }

        public RefreshView(Context context, @Nullable AttributeSet attrs) {
            this(context, attrs,0);
        }

        public RefreshView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            final DisplayMetrics metrics = getResources().getDisplayMetrics();
            mTotalDragDistance = DEFAULT_PULL_TARGET * metrics.density;

            targetViewAnimator = ValueAnimator.ofFloat(0,1);
            targetViewAnimator.setDuration(targetViewAnimatorDuration);
            targetViewAnimator.addUpdateListener(this);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if(animation==targetViewAnimator){
                mTargetViewOffset = (float) animation.getAnimatedValue();
                targetScrollTo(mTargetViewOffset);
            }
        }

        protected void targetScrollTo(float offsetY) {
            if(targetViewScroll!=null)
                targetViewScroll.scrollTo(offsetY);
        }

        protected void targetScrollWith(float offsetY) {
            if(targetViewScroll!=null)
                targetViewScroll.scrollWith(offsetY);
        }

        protected void targetLockedScroll() {
            if(targetViewScroll==null)
                targetViewScroll.lockedScroll();
//            if(targetViewScroll==null)
//                return;
//            float finishOffset = targetViewFinishOffset(0,0,0);
//            targetViewAnimator.cancel();
//
//            targetViewAnimator.setFloatValues(mTargetViewOffset,finishOffset);
//            targetViewAnimator.setDuration((long) (targetViewAnimatorDuration * (1 - (mTargetViewOffset/finishOffset))));
//
//            targetViewAnimator.addListener(new Animator.AnimatorListener() {
//                @Override
//                public void onAnimationStart(Animator animation) {
//
//                }
//
//                @Override
//                public void onAnimationEnd(Animator animation) {
//                    targetViewScroll.lockedScroll();
//                    animation.removeListener(this);
//                }
//
//                @Override
//                public void onAnimationCancel(Animator animation) {
//                    animation.removeListener(this);
//                }
//
//                @Override
//                public void onAnimationRepeat(Animator animation) {
//
//                }
//            });
//
//            targetViewAnimator.start();
        }

        protected void targetResetScroll() {
            if(targetViewScroll==null)
                targetViewScroll.resetScroll();
//            if(targetViewScroll==null)
//                return;
//            targetViewAnimator.cancel();
//
//            targetViewAnimator.setFloatValues(mTargetViewOffset,0);
//            targetViewAnimator.setDuration(targetViewAnimatorDuration);
//
//            targetViewAnimator.addListener(new Animator.AnimatorListener() {
//                @Override
//                public void onAnimationStart(Animator animation) {
//
//                }
//
//                @Override
//                public void onAnimationEnd(Animator animation) {
//                    targetViewScroll.resetScroll();
//                    animation.removeListener(this);
//                }
//
//                @Override
//                public void onAnimationCancel(Animator animation) {
//                    animation.removeListener(this);
//                }
//
//                @Override
//                public void onAnimationRepeat(Animator animation) {
//
//                }
//            });
//
//            targetViewAnimator.start();
        }

        /**
         * 下拉刷新的方法，此方法将直接接受SimpleRefreshLayout的调用
         * 主控件View的位置变化以及刷新显示控件的状态变化都是由此方法开始
         * @param overscrollTop 下拉的距离
         */
        protected void pullRefresh(float overscrollTop){
            //计算当前下拉的百分比
            float originalDragPercent = overscrollTop / mTotalDragDistance;
            //得到目标View的位置变化
            mTargetViewOffset = targetViewOffset(overscrollTop,mTotalDragDistance,originalDragPercent);
            targetScrollTo(mTargetViewOffset);
            onPullToRefresh(overscrollTop,mTotalDragDistance,originalDragPercent);
        }

        protected boolean finishPull(float overscrollTop){
            //计算当前下拉的百分比
            float originalDragPercent = overscrollTop / mTotalDragDistance;
            //触发结束下拉的方法
            onFinishPull(overscrollTop,mTotalDragDistance,originalDragPercent);
            //最后调用一次获取目标View的高度
            mTargetViewOffset = targetViewFinishOffset(overscrollTop,mTotalDragDistance,originalDragPercent);
            targetScrollTo(mTargetViewOffset);
            //返回是否用于刷新的方法
            mRefreshing = canRefresh(overscrollTop,mTotalDragDistance,originalDragPercent);
            if(mRefreshing)
                setRefreshing(mRefreshing);
            return mRefreshing;
        }

        /**
         * 是否开始刷新的确定方法，重写次方法用于决定是否开始刷新动作
         * @param pullPixels 下拉的实际距离
         * @param totalDragDistance 设定的触发刷新距离
         * @param originalDragPercent 下拉距离与目标距离间的百分比
         * @return 返回是否开始刷新，如果是true,那么认为是确认为刷新状态，
         * OnRefreshListener将得到触发
         */
        protected boolean canRefresh(float pullPixels,float totalDragDistance,float originalDragPercent){
            return pullPixels>totalDragDistance;
        }

        /**
         * 返回当前的状态，用于告诉其他部件，确认刷新状态
         * @return
         */
        protected boolean isRefreshing(){
            return mRefreshing;
        }

        /**
         * 下拉刷新的控制方法，实际显示的View需要实现此方法，
         * 用于实现对用户操作的反馈，建议将变化过程细化，增加跟随手指的感觉
         * 并且给予足够明显并且有明显暗示性的显示，告知用户当前状态
         * @param pullPixels 下拉的实际距离
         * @param totalDragDistance 设定的触发刷新距离
         * @param originalDragPercent 下拉距离与目标距离间的百分比
         */
        protected abstract void onPullToRefresh(float pullPixels,float totalDragDistance,float originalDragPercent);

        /**
         * 此方法将在结束下拉之后触发，实现或者重写次方法，
         * 将可以在松手后将View复位或者进行其他相关设置
         * @param pullPixels 下拉的实际距离
         * @param totalDragDistance 设定的触发刷新距离
         * @param originalDragPercent 下拉距离与目标距离间的百分比
         */
        protected abstract void onFinishPull(float pullPixels,float totalDragDistance,float originalDragPercent);

        /**
         * 开始刷新的方法，实现或重写此方法，
         * 用于展示自定义的加载动画方法
         */
        protected void setRefreshing(boolean refreshing){
            if(refreshing && mRefreshing != refreshing){
                callOnRefresh();
            }
            mRefreshing = refreshing;
        }

        protected void setAutoLockView(boolean autoLockView) {
            this.autoLockView = autoLockView;
        }

        /**
         * 这是用来控制主控件View高度变化的方法，在下拉动作触发的时候，
         * 将会调用此方法并且移动主控件View
         * @param pullPixels 下拉的实际距离
         * @param totalDragDistance 设定的触发刷新距离
         * @param originalDragPercent 下拉距离与目标距离间的百分比
         * @return 主控件的位置，此位置为最终位置，并非变化距离。并且此距离为像素距离
         */
        protected float targetViewOffset(float pullPixels,float totalDragDistance,float originalDragPercent){
            //默认不跟随滑动
            return 0;
        }
        /**
         * 这是用来控制主控件View高度变化的方法，在松手动作触发的时候，
         * 将会调用此方法并且移动主控件View
         * @param pullPixels 下拉的实际距离
         * @param totalDragDistance 设定的触发刷新距离
         * @param originalDragPercent 下拉距离与目标距离间的百分比
         * @return 主控件的位置，此位置为最终位置，并非变化距离。并且此距离为像素距离
         */
        protected float targetViewFinishOffset(float pullPixels,float totalDragDistance,float originalDragPercent){
            //默认不跟随滑动
            return 0;
        }

        /**
         * 主动触发刷新监听器，
         * 用于特殊情况下，
         * 主动触发刷新
         */
        protected void callOnRefresh(){
            if(refreshListener!=null)
                refreshListener.onRefresh();
        }

        /**
         * 此方法用于重置显示状态
         */
        protected void reset(){
        }
    }

    public void setMoreListener(RecyclerView recyclerView,OnScrollDownListener.OnScrollListener onScrollListener){
        recyclerView.addOnScrollListener(
                new OnScrollDownListener(
                        (LinearLayoutManager) recyclerView.getLayoutManager(),
                        onScrollListener));
    }

}
