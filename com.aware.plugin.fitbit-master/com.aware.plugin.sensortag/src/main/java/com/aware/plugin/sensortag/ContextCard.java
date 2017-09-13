package com.aware.plugin.sensortag;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.utils.IContextCard;

/**
 * Created by denzil on 13/01/2017.
 */

public class ContextCard implements IContextCard {

    public ContextCard() {
    }

    @Override
    public View getContextCard(Context context) {

        View card = LayoutInflater.from(context).inflate(R.layout.card, null);
        //getDailySteps(context, (BarChart) card.findViewById(R.id.daily_steps));
        //getDailyHeartRate(context, (LineChart) card.findViewById(R.id.daily_heartrate));

        return card;
    }


//    private class HoursAxisValueFormatter implements IAxisValueFormatter {
//        @Override
//        public String getFormattedValue(float value, AxisBase axis) {
//            Date date = new Date((long) value);
//            return String.valueOf(date.getHours());
//        }
//    }
}
