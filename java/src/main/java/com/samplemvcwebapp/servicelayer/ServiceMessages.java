package com.samplemvcwebapp.servicelayer;

/** GenericServices / DetailPostDto messages used by the service implementations. */
public final class ServiceMessages {

    private ServiceMessages() {
    }

    public static final String BLOGGER_NOT_SELECTED = "The blogger was not selected. You must do that before the post can be saved.";
    public static final String BLOGGER_NOT_FOUND = "Could not find the blogger you selected. Did another user delete it?";
    public static final String NO_TAG_SELECTED = "You must select at least one tag for the post.";
    public static final String TAG_NOT_FOUND = "Could not find one of the tags. Did another user delete it?";
    public static final String NOT_FOUND = "We could not find an entry using that filter. Has it been deleted by someone else?";
    public static final String CHOOSE_BLOGGER_PROMPT = "--- choose blogger ---";

    public static String created(String item) { return "Successfully created " + item + "."; }
    public static String updated(String item) { return "Successfully updated " + item + "."; }
    public static String deleted(String item) { return "Successfully deleted " + item + "."; }
}
