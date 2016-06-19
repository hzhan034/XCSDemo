package com.hugo.annotationprocessortest;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.hugo.annotationprocessortest.annnotation.Arg;
import com.hugo.annotationprocessortest.annnotation.FragmentWithArgs;



/**
 * Created by Administrator on 2016/6/18.
 */

@FragmentWithArgs
public class MyFragment extends Fragment {


    @Arg
    int id;

    @Arg
    private String title; // private fields requires a setter method

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FragmentArgs.inject(this);     // read @Arg fields
//        AutoFragmentArgInjector.inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        Toast.makeText(getActivity(), "Hello " + title,
                Toast.LENGTH_SHORT).show();

        return null;
    }

    // Setter method for private field
    public void setTitle(String title) {
        this.title = title;
    }

}
