#region licence
// The MIT License (MIT)
// 
// Filename: LoadDbDataFromXml.cs
// Date Created: 2014/06/09
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
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Xml.Linq;
using DataLayer.DataClasses.Concrete;

namespace DataLayer.Startup.Internal
{
    internal static class LoadDbDataFromXml
    {
        public static IEnumerable<Blog> FormBlogsWithPosts(string filepathWithinAssembly)
        {
            var assemblyHoldingFile = Assembly.GetAssembly(typeof(LoadDbDataFromXml));

            using (var fileStream = assemblyHoldingFile.GetManifestResourceStream(filepathWithinAssembly))
            {
                if (fileStream == null)
                    throw new NullReferenceException("Could not find the xml file you asked for. Did you remember to set properties->BuildAction to Embedded Resource?");
                var xmlData = XElement.Load(fileStream);
                
                //now decode and return
                var tagsDict = DecodeTags(xmlData.Element("Tags"));
                return DecodeBlogs(xmlData.Element("Blogs"), tagsDict);
            }
        }

        //---------------------------------------------------
        //private helpers

        private static Dictionary<string, Tag> DecodeTags(XElement element)
        {
            return element.Elements("Tag").ToDictionary(
                el => el.Attribute("Slug").Value,
                el => new Tag
                {
                    Slug = el.Attribute("Slug").Value,
                    Name = el.Attribute("Name").Value
                });
        }

        private static IEnumerable<Blog> DecodeBlogs(XElement element, Dictionary<string, Tag> tagsDict)
        {
            return element.Elements("Blog").Select(
                blogXml => new Blog
                {
                    Name = blogXml.Attribute("Name").Value,
                    EmailAddress = blogXml.Attribute("Email").Value,
                    Posts = DecodePosts(blogXml.Element("Posts"), tagsDict).ToList()
                });
        }

        private static IEnumerable<Post> DecodePosts(XElement element, Dictionary<string, Tag> tagsDict)
        {
            return element.Elements("Post").Select(
                postXml => new Post
                {
                    Title = postXml.Attribute("Title").Value,
                    Content = postXml.Value,
                    Tags = postXml.Attribute("TagSlugs").Value.Split(',')
                        .Select(x => x.Trim())
                        .Where(x => !string.IsNullOrEmpty(x))
                        .Select(x => tagsDict[x]).ToList()
                });
        }
    }
}
