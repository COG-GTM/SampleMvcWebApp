#region licence
// The MIT License (MIT)
// 
// Filename: Test02Validation.cs
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
using System.Linq;
using NUnit.Framework;
using NUnit.Framework.Legacy;
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
            CollectionAssert.AreEquivalent(new[]
            {
                "The field MyString must be a string or array type with a minimum length of '2'.",
                "The MyString field is required."
            }, 
            modelState[modelState.Keys.First()].Errors.Select( x => x.ErrorMessage));


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
            CollectionAssert.AreEquivalent(new[] { "MyInt", "MyString" }, modelState.Keys);     //Note: only runs Validate if no attribute errors
            modelState["MyInt"].Errors.Count.ShouldEqual(1);
            modelState["MyString"].Errors.Count.ShouldEqual(2);
        }

        //---------------------------------------------------------------------
        //The old Check15-Check21 tests exercised SampleWebApp.Infrastructure.ValidationHelper
        //(ReturnModelErrorsAsJson / JsonNetResult) and the EF6 GenericServices SuccessOrErrors type.
        //Both of those types were deleted in the ASP.NET Core / EfCore.GenericServices migration, so the
        //JSON-serialisation tests no longer have anything to test and have been removed.
    }
}
