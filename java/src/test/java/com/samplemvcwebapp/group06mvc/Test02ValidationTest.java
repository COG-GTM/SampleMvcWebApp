package com.samplemvcwebapp.group06mvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.samplemvcwebapp.testsupport.ModelErrors;
import com.samplemvcwebapp.testsupport.ModelStateTestController;
import com.samplemvcwebapp.testsupport.TestModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;

/**
 * Port of Tests/UnitTests/Group06Mvc/Test02Validation.cs (13 tests). The model is bound and validated by Spring MVC
 * through MockMvc (legacy ran the MVC model binder by hand in ModelStateTester). Legacy has two pairs of duplicate
 * method names (Check06..., Check16...) which compile as overload-free names here; JSON is asserted without the
 * \u0027 escaping that .NET's JavaScriptSerializer applied to apostrophes.
 */
class Test02ValidationTest {

    private static final String TOP_CREATE = "This is a top level error caused by CreateValidationError being set.";
    private static final String TOP_FIFTY = "This is a top level error caused by MyInt having value 50.";
    private static final String MY_INT_RANGE = "The field MyInt must be between 0 and 100.";
    private static final String MY_STRING_MIN = "The field MyString must be a string or array type with a minimum length of '2'.";
    private static final String MY_STRING_REQUIRED = "The MyString field is required.";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ModelStateTestController()).build();
    }

    @Test
    void check01TestModelValidate() {
        List<String> vErrors = new TestModel("123", 50, true).validate();

        assertThat(vErrors).containsExactly(TOP_CREATE, TOP_FIFTY);
    }

    @Test
    void check05TestModelStateValidateOnly() throws Exception {
        Map<String, List<String>> modelState = modelState("123", 50, true);

        assertThat(modelState).containsOnlyKeys("");
        assertThat(modelState.get("")).containsExactly(TOP_CREATE, TOP_FIFTY);
    }

    @Test
    void check06TestModelStateValidateOneOnly() throws Exception {
        Map<String, List<String>> modelState = modelState("123", 2, true);

        assertThat(modelState).containsOnlyKeys("");
        assertThat(modelState.get("")).containsExactly(TOP_CREATE);
    }

    @Test
    void check06TestModelStateIntAttributeOnly() throws Exception {
        Map<String, List<String>> modelState = modelState("123", -1, false);

        assertThat(modelState).containsOnlyKeys("MyInt");
        assertThat(modelState.get("MyInt")).containsExactly(MY_INT_RANGE);
    }

    @Test
    void check07TestModelStateStringAttributeOnly() throws Exception {
        Map<String, List<String>> modelState = modelState("", 2, false);

        assertThat(modelState).containsOnlyKeys("MyString");
        assertThat(modelState.get("MyString")).containsExactlyInAnyOrder(MY_STRING_MIN, MY_STRING_REQUIRED);
    }

    @Test
    void check08TestModelStateMixedErrorsOnly() throws Exception {
        Map<String, List<String>> modelState = modelState("", -1, true);

        assertThat(modelState).containsOnlyKeys("MyInt", "MyString"); // only runs validate() if no attribute errors
        assertThat(modelState.get("MyInt")).hasSize(1);
        assertThat(modelState.get("MyString")).hasSize(2);
    }

    @Test
    void check15TestModelStateValidateOnly() throws Exception {
        assertThat(modelStateJson("123", 50, true)).isEqualTo(
                "{\"errorsDict\":{\"\":{\"errors\":[\"" + TOP_CREATE + "\",\"" + TOP_FIFTY + "\"]}}}");
    }

    @Test
    void check16TestModelStateValidateOneOnly() throws Exception {
        assertThat(modelStateJson("123", 2, true)).isEqualTo(
                "{\"errorsDict\":{\"\":{\"errors\":[\"" + TOP_CREATE + "\"]}}}");
    }

    @Test
    void check16TestModelStateIntAttributeOnly() throws Exception {
        assertThat(modelStateJson("123", -1, false)).isEqualTo(
                "{\"errorsDict\":{\"MyInt\":{\"errors\":[\"" + MY_INT_RANGE + "\"]}}}");
    }

    @Test
    void check17TestModelStateStringAttributeOnly() throws Exception {
        String order1 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"" + MY_STRING_MIN + "\",\"" + MY_STRING_REQUIRED + "\"]}}}";
        String order2 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"" + MY_STRING_REQUIRED + "\",\"" + MY_STRING_MIN + "\"]}}}";

        assertThat(modelStateJson("", 2, false)).isIn(order1, order2);
    }

    @Test
    void check18TestModelStateMixedErrorsOnly() throws Exception {
        String order1 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"" + MY_STRING_MIN + "\",\"" + MY_STRING_REQUIRED + "\"]},";
        String order2 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"" + MY_STRING_REQUIRED + "\",\"" + MY_STRING_MIN + "\"]},";
        String part2 = "\"MyInt\":{\"errors\":[\"" + MY_INT_RANGE + "\"]}}}";

        assertThat(modelStateJson("", -1, true)).isIn(order1 + part2, order2 + part2);
    }

    @Test
    void check20StatusToJsonTopLevel() throws Exception {
        mvc.perform(post("/test/status/json").param("message", "This is a top level error."))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"errorsDict\":{\"\":{\"errors\":[\"This is a top level error.\"]}}}", true));
    }

    @Test
    void check21StatusToJsonProperty() throws Exception {
        mvc.perform(post("/test/status/json").param("propertyName", "MyInt")
                        .param("message", "This is a property level error."))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"errorsDict\":{\"MyInt\":{\"errors\":[\"This is a property level error.\"]}}}", true));
    }

    // ------------------------------------------------------------------ helpers

    private static MockHttpServletRequestBuilder form(String url, String myString, int myInt, boolean createError) {
        return post(url).param("myString", myString).param("myInt", String.valueOf(myInt))
                .param("createValidationError", String.valueOf(createError));
    }

    private Map<String, List<String>> modelState(String myString, int myInt, boolean createError) throws Exception {
        var mav = mvc.perform(form("/test/modelstate", myString, myInt, createError))
                .andExpect(status().isOk())
                .andExpect(view().name(ModelStateTestController.VIEW))
                .andReturn().getModelAndView();
        assertThat(mav).isNotNull();
        BindingResult result = (BindingResult) mav.getModel().get(BindingResult.MODEL_KEY_PREFIX + "testModel");
        return ModelErrors.fromBindingResult(result);
    }

    private String modelStateJson(String myString, int myInt, boolean createError) throws Exception {
        return mvc.perform(form("/test/modelstate/json", myString, myInt, createError))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
