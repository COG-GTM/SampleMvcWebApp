#region licence
// The MIT License (MIT)
// 
// Filename: DetailPostDtoAsync.cs
// Date Created: 2014/08/16
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
using System.ComponentModel.DataAnnotations;
using System.Linq;
using DataLayer.DataClasses.Concrete;
using ServiceLayer.UiClasses;

namespace ServiceLayer.PostServices
{
    public class DetailPostDtoAsync
    {
        [UIHint("HiddenInput")]
        [Key]
        public int PostId { get; set; }

        [MinLength(2), MaxLength(128)]
        public string Title { get; set; }

        [DataType(DataType.MultilineText)]
        [Required]
        public string Content { get; set; }

        public string BloggerName { get; set; }

        [ScaffoldColumn(false)]
        public DateTime LastUpdated { get; set; }

        [UIHint("HiddenInput")]
        public int BlogId { get; set; }

        [ScaffoldColumn(false)]
        public ICollection<Tag> Tags { get; set; }

        /// <summary>
        /// This allows a single blogger to be chosen from the list
        /// </summary>
        public DropDownListType Bloggers { get; set; }

        public MultiSelectListType UserChosenTags { get; set; }

        /// <summary>
        /// When it was last updated in DateTime format
        /// </summary>
        public DateTime LastUpdatedUtc { get { return DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc); } }

        public string TagNames { get { return Tags != null ? string.Join(", ", Tags.Select(x => x.Name)) : ""; } }

        public DetailPostDtoAsync()
        {
            Tags = new List<Tag>();
            Bloggers = new DropDownListType();
            UserChosenTags = new MultiSelectListType();
        }
    }
}
