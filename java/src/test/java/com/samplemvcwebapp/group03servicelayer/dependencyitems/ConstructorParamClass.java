package com.samplemvcwebapp.group03servicelayer.dependencyitems;

/** Port of Tests/DependencyItems/ConstructorParamClass.cs. */
public class ConstructorParamClass {

    private final int myInt;

    public ConstructorParamClass(int myInt) {
        this.myInt = myInt;
    }

    public int getMyInt() {
        return myInt;
    }
}
