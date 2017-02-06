package org.antwork.core;

/**
 * Dial plan
 */

public interface DialPlan {

    String getCountryCode();
    String getCountryName();
    String getCountryCallingCode();
    int getNumberLength();
    String getUsualPrefix();

}
