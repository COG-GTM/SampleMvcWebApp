package com.samplemvcwebapp.servicelayer.ui;

import java.util.ArrayList;
import java.util.List;

/** Mirrors ServiceLayer.UiClasses.MultiSelectListType (tag selector). Form field: UserChosenTags.FinalSelection. */
public class MultiSelectListType {

    private List<KeyValue> keyValueList = new ArrayList<>();
    private List<Integer> finalSelection = new ArrayList<>();

    public List<KeyValue> getKeyValueList() { return keyValueList; }
    public void setKeyValueList(List<KeyValue> keyValueList) { this.keyValueList = keyValueList; }
    public List<Integer> getFinalSelection() { return finalSelection; }
    public void setFinalSelection(List<Integer> finalSelection) { this.finalSelection = finalSelection; }

    public boolean isSelected(String value) {
        return finalSelection != null && finalSelection.stream().anyMatch(id -> String.valueOf(id).equals(value));
    }
}
