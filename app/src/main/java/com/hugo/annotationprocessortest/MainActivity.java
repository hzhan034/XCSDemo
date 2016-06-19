package com.hugo.annotationprocessortest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.support.v4.app.Fragment;


public class MainActivity extends AppCompatActivity {


//    private MealFactory factory;

    private Button orderBtn;
    private TextView priceTxt;
    private EditText orderEdit;

    private String mealName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();


        initFrg();

//        factory = new MealFactory();
    }

    private void initFrg() {


        int id = 123;
        String title = "test";

//        // Using the generated Builder
        Fragment fragment =
                new MyFragmentBuilder(id, title)
                        .build();
//
//
//        // Fragment Transaction

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private void initView() {

        orderBtn = (Button) findViewById(R.id.order_btn);
        priceTxt = (TextView) findViewById(R.id.price_txt);
        orderEdit = (EditText) findViewById(R.id.edit_pizza_type);
        orderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
//                    Meal meal = factory.create(orderEdit.getText() + "");
//                    priceTxt.setText(meal.getPrice() + "");
                } catch (Exception e) {

                }
            }
        });
    }


}
