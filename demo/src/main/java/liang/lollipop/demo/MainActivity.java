package liang.lollipop.demo;

import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import liang.lollipop.simplerefreshlayout.SimpleRefreshLayout;
import liang.lollipop.simplerefreshlayout.models.CircleMaterialModel;
import liang.lollipop.simplerefreshlayout.models.SimplePullModel;

/**
 * @author Lollipop
 */
public class MainActivity extends AppCompatActivity implements SimpleRefreshLayout.OnRefreshListener {

    public static final String ARG_STYLE = "ARG_STYLE";
    public static final int STYLE_CIRCLE_MATERIAL = 0;
    public static final int STYLE_SIMPLE_PULL = 1;

    private SimpleRefreshLayout simpleRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        simpleRefreshLayout = (SimpleRefreshLayout) findViewById(R.id.simpleRefreshLayout);
        simpleRefreshLayout.setOnRefreshListener(this);
        int style = getIntent().getIntExtra(ARG_STYLE, STYLE_CIRCLE_MATERIAL);
        switch (style){
            case STYLE_SIMPLE_PULL:
                simpleRefreshLayout
                        .setRefreshView(new SimplePullModel(this))
                        .setPullDownHint("下拉进行刷新")
                        .setPullReleaseHint("松手开始刷新")
                        .setRefreshingHint("正在进行刷新")
                        .setHintColor(Color.GRAY)
                        .setProgressColors(Color.GRAY,Color.TRANSPARENT);
                break;
            default:
            case STYLE_CIRCLE_MATERIAL:
                simpleRefreshLayout
                        .setRefreshView(new CircleMaterialModel(this))
                        .setColorSchemeResources(R.color.colorAccent,R.color.colorPrimary);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if(simpleRefreshLayout.isRefreshing()){
            simpleRefreshLayout.setRefreshing(false);
        }else{
            super.onBackPressed();
        }
    }

    @Override
    public void onRefresh() {
        Toast.makeText(this,"开始刷新",Toast.LENGTH_SHORT).show();
    }
}
