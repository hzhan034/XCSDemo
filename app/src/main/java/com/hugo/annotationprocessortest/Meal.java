/*
 * Copyright (C) 2015 Hannes Dorfmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hugo.annotationprocessortest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * @author Hannes Dorfmann
 */
public interface Meal {
  public float getPrice();
//
//    class MainActivity extends AppCompatActivity {
//
//
//        private MealFactory factory;
//
//        private Button orderBtn;
//        private TextView priceTxt;
//        private EditText orderEdit;
//
//        private String mealName;
//
//        @Override
//        protected void onCreate(Bundle savedInstanceState) {
//            super.onCreate(savedInstanceState);
//            setContentView(R.layout.activity_main);
//
//            initView();
//
//            factory = new MealFactory();
//        }
//
//        private void initView() {
//
//            orderBtn = (Button) findViewById(R.id.order_btn);
//            priceTxt = (TextView) findViewById(R.id.price_txt);
//            orderEdit = (EditText) findViewById(R.id.edit_pizza_type);
//            orderBtn.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    try {
//                        Meal meal = factory.create(orderEdit.getText() + "");
//                        priceTxt.setText(meal.getPrice() + "");
//                    } catch (Exception e) {
//
//                    }
//                }
//            });
//        }
//
//
//    }
}
