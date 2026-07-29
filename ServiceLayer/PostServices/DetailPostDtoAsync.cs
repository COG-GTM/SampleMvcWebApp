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
using AutoMapper;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using GenericServices.Configuration;
using ServiceLayer.UiClasses;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// Create/Read/Update DTO for a Post used by the async path.
    /// Reading is done via <c>ICrudServicesAsync.ReadSingleAsync&lt;DetailPostDtoAsync&gt;(id)</c>.
    /// Dropdown/multi-select setup and mapping the user's selection onto the Post entity are handled by
    /// <see cref="IPostCrudHelper"/> (GenericServices has no SetupSecondaryData hook).
    /// </summary>
    public class DetailPostDtoAsync : ILinkToEntity<Post>
    {

        [UIHint("HiddenInput")]
        [Key]
        public int PostId { get; set; }

        [MinLength(2), MaxLength(128)]
        public string Title { get; set; }

        [DataType(DataType.MultilineText)]
        [Required]
        public string Content { get; set; }

        //AutoMapper understands that this means Blogger.Name and fills it with the Name of the Blogger
        public string BloggerName { get; set; }

        //-------------------------------------------
        //properties that cannot be set directly (The data layer looks after them)

        [ScaffoldColumn(false)]
        public DateTime LastUpdated { get; set; }

        //------------------------------------------
        //these two items are altered by IPostCrudHelper based on the user's selection

        [UIHint("HiddenInput")]
        public int BlogId { get; set; }

        [ScaffoldColumn(false)]
        public ICollection<Tag> Tags { get; set; }

        //-------------------------------------------
        //now the various lists for user interaction (populated by IPostCrudHelper.SetupSecondaryDataAsync)

        /// <summary>
        /// This allows a single blogger to be chosen from the list
        /// </summary>
        public DropDownListType Bloggers { get; set; }

        public MultiSelectListType UserChosenTags { get; set; }

        //-------------------------------------------
        //calculated properties to help display

        /// <summary>
        /// When it was last updated in DateTime format
        /// </summary>
        public DateTime LastUpdatedUtc { get { return DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc); } }

        public string TagNames { get { return Tags == null ? string.Empty : string.Join(", ", Tags.Select(x => x.Name)); } }

        //ctor
        public DetailPostDtoAsync()
        {
            Bloggers = new DropDownListType();
            UserChosenTags = new MultiSelectListType();
        }
    }

    /// <summary>
    /// Tells GenericServices' AutoMapper config to ignore the UI-only helper members so the
    /// read projection (and any save mapping) does not attempt to map them.
    /// </summary>
    public class DetailPostDtoAsyncConfig : PerDtoConfig<DetailPostDtoAsync, Post>
    {
        public override Action<IMappingExpression<Post, DetailPostDtoAsync>> AlterReadMapping
        {
            get
            {
                return cfg => cfg
                    .ForMember(d => d.Bloggers, o => o.Ignore())
                    .ForMember(d => d.UserChosenTags, o => o.Ignore());
            }
        }

        public override Action<IMappingExpression<DetailPostDtoAsync, Post>> AlterSaveMapping
        {
            get
            {
                return cfg => cfg
                    .ForMember(d => d.Tags, o => o.Ignore())
                    .ForMember(d => d.Blogger, o => o.Ignore())
                    .ForMember(d => d.LastUpdated, o => o.Ignore());
            }
        }
    }
}
