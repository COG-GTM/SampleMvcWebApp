#region licence
// The MIT License (MIT)
// 
// Filename: SampleWebAppDbDesignTimeFactory.cs
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
using System;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DataLayer.DataClasses
{
    /// <summary>
    /// This allows "dotnet ef ..." to build a SampleWebAppDb without needing a startup project.
    /// The connection string comes from the SampleWebAppDb environment variable.
    /// </summary>
    public class SampleWebAppDbDesignTimeFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        internal const string DefaultConnectionString =
            "Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True";

        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString = Environment.GetEnvironmentVariable(SampleWebAppDb.NameOfConnectionString);
            if (string.IsNullOrEmpty(connectionString))
                connectionString = DefaultConnectionString;

            var options = new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlServer(connectionString)
                .Options;

            return new SampleWebAppDb(options);
        }
    }
}
