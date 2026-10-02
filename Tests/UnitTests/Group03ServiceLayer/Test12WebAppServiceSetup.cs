#region licence
// The MIT License (MIT)
// 
// Filename: Test12WebAppServiceSetup.cs
// Date Created: 2014/05/22
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
using Microsoft.AspNetCore.Mvc.Testing;
using NUnit.Framework;

namespace Tests.UnitTests.Group03ServiceLayer
{
    //Needs the SampleWebApp project (excluded from compilation in Tests.csproj until Subsession B is merged)
    //This was Test20ViaMvcSetup in Test11AutoFacModules, which used SampleWebApp's AutofacDi.SetupDependency().
    //It now checks the web app's real DI container (built by Program.cs) resolves the same services
    [TestFixture]
    public class Test12WebAppServiceSetup
    {
        [Test]
        public void Test20ViaMvcSetup()
        {
            //SETUP
            using var factory = new WebApplicationFactory<Program>();

            //ATTEMPT & VERIFY
            Test11ServiceCollectionSetup.CheckExampleServicesResolve(factory.Services);
        }
    }
}
