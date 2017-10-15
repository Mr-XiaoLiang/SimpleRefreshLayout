package liang.lollipop.demo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

/**
 * @author Lollipop
 */
public class StartActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
    }

    @Override
    public void onClick(View view) {
        Intent intent = new Intent(this,MainActivity.class);
        switch (view.getId()){
            case R.id.system_style:
                intent.putExtra(MainActivity.ARG_STYLE,MainActivity.STYLE_CIRCLE_MATERIAL);
                break;
            case R.id.simple_style:
                intent.putExtra(MainActivity.ARG_STYLE,MainActivity.STYLE_SIMPLE_PULL);
                break;
            default:
                return;
        }
        startActivity(intent);
    }
}
