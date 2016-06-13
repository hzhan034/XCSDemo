package com.hugo.annotationprocessortest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {


    private MealFactory factory;

    private Button orderBtn;
    private TextView priceTxt;
    private EditText orderEdit;

    private String mealName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();

        factory = new MealFactory();
    }

    private void initView() {

        orderBtn = (Button) findViewById(R.id.order_btn);
        priceTxt = (TextView) findViewById(R.id.price_txt);
        orderEdit = (EditText) findViewById(R.id.edit_pizza_type);
        orderBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Meal meal = factory.create(orderEdit.getText() + "");
                    priceTxt.setText(meal.getPrice() + "");
                } catch (Exception e) {

                }
            }
        });
    }


}
