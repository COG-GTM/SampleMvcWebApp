package com.samplemvcwebapp.testsupport;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.validator.constraints.Range;

/**
 * Port of Tests/Helpers/ModelStateTester.TestModel: DataAnnotations become Bean Validation constraints carrying the
 * legacy messages, and IValidatableObject.Validate becomes {@link #validate()} (model-level, i.e. no property).
 */
public class TestModel {

    @Size(min = 2, message = "The field MyString must be a string or array type with a minimum length of '2'.")
    @NotEmpty(message = "The MyString field is required.")
    private String myString;

    @Range(min = 0, max = 100, message = "The field MyInt must be between 0 and 100.")
    private int myInt;

    private boolean createValidationError;

    public TestModel() {
    }

    public TestModel(String myString, int myInt, boolean createValidationError) {
        this.myString = myString;
        this.myInt = myInt;
        this.createValidationError = createValidationError;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (createValidationError) {
            errors.add("This is a top level error caused by CreateValidationError being set.");
        }
        if (myInt == 50) {
            errors.add("This is a top level error caused by MyInt having value 50.");
        }
        return errors;
    }

    public String getMyString() { return myString; }
    public void setMyString(String myString) { this.myString = myString; }
    public int getMyInt() { return myInt; }
    public void setMyInt(int myInt) { this.myInt = myInt; }
    public boolean isCreateValidationError() { return createValidationError; }
    public void setCreateValidationError(boolean createValidationError) { this.createValidationError = createValidationError; }
}
