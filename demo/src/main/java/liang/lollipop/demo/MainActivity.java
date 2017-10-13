package liang.lollipop.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

import liang.lollipop.simplerefreshlayout.SimpleRefreshLayout;
import liang.lollipop.simplerefreshlayout.models.CircleMaterialModel;
import liang.lollipop.simplerefreshlayout.models.SimplePullModel;

public class MainActivity extends AppCompatActivity implements SimpleRefreshLayout.OnRefreshListener {

    public static final String ARG_STYLE = "ARG_STYLE";
    public static final int STYLE_CircleMaterial = 0;
    public static final int STYLE_SimplePull = 1;

    private SimpleRefreshLayout simpleRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        simpleRefreshLayout = (SimpleRefreshLayout) findViewById(R.id.simpleRefreshLayout);
        simpleRefreshLayout.setOnRefreshListener(this);
        int style = getIntent().getIntExtra(ARG_STYLE,STYLE_CircleMaterial);
        switch (style){
            case STYLE_CircleMaterial:
                simpleRefreshLayout
                        .setRefreshView(new CircleMaterialModel(this))
                        .setColorSchemeResources(R.color.colorAccent,R.color.colorPrimary);
                break;
            case STYLE_SimplePull:
                simpleRefreshLayout.setRefreshView(new SimplePullModel(this));
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
