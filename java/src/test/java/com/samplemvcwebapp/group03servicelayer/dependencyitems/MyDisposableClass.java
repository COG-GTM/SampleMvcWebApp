package com.samplemvcwebapp.group03servicelayer.dependencyitems;

/** Port of Tests/DependencyItems/MyDisposableClass.cs: IDisposable maps to AutoCloseable (Spring calls close()). */
public class MyDisposableClass implements AutoCloseable {

    private final Runnable disposeWasCalled;

    public MyDisposableClass(Runnable disposeWasCalled) {
        this.disposeWasCalled = disposeWasCalled;
    }

    @Override
    public void close() {
        disposeWasCalled.run();
    }
}
