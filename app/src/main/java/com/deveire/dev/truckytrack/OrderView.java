package com.deveire.dev.truckytrack;

import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Created by owenryan on 14/08/2017.
 */
public class OrderView extends ConstraintLayout
{

    private View rootView;
    private TextView patronName;
    private ImageView patronPhoto;
    private ImageView drinkCounterImage;
    private TextView drinkCount;
    private ImageButton dismissButton;
    private ImageButton addAnotherButton;
    private TextView patronPreferedDrink;
    private ImageView bottomBar;
    private TextView serialCodeText;

    private int numberOfDrinksOrdered;
    private String preferedDrinkType;

    public OrderView(Context context)
    {
        super(context);
        init(context);
    }

    public OrderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context)
    {
        Log.i("Order", "Creating Order");
        rootView = inflate(context, R.layout.order_view, this);
        patronName = (TextView) rootView.findViewById(R.id.patronName);
        drinkCount = (TextView) rootView.findViewById(R.id.drinkCount);
        patronPreferedDrink = (TextView) rootView.findViewById(R.id.patronPreferedDrink);
        patronPhoto = (ImageView) rootView.findViewById(R.id.patronPhoto);
        drinkCounterImage = (ImageView) rootView.findViewById(R.id.drinkCounterImage);
        bottomBar = (ImageView) rootView.findViewById(R.id.bottomBar);
        dismissButton = (ImageButton) rootView.findViewById(R.id.dismissButton);
        addAnotherButton = (ImageButton) rootView.findViewById(R.id.addAnotherButton);
        serialCodeText = (TextView) rootView.findViewById(R.id.serialCodeText);
        int a1 = (int)((Math.random() * 26) + 'a');
        char a = (char)a1;
        int b1 = (int)((Math.random() * 26) + 'a');
        char b = (char)b1;
        int c = (int) (Math.random() * 10);
        int d = (int) (Math.random() * 10);
        serialCodeText.setText(a + "" + b + "" + c + "" + d);


        numberOfDrinksOrdered = 1;
        preferedDrinkType = "";

        dismissButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ((ViewManager)rootView.getParent().getParent()).removeView((View) rootView.getParent());
            }
        });

        addAnotherButton.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                numberOfDrinksOrdered++;
                patronPreferedDrink.setText(numberOfDrinksOrdered + " " + preferedDrinkType);
            }
        });
    }

    void setOrder(String inName, String inPreferedDrinkName, int inDrinkCount)
    {
        Log.i("Order", "Setting Order");
        patronName.setText(inName);
        preferedDrinkType = inPreferedDrinkName;
        patronPreferedDrink.setText(numberOfDrinksOrdered + " " + preferedDrinkType);
        drinkCount.setText("" + inDrinkCount);
    }
}
