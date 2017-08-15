package com.deveire.dev.truckytrack;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import java.util.ArrayList;

public class SetupPatronActivity extends AppCompatActivity
{

    private SharedPreferences savedData;
    private int savedTotal;
    private ArrayList<String> savedNames;
    private ArrayList<String> savedDrinks;
    private ArrayList<String> savedIDs;
    private ArrayList<Integer> savedDrinksCount;
    private int savedBalance;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_patron);

        savedData = this.getApplicationContext().getSharedPreferences("Drinks-On-Me SavedData", Context.MODE_PRIVATE);
        savedTotal = savedData.getInt("savedTotal", 0);
        for(int i = 0; i < savedTotal; i++)
        {
            savedNames.add(savedData.getString("patronName" + i, "Error"));
            savedDrinks.add(savedData.getString("patronDrinks" + i, "Error"));
            savedIDs.add(savedData.getString("patronIDs" + i, "Error"));
            savedDrinksCount.add(savedData.getInt("patronDrinksCount" + i, 0));
        }
    }

    protected void onStop()
    {
        super.onStop();
    }
}
