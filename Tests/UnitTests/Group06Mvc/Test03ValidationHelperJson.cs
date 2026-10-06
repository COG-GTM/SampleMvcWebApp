#region licence
// The MIT License (MIT)
// 
// Filename: Test03ValidationHelperJson.cs
// Date Created: 2014/06/10
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
using NUnit.Framework;
using SampleWebApp.Infrastructure;
using StatusGeneric;
using Tests.Helpers;

namespace Tests.UnitTests.Group06Mvc
{
    public class Test03ValidationHelperJson
    {

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
            var json = jsonResult.Value.SerialiseToJson();
            const string order1 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The field MyString must be a string or array type with a minimum length of '2'.\",\"The MyString field is required.\"]}}}";
            const string order1Json = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\",\"The MyString field is required.\"]}}}";

            const string order2 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The MyString field is required.\",\"The field MyString must be a string or array type with a minimum length of '2'.\"]}}}";
            const string order2Json =
                "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The MyString field is required.\",\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\"]}}}";
            (json == order1Json || json == order2Json).ShouldEqual(true);

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
            const string order1 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The field MyString must be a string or array type with a minimum length of '2'.\",\"The MyString field is required.\"]},";
            const string order1Json = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\",\"The MyString field is required.\"]},";
            const string order2 = "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The MyString field is required.\",\"The field MyString must be a string or array type with a minimum length of '2'.\"]},";
            const string order2Json =
                "{\"errorsDict\":{\"MyString\":{\"errors\":[\"The MyString field is required.\",\"The field MyString must be a string or array type with a minimum length of \\u00272\\u0027.\"]},";
            const string part2 = "\"MyInt\":{\"errors\":[\"The field MyInt must be between 0 and 100.\"]}}}";
            (json == order1Json + part2 || json == order2Json + part2).ShouldEqual(true);
        }

        //-------------------------------------------------------------------
        //now the ReturnErrorsAsJson

        [Test]
        public void Check20StatusToJsonTopLevel()
        {
            //SETUP  
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
