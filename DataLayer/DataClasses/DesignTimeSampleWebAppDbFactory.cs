#region licence
// The MIT License (MIT)
// 
// Filename: DesignTimeSampleWebAppDbFactory.cs
// Date Created: 2026/11/24
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
    /// Used by the dotnet-ef tools to create the context when adding migrations
    /// </summary>
    public class DesignTimeSampleWebAppDbFactory : IDesignTimeDbContextFactory<SampleWebAppDb>
    {
        public SampleWebAppDb CreateDbContext(string[] args)
        {
            var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__SampleWebAppDb")
                ?? "Server=localhost;Database=SampleWebAppDb;Trusted_Connection=True;TrustServerCertificate=True";
            var options = new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlServer(connectionString)
                .Options;
            return new SampleWebAppDb(options);
        }
    }
}
