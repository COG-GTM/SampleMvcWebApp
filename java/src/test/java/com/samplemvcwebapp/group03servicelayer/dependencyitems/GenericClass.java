package com.samplemvcwebapp.group03servicelayer.dependencyitems;

/** Port of Tests/DependencyItems/GenericInterface.cs (GenericInterface&lt;T&gt;); Java erases T, so it is captured. */
public class GenericClass<T> implements GenericInterface<T> {

    private final Class<T> type;

    public GenericClass(Class<T> type) {
        this.type = type;
    }

    @Override
    public String getTypeName() {
        return type.getSimpleName();
    }
}
