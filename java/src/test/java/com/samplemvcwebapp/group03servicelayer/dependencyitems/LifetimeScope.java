package com.samplemvcwebapp.group03servicelayer.dependencyitems;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/** Minimal Spring {@link Scope} standing in for an Autofac lifetime scope: one instance per open scope. */
public class LifetimeScope implements Scope {

    public static final String NAME = "lifetime";

    private Map<String, Object> current = new HashMap<>();

    public void beginNewScope() {
        current = new HashMap<>();
    }

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        return current.computeIfAbsent(name, n -> objectFactory.getObject());
    }

    @Override
    public Object remove(String name) {
        return current.remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return null;
    }
}
