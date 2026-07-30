#region licence
// The MIT License (MIT)
// 
// Filename: TestDbHelper.cs
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
using DataLayer.DataClasses;
using DataLayer.Startup;
using Microsoft.EntityFrameworkCore;

namespace Tests.Helpers
{
    /// <summary>
    /// EF Core has no connection-string-by-name resolution and no App.config, so every test builds
    /// its DbContextOptions here. Override the connection string with the SampleWebAppDb environment
    /// variable; the default is a SQL Server on localhost (see MIGRATION_NOTES.md section 10).
    /// </summary>
    internal static class TestDbHelper
    {
        private const string DefaultConnectionString =
            "Server=localhost,1433;Database=TestSampleWebAppDb;User Id=sa;Password=Str0ng!Passw0rd;" +
            "TrustServerCertificate=True;Encrypt=False;MultipleActiveResultSets=True";

        public static string ConnectionString
        {
            get
            {
                return Environment.GetEnvironmentVariable(SampleWebAppDb.NameOfConnectionString)
                       ?? DefaultConnectionString;
            }
        }

        public static DbContextOptions<SampleWebAppDb> GetOptions()
        {
            return new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlServer(ConnectionString)
                .Options;
        }

        public static SampleWebAppDb CreateContext()
        {
            return new SampleWebAppDb(GetOptions());
        }

        /// <summary>
        /// This replaces the EF6 Database.SetInitializer(new CreateDatabaseIfNotExists...) that the
        /// tests used to call: it applies the migrations and seeds the small test data set.
        /// </summary>
        public static void ResetDatabase(TestDataSelection selection = TestDataSelection.Small)
        {
            using (var db = CreateContext())
            {
                db.Database.Migrate();
                DataLayerInitialise.ResetBlogs(db, selection);
            }
        }
    }
}
