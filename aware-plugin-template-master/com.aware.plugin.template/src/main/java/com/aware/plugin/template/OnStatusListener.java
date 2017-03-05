package com.aware.plugin.template;

/**
 * Created by aayushchadha on 05/03/17.
 */

public interface OnStatusListener {
    void onListFragmentInteraction(String address);

    void onShowProgress();

    void onHideProgress();
}
