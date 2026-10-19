package com.samplemvcwebapp.web.infrastructure;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** View helpers exposed to every template as ${ui} (legacy EditorTemplates logic). */
@ControllerAdvice
public class UiHelper {

    @ModelAttribute("ui")
    public UiHelper ui() {
        return this;
    }

    /**
     * Prompt shown as the first, value-less option of a drop-down (legacy SetupDropDownListContent promptString,
     * e.g. "--- choose blogger ---"), or null. Read reflectively so the view works whether or not the service layer
     * exposes DropDownListType.promptString.
     */
    public String prompt(Object dropDown) {
        if (dropDown == null) return null;
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(dropDown);
        if (!wrapper.isReadableProperty("promptString")) return null;
        Object value = wrapper.getPropertyValue("promptString");
        return value == null ? null : value.toString();
    }
}
