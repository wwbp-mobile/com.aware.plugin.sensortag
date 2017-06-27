package com.aware.plugin.fitbit;

/**
 * Created by aayushchadha on 27/06/17.
 */

public class Measurement {
    private double x, y, z;

    private String time;

    public Measurement(double x, double y, double z, String time) {

        this.x = x;
        this.y = y;
        this.z = z;
        this.time = time;

    }

    public double getCombined() {
        return Math.sqrt(x*x + y*y + z*z);
    }

    public double getX () {
        return x;
    }

    public double getY () {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getTime() {
        return time;
    }
}
