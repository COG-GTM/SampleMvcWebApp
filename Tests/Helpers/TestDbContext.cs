#region licence
// The MIT License (MIT)
//
// Filename: TestDbContext.cs
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
using DataLayer.DataClasses;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

namespace Tests.Helpers
{
    /// <summary>
    /// Builds <see cref="SampleWebAppDb"/> instances backed by an in-memory SQLite database.
    /// The relational SQLite provider is used (rather than the EF Core in-memory provider) because it
    /// honours the schema, including the unique index on <c>Tag.Slug</c> that the Slug tests rely on.
    ///
    /// A SQLite in-memory database only lives for as long as its connection is open, so the connection
    /// is created and kept open by the test; several <see cref="SampleWebAppDb"/> contexts can be built
    /// on the same connection to mimic the old "new SampleWebAppDb()" pattern (a fresh change tracker
    /// over the same underlying data).
    /// </summary>
    public static class TestDbContext
    {
        /// <summary>
        /// Opens a new in-memory SQLite connection and creates the schema on it.
        /// Keep the returned connection open for the lifetime of the test and dispose it when done.
        /// </summary>
        public static SqliteConnection CreateOpenConnection()
        {
            var connection = new SqliteConnection("DataSource=:memory:");
            connection.Open();
            using (var db = CreateContext(connection))
                db.Database.EnsureCreated();
            return connection;
        }

        /// <summary>
        /// Builds a <see cref="SampleWebAppDb"/> over an already-open connection.
        /// </summary>
        public static SampleWebAppDb CreateContext(SqliteConnection connection)
        {
            var options = new DbContextOptionsBuilder<SampleWebAppDb>()
                .UseSqlite(connection)
                .Options;
            return new SampleWebAppDb(options);
        }
    }
}
