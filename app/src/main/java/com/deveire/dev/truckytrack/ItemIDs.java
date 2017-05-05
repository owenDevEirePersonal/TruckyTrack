package com.deveire.dev.truckytrack;

import android.util.Log;

/**
 * Created by owenryan on 04/05/2017.
 */

class ItemIDs
{

    private int id;
    private String name;

    public ItemIDs()
    {

    }

    public ItemIDs(int inID, String inName)
    {
        if(!inName.contains(" - ")){
            Log.e("ItemID Format Warning", "Name does not match format, name should be of format \"inID - Name\"");
        }

        id = inID;
        name = inName;
    }

    public String getName()
    {
        return name;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int inId)
    {
        this.id = id;
    }

    public void setName(String inName)
    {
        this.name = name;
        if(!inName.contains(" - ")){
            Log.e("ItemID Format Warning", "Name does not match format, name should be of format \"inID - Name\"");
        }
    }


}
