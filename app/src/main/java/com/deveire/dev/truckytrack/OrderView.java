package com.deveire.dev.truckytrack;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
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
    private TextView balanceText;

    private String attachedUID;
    private int numberOfDrinksOrdered;
    private String preferedDrinkType;
    private OrderDismissObserver dismissObserver;
    private OrderAddAnotherObserver addAnotherObserver;

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
        balanceText = (TextView) findViewById(R.id.balanceText);
        patronPhoto = (ImageView) rootView.findViewById(R.id.patronPhoto);
        drinkCounterImage = (ImageView) rootView.findViewById(R.id.drinkCounterImage);
        bottomBar = (ImageView) rootView.findViewById(R.id.bottomBar);
        dismissButton = (ImageButton) rootView.findViewById(R.id.dismissButton);
        addAnotherButton = (ImageButton) rootView.findViewById(R.id.addAnotherButton);

        serialCodeText = (TextView) rootView.findViewById(R.id.serialCodeText);
        int a1 = (int)((Math.random() * 26) + 'a');
        char a = (char)a1;
        int b1 = (int)((Math.random() * 26 ) + 'a');
        char b = (char)b1;
        int c = (int) (Math.random() * 10);
        int d = (int) (Math.random() * 10);
        serialCodeText.setText(a + "" + b + "" + c + "" + d);


        numberOfDrinksOrdered = 1;
        preferedDrinkType = "";

        //aOrderView passes this instance of OrderView to the Observer, allowing it to call OrderView methods on this instance from callback
        final OrderView aOrderView = this;

        dismissButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(dismissObserver != null)
                {
                    dismissObserver.callBack(numberOfDrinksOrdered, aOrderView);
                }
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
                if(addAnotherObserver != null)
                {
                    addAnotherObserver.callBack(numberOfDrinksOrdered, aOrderView);
                }
            }
        });
    }

    void setOrder(String inName, String inPreferedDrinkName, int inDrinkCount, float inBalance, String inAttachedUID)
    {
        Log.i("Order", "Setting Order");
        patronName.setText(inName);
        preferedDrinkType = inPreferedDrinkName;
        patronPreferedDrink.setText(numberOfDrinksOrdered + " " + preferedDrinkType);
        drinkCount.setText("" + inDrinkCount);
        balanceText.setText(inBalance + "€");
        attachedUID = inAttachedUID;
    }

    void setDismissObserver(OrderDismissObserver aObserver)
    {
        dismissObserver = aObserver;
    }

    void setAddAnotherObserver(OrderAddAnotherObserver aObserver) { addAnotherObserver = aObserver; }

    public void setPreferedDrinkText(String inText)
    {
        patronPreferedDrink.setText(inText);
    }

    public String getAttachedUID(){ return attachedUID; }
}
