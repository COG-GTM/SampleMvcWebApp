#region licence
// The MIT License (MIT)
// 
// Filename: Test10SetupBlogs.cs
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
using System.Linq;
using DataLayer.Startup;
using DataLayer.Startup.Internal;
using Microsoft.EntityFrameworkCore;
using NUnit.Framework;
using Tests.Helpers;

namespace Tests.UnitTests.Group01DataLayer
{
    public class Test10SetupBlogs
    {
        [Test]
        public void Check01XmlFileLoadOk()
        {

            //SETUP

            //ATTEMPT
            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts("DataLayer.Startup.Internal.BlogsContentSimple.xml").ToList();

            //VERIFY
            bloggers.Count().ShouldEqual(2);
            bloggers.SelectMany( x => x.Posts).Count().ShouldEqual(3);
            bloggers.SelectMany( x => x.Posts.SelectMany( y => y.Tags)).Distinct().Count().ShouldEqual(3);

        }

        [Test]
        public void Check02XmlFileLoadBad()
        {

            //SETUP

            //ATTEMPT
            var ex = Assert.Throws<NullReferenceException>( () => LoadDbDataFromXml.FormBlogsWithPosts("badname.xml"));

            //VERIFY
            ex.Message.ShouldStartWith("Could not find the xml file you asked for.");

        }

        [Test]
        public void Check10BlogsResetSmallOk()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                db.Database.Migrate();

                //ATTEMPT
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);

                //VERIFY
                db.Blogs.Count().ShouldEqual(2);
                db.Posts.Count().ShouldEqual(3);
                db.Tags.Count().ShouldEqual(3);
            }
        }

        [Test]
        public void Check11BlogsResetMediumOk()
        {
            using (var db = TestDbHelper.CreateContext())
            {
                //SETUP
                db.Database.Migrate();

                //ATTEMPT
                DataLayerInitialise.ResetBlogs(db, TestDataSelection.Medium);

                //VERIFY
                db.Blogs.Count().ShouldEqual(4);
                db.Posts.Count().ShouldEqual(17);
                db.Tags.Count().ShouldEqual(8);
            }
        }

        //---------------------------------------------------------

        [Test]
        public void Check20MigrateAndSeedOk()
        {
            //SETUP
            using (var db = TestDbHelper.CreateContext())
            {
                db.Database.EnsureDeleted();
            }

            //ATTEMPT
            //EF Core has no database initialisers, so MigrateAndSeed replaces InitialiseThis
            using (var db = TestDbHelper.CreateContext())
            {
                DataLayerInitialise.MigrateAndSeed(db, TestDataSelection.Small);
            }

            //VERIFY
            using (var db = TestDbHelper.CreateContext())
            {
                db.Blogs.Count().ShouldEqual(2);
                db.Posts.Count().ShouldEqual(3);
                db.Tags.Count().ShouldEqual(3);
            }
        }
    }
}
