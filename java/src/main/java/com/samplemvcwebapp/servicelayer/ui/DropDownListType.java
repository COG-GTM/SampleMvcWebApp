package com.samplemvcwebapp.servicelayer.ui;

import java.util.ArrayList;
import java.util.List;

/** Mirrors ServiceLayer.UiClasses.DropDownListType (blogger selector). Form field: Bloggers.SelectedValue. */
public class DropDownListType {

    private List<KeyValue> keyValueList = new ArrayList<>();
    private String selectedValue;
    private String promptString;

    public List<KeyValue> getKeyValueList() { return keyValueList; }
    public void setKeyValueList(List<KeyValue> keyValueList) { this.keyValueList = keyValueList; }
    public String getSelectedValue() { return selectedValue; }
    public void setSelectedValue(String selectedValue) { this.selectedValue = selectedValue; }

    /** Legacy SetupDropDownListContent prompt (e.g. "--- choose blogger ---"), rendered as a first option with no value. */
    public String getPromptString() { return promptString; }
    public void setPromptString(String promptString) { this.promptString = promptString; }

    public Integer getSelectedValueAsInt() {
        if (selectedValue == null || selectedValue.isBlank()) return null;
        try {
            return Integer.valueOf(selectedValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
