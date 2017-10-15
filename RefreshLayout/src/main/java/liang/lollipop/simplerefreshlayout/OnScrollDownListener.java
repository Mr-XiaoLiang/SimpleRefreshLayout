package liang.lollipop.simplerefreshlayout;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

/**
 * Created by Lollipop on 2016/10/7.
 * RecyclerView的加载更多监听
 * @author Lollipop
 */

public class OnScrollDownListener extends RecyclerView.OnScrollListener{
    /**
     * 用来标记是否正在向最后一个滑动，既是否向下滑动
     */
    private boolean isSlidingToLast = false;
    private LinearLayoutManager manager;
    private int loadSize = 5;

    private OnScrollListener onScroll;

    public OnScrollDownListener(LinearLayoutManager manager, OnScrollListener onScroll) {
        this(5,manager,onScroll);
    }

    public OnScrollDownListener(int loadSize, LinearLayoutManager manager, OnScrollListener onScroll) {
        this.loadSize = loadSize;
        this.manager = manager;
        this.onScroll = onScroll;
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                // 当不滚动时
                onScroll.onScroll(true,newState);
            }else{
                onScroll.onScroll(!isSlidingToLast,newState);
            }
    }
    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
        isSlidingToLast = dy>0;
        //获取最后一个完全显示的ItemPosition
        int lastVisiblePositions = manager.findLastVisibleItemPosition();
//        int lastVisiblePos = getMaxElem(lastVisiblePositions);
        int totalItemCount = manager.getItemCount();
        // 判断是否滚动到底部
        if (lastVisiblePositions > (totalItemCount - loadSize) && isSlidingToLast) {
            //加载更多功能的代码
            onScroll.onMore();
        }
    }

    private int getMaxElem(int[] arr) {
        int maxVal = Integer.MIN_VALUE;
        for(int a:arr){
            if(a>maxVal){
                maxVal = a;
            }
        }
        return maxVal;
    }

    public interface OnScrollListener{
        /**
         * 列表状态变化的回调方法
         * @param down 是否正在向下滚动
         * @param newState 新的状态的什么
         */
        public void onScroll(boolean down, int newState);

        /**
         * 需要加载更多时的回调方法
         */
        public void onMore();
    }

}