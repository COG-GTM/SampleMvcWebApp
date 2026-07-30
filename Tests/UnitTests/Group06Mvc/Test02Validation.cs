#region licence
// The MIT License (MIT)
// 
// Filename: Test02Validation.cs
// Date Created: 2014/05/20
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System.Linq;
using NUnit.Framework;
using SampleWebApp.Infrastructure;
using StatusGeneric;
using Tests.Helpers;

namespace Tests.UnitTests.Group06Mvc
{
    public class Test02Validation
    {

        [Test]
        public void Check01TestModelValidate()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", 50, true);

            //ATTEMPT
            var vErrors = model.Validate(null).ToList();

            //VERIFY
            vErrors.Count.ShouldEqual(2);
            vErrors[0].MemberNames.Any().ShouldEqual(false);
            vErrors[0].ErrorMessage.ShouldEqual("This is a top level error caused by CreateValidationError being set.");
            vErrors[1].MemberNames.Any().ShouldEqual(false);
            vErrors[1].ErrorMessage.ShouldEqual("This is a top level error caused by MyInt having value 50.");
        }


        [Test]
        public void Check05TestModelStateValidateOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", 50, true);

            //ATTEMPT
            var modelState = model.ReturnModelState();

            //VERIFY
            modelState.IsValid.ShouldEqual(false);
            modelState.Keys.Count().ShouldEqual(1);
            modelState.Keys.First().ShouldEqual("");
            modelState[modelState.Keys.First()].Errors.Count.ShouldEqual(2);
            modelState[modelState.Keys.First()].Errors[0].ErrorMessage.ShouldEqual("This is a top level error caused by CreateValidationError being set.");
            modelState[modelState.Keys.First()].Errors[1].ErrorMessage.ShouldEqual("This is a top level error caused by MyInt having value 50.");
        }

        [Test]
        public void Check06TestModelStateValidateOneOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", 2, true);

            //ATTEMPT
            var modelState = model.ReturnModelState();

            //VERIFY
            modelState.IsValid.ShouldEqual(false);
            modelState.Keys.Count().ShouldEqual(1);
            modelState.Keys.First().ShouldEqual("");
            modelState[modelState.Keys.First()].Errors.Count.ShouldEqual(1);
            modelState[modelState.Keys.First()].Errors[0].ErrorMessage.ShouldEqual("This is a top level error caused by CreateValidationError being set.");
        }

        [Test]
        public void Check06TestModelStateIntAttributeOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", -1, false);

            //ATTEMPT
            var modelState = model.ReturnModelState();

            //VERIFY
            modelState.IsValid.ShouldEqual(false);
            modelState.Keys.Count().ShouldEqual(1);
            modelState.Keys.First().ShouldEqual("MyInt");
            modelState[modelState.Keys.First()].Errors.Count.ShouldEqual(1);
            modelState[modelState.Keys.First()].Errors[0].ErrorMessage.ShouldEqual("The field MyInt must be between 0 and 100.");

        }

        [Test]
        public void Check07TestModelStateStringAttributeOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("", 2, false);

            //ATTEMPT
            var modelState = model.ReturnModelState();

            //VERIFY
            modelState.IsValid.ShouldEqual(false);
            modelState.Keys.Count().ShouldEqual(1);
            modelState.Keys.First().ShouldEqual("MyString");
            Assert.That(modelState[modelState.Keys.First()].Errors.Select(x => x.ErrorMessage), Is.EquivalentTo(new[]
            {
                "The field MyString must be a string or array type with a minimum length of '2'.",
                "The MyString field is required."
            }));


        }

        [Test]
        public void Check08TestModelStateMixedErrorsOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("", -1, true);

            //ATTEMPT
            var modelState = model.ReturnModelState();

            //VERIFY
            modelState.IsValid.ShouldEqual(false);
            Assert.That(modelState.Keys, Is.EquivalentTo(new[] { "MyInt", "MyString" }));     //Note: only runs Validate if no attribute errors
            modelState["MyInt"].Errors.Count.ShouldEqual(1);
            modelState["MyString"].Errors.Count.ShouldEqual(2);
        }

        //---------------------------------------------------------------------
        //now use to check ValidationHelper ReturnModelErrorsAsJson

        [Test]
        public void Check15TestModelStateValidateOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", 50, true);

            //ATTEMPT
            var jsonResult = model.ReturnModelState().ReturnModelErrorsAsJson();

            //VERIFY
            //ASP.NET Core's JsonResult holds the object in Value, not Data
            var json = jsonResult.Value.SerialiseToJson();
            json.ShouldEqual("{\"errorsDict\":{\"\":{\"errors\":[\"This is a top level error caused by CreateValidationError being set.\",\"This is a top level error caused by MyInt having value 50.\"]}}}");
        }

        [Test]
        public void Check16TestModelStateValidateOneOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", 2, true);

            //ATTEMPT
            var jsonResult = model.ReturnModelState().ReturnModelErrorsAsJson();

            //VERIFY
            var json = jsonResult.Value.SerialiseToJson();
            json.ShouldEqual("{\"errorsDict\":{\"\":{\"errors\":[\"This is a top level error caused by CreateValidationError being set.\"]}}}");
        }


        [Test]
        public void Check16TestModelStateIntAttributeOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("123", -1, false);

            //ATTEMPT
            var jsonResult = model.ReturnModelState().ReturnModelErrorsAsJson();

            //VERIFY
            var json = jsonResult.Value.SerialiseToJson();
            json.ShouldEqual("{\"errorsDict\":{\"MyInt\":{\"errors\":[\"The field MyInt must be between 0 and 100.\"]}}}");
        }

        [Test]
        public void Check17TestModelStateStringAttributeOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("", 2, false);

            //ATTEMPT
            var jsonResult = model.ReturnModelState().ReturnModelErrorsAsJson();

            //VERIFY
            //System.Text.Json escapes the ' in the MinLength message as \u0027
            var json = jsonResult.Value.SerialiseToJson();
            const string order1Json = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\",\"The MyString field is required.\"]}}}";
            const string order2Json =
                "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The MyString field is required.\",\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\"]}}}";
            (json == order1Json || json == order2Json).ShouldEqual(true, json);

        }

        [Test]
        public void Check18TestModelStateMixedErrorsOnly()
        {
            //SETUP  
            var model = new ModelStateTester.TestModel("", -1, true);

            //ATTEMPT
            var jsonResult = model.ReturnModelState().ReturnModelErrorsAsJson();

            //VERIFY
            var json = jsonResult.Value.SerialiseToJson();
            const string myStringOrder1 = "\"MyString\":{\"errors\":[\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\",\"The MyString field is required.\"]}";
            const string myStringOrder2 = "\"MyString\":{\"errors\":[\"The MyString field is required.\",\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\"]}";
            const string myInt = "\"MyInt\":{\"errors\":[\"The field MyInt must be between 0 and 100.\"]}";
            //ASP.NET Core's ModelStateDictionary returns its entries in key order, i.e. MyInt then MyString
            (json == "{\"errorsDict\":{" + myInt + "," + myStringOrder1 + "}}"
             || json == "{\"errorsDict\":{" + myInt + "," + myStringOrder2 + "}}").ShouldEqual(true, json);
        }

        //-------------------------------------------------------------------
        //now the ReturnErrorsAsJson

        [Test]
        public void Check20StatusToJsonTopLevel()
        {
            //SETUP  
            //StatusGeneric's StatusGenericHandler replaces GenericLibsBase's SuccessOrErrors
            var status = new StatusGenericHandler();
            var dto = new {MyInt = 1};

            //ATTEMPT
            status.AddError("This is a top level error.");
            var jsonResult = status.ReturnErrorsAsJson(dto);

            //VERIFY
            var json = jsonResult.Value.SerialiseToJson();
            json.ShouldEqual("{\"errorsDict\":{\"\":{\"errors\":[\"This is a top level error.\"]}}}");
        }

        [Test]
        public void Check21StatusToJsonProperty()
        {
            //SETUP  
            var status = new StatusGenericHandler();
            var dto = new { MyInt = 1 };

            //ATTEMPT
            status.AddError("This is a property level error.", "MyInt");
            var jsonResult = status.ReturnErrorsAsJson(dto);

            //VERIFY
            var json = jsonResult.Value.SerialiseToJson();
            json.ShouldEqual("{\"errorsDict\":{\"MyInt\":{\"errors\":[\"This is a property level error.\"]}}}");
        }

    }
}
