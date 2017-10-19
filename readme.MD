## SimpleRefreshLayout
-------------------
> 这是一个基于SwipeRefreshLayout的，简单的下拉刷新容器。
> 之所以命名为简单，原因是
>  * Layout结构非常简单，主体仅仅只有1个类，不到1000行（包括注释）。
>  * 使用及自定义非常简单，只需要实现相应的方法，即可做到绝大多数效果。

相关解说，欢迎移步[简书](http://www.jianshu.com/p/65506b0f993a)

效果预览：
![系统默认样式](https://gitee.com/xxxxl/SimpleRefreshLayout/blob/develop/gif/CircelMaterialModel-Demo.gif)
![简易下拉样式](https://gitee.com/xxxxl/SimpleRefreshLayout/blob/develop/gif/SimplePullModel-Demo.gif)

结构说明：
> 1. 本项目基于SwipeRefreshLayout改写，将SwipeRefreshLayout的代码抽象为滑动处理部分（主体）和刷新头部分（Head）。
> 2. 将主体的状态管理全部交由Head控制，主体仅仅将捕获的手势结果传递到Head，然后得到相应的状态结果。
> 3. Head负责刷新头的样式，内容体的位置（是否跟随下拉），是否可以刷新等控制。
> 4. Head覆盖于内容体上层，允许覆盖整个SimpleRefreshLayout，因此可以做到很多的刷新效果，比如省略部分UI设计中的刷新提示Dialog。
> 5. 本项目并未做上拉加载更多实现，但是提供相应的解决方案，并且加入兼容。


引入Android Studio方法:
> compile 'liang.lollipop.simplerefreshlayout:SimpleRefreshLayout:1.0.2'


使用方法：
> ``` Java
> simpleRefreshLayout
>     .setRefreshView(BaseRefreshView view) //设置刷新头
>     .xxxx //设置刷新头的相关属性
> ```
> 以下为使用默认的简单刷新头实现：
> ``` Java
> simpleRefreshLayout
>     .setRefreshView(new SimplePullModel(this))//设置刷新头为简易刷新模式
>     .setPullDownHint("下拉进行刷新")//设置下拉时的提示语
>     .setPullReleaseHint("松手开始刷新")//设置提示释放的提示语
>     .setRefreshingHint("正在进行刷新")//设置正在刷新的提示语
>     .setHintColor(Color.GRAY)//设置刷新提示的文字颜色
>     .setProgressColors(Color.GRAY,Color.TRANSPARENT);//设置刷新进度条的颜色（数组）
> ```
> 注意，除了 _setRefreshView(BaseRefreshView view)_ 方法，其他方法均由刷新头提供，因此，更换刷新头，将会有不同的参数设置项以及刷新效果。

代码介绍：
> _setRefreshView(BaseRefreshView view)_ 这是SimpleRefreshLayout对外最主要的方法，此方法用于初始化SimpleRefreshLayout，同时做到了更换刷新样式而不需要修改布局文件以及其他代码的目的。__此方法请在界面测量前调用并完成初始化。__建议封装为公用的方法，当业务需求变化时，可以一处修改，所有界面的刷新样式全部改变。
> ``` Java
>     public <T extends BaseRefreshView> T setRefreshView(T view) {
>        //如果已经存在刷新头
>        if(mRefreshView!=null){
>            //那么去掉历史控件的刷新接口引用
>            mRefreshView.refreshListener = null;
>            //去除历史控件的Body控制引用
>            mRefreshView.targetViewScroll = null;
>            //移除刷新控件
>            removeView(mRefreshView);
>        }
>        //保存新控件引用
>        mRefreshView = view;
>        //关联刷新接口引用
>        mRefreshView.refreshListener = mListener;
>        //关联Body控制引用
>        mRefreshView.targetViewScroll = this;
>        //添加到Layout中
>        addView(mRefreshView);
>        //返回控件，以方便参数设置
>        return view;
>    }
> ```

自定义刷新头：
> 1. 继承SimpleRefreshLayout.BaseRefreshView
> 2. 实现抽象方法，必要时可以重写相关方法
> ``` Java
>         /**
>         * 下拉刷新的控制方法，实际显示的View需要实现此方法，
>         * 用于实现对用户操作的反馈，建议将变化过程细化，增加跟随手指的感觉
>         * 并且给予足够明显并且有明显暗示性的显示，告知用户当前状态
>         * @param pullPixels 下拉的实际距离
>         * @param totalDragDistance 设定的触发刷新距离
>         * @param originalDragPercent 下拉距离与目标距离间的百分比
>         */
>        protected abstract void onPullToRefresh(float pullPixels,float totalDragDistance,float originalDragPercent);
>
>        /**
>         * 此方法将在结束下拉之后触发，实现或者重写次方法，
>         * 将可以在松手后将View复位或者进行其他相关设置
>         * @param pullPixels 下拉的实际距离
>         * @param totalDragDistance 设定的触发刷新距离
>         * @param originalDragPercent 下拉距离与目标距离间的百分比
>         */
>        protected abstract void onFinishPull(float pullPixels,float totalDragDistance,float originalDragPercent);
> ```
