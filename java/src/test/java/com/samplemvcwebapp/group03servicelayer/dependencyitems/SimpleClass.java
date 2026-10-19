package com.samplemvcwebapp.group03servicelayer.dependencyitems;

import org.springframework.stereotype.Component;

/** Port of Tests/DependencyItems/SimpleClass.cs. {@code @Component} so the "register assembly" port can scan it. */
@Component
public class SimpleClass implements SimpleClassInterface {
    @Override
    public void doSomething() {
    }
}
