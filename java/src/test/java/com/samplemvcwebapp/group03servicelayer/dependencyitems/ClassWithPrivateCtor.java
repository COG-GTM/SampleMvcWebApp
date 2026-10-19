package com.samplemvcwebapp.group03servicelayer.dependencyitems;

/** Port of Tests/DependencyItems/ClassWithPrivateCtor.cs: only reachable through its factory method. */
public final class ClassWithPrivateCtor {

    private ClassWithPrivateCtor(UnregisteredDependency dependency) {
    }

    public static ClassWithPrivateCtor create() {
        return new ClassWithPrivateCtor(new UnregisteredDependency());
    }

    /** Never registered as a bean, so the container cannot satisfy the private constructor itself. */
    public static final class UnregisteredDependency {
    }
}
