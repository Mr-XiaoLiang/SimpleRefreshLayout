package liang.lollipop.simplerefreshlayout;

/**
 * Created by Lollipop on 2017/10/11.
 * 这是为下拉头准备的用于关联内容体的回调
 * 辅助调整内容体的位置
 * @author Lollipop
 */
public interface ScrollCallBack {

    /**
     * 这是直接指定滑动位置的方法
     * @param offsetY 真实的基于父类的Y坐标
     */
    void scrollTo(float offsetY);

    /**
     * 这是坐标变化值的方法
     * @param offsetY 基于现有位置变化的值
     */
    void scrollWith(float offsetY);

    /**
     * 锁定当前位置，可以用于在刷新过程中锁定当前位置，为刷新头提供稳定的状态。
     */
    void lockedScroll();

    /**
     * 重置位置
     */
    void resetScroll();

}
