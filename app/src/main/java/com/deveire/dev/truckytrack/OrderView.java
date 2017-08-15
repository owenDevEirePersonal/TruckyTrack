package com.deveire.dev.truckytrack;

import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
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
    private TextView patronPreferedDrink;
    private ImageView bottomBar;

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
        rootView = inflate(context, R.layout.order_view, this);
        patronName = (TextView) rootView.findViewById(R.id.patronName);
        drinkCount = (TextView) rootView.findViewById(R.id.drinkCount);
        patronPreferedDrink = (TextView) rootView.findViewById(R.id.patronPreferedDrink);
        patronPhoto = (ImageView) rootView.findViewById(R.id.patronPhoto);
        drinkCounterImage = (ImageView) rootView.findViewById(R.id.drinkCounterImage);
        bottomBar = (ImageView) rootView.findViewById(R.id.bottomBar);
        dismissButton = (ImageButton) rootView.findViewById(R.id.dismissButton);


        dismissButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ((ViewManager)rootView.getParent().getParent()).removeView((View) rootView.getParent());
            }
        });
    }
}
