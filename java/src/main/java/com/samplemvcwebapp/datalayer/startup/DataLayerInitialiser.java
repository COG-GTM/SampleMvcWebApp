package com.samplemvcwebapp.datalayer.startup;

/** Mirrors DataLayer.Startup.DataLayerInitialise.ResetBlogs: delete all posts, tags, blogs then load the XML seed. */
public interface DataLayerInitialiser {

    void resetBlogs(TestDataSelection selection);
}
