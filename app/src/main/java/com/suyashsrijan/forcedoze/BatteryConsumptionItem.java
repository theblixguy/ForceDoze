package com.suyashsrijan.forcedoze;

import java.io.Serializable;

public class BatteryConsumptionItem implements Serializable {
    public String getTimestampPercCombo() {
        return timestampPercCombo;
    }

    public void setTimestampPercCombo(String timestampPercCombo) {
        this.timestampPercCombo = timestampPercCombo;
    }

    private String timestampPercCombo;

}