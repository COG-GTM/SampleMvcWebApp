package com.samplemvcwebapp.datalayer.validation;

/** Exact legacy DataAnnotations / IValidatableObject messages (JAVA_MIGRATION_NOTES.md section 6). */
public final class ValidationMessages {

    private ValidationMessages() {
    }

    public static final String TITLE_REQUIRED = "The Title field is required.";
    public static final String TITLE_MIN = "The field Title must be a string or array type with a minimum length of '2'.";
    public static final String TITLE_MAX = "The field Title must be a string or array type with a maximum length of '128'.";
    public static final String CONTENT_REQUIRED = "The Content field is required.";
    public static final String TITLE_NO_BANG = "Sorry, but you can't get too excited and include a ! in the title.";
    public static final String TITLE_NO_QUESTION = "Sorry, but you can't ask a question, i.e. the title can't end with '?'.";
    public static final String POST_NEEDS_TAG = "The post must have at least one Tag.";

    public static final String TAG_NAME_REQUIRED = "The Name field is required.";
    public static final String TAG_NAME_MAX = "The field Name must be a string or array type with a maximum length of '128'.";
    public static final String TAG_SLUG_REQUIRED = "The Slug field is required.";
    public static final String TAG_SLUG_MAX = "The field Slug must be a string or array type with a maximum length of '64'.";
    public static final String TAG_SLUG_PATTERN = "The slug must not contain spaces or non-alphanumeric characters.";
    public static final String TAG_SLUG_UNIQUE_FORMAT = "The Slug on tag '%s' must be unique and is already being used.";

    public static final String BLOG_NAME_REQUIRED = "The Name field is required.";
    public static final String BLOG_NAME_MIN = "The field Name must be a string or array type with a minimum length of '2'.";
    public static final String BLOG_NAME_MAX = "The field Name must be a string or array type with a maximum length of '64'.";
    public static final String BLOG_EMAIL_REQUIRED = "The EmailAddress field is required.";
    public static final String BLOG_EMAIL_FORMAT = "The EmailAddress field is not a valid e-mail address.";
    public static final String BLOG_EMAIL_MAX = "The field EmailAddress must be a string or array type with a maximum length of '256'.";

    public static String sentenceEnding(String word) {
        return "Sorry. Not allowed to end a sentance with '" + word + "'.";
    }

    public static String slugNotUnique(String tagName) {
        return String.format(TAG_SLUG_UNIQUE_FORMAT, tagName);
    }
}
