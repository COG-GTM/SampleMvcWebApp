using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using ServiceLayer.UiClasses;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public abstract class DetailPostDtoBase
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
        [ReadOnly(true)]
        public string BloggerName { get; set; }

        //-------------------------------------------
        //properties that cannot be set directly (The data layer looks after them)

        [ScaffoldColumn(false)]
        [ReadOnly(true)]
        public DateTime LastUpdated { get; set; }

        //------------------------------------------
        //these two items are altered by the SetupRestOfDto method based on the user's selection

        [UIHint("HiddenInput")]
        public int BlogId { get; set; }

        [ScaffoldColumn(false)]
        public ICollection<Tag> Tags { get; set; }

        //-------------------------------------------
        //now the various lists for user interaction

        /// <summary>
        /// This allows a single blogger to be chosen from the list
        /// </summary>
        public DropDownListType Bloggers { get; set; } = new DropDownListType();

        public MultiSelectListType UserChosenTags { get; set; } = new MultiSelectListType();

        //-------------------------------------------
        //calculated properties to help display 

        /// <summary>
        /// When it was last updated in DateTime format
        /// </summary>
        public DateTime LastUpdatedUtc { get { return DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc); } }

        public string TagNames { get { return string.Join(", ", (Tags ?? new List<Tag>()).Select(x => x.Name)); } }

        //---------------------------------------------------
        //shared helpers

        protected void FillSecondaryData(IEnumerable<Blog> bloggers, IEnumerable<Tag> allTags)
        {
            Bloggers.SetupDropDownListContent(
                bloggers.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (PostId != 0)
                //there is an entry, so set the selected value to that
                Bloggers.SetSelectedValue(BlogId.ToString("D"));

            var preselectedTags = PostId == 0 || Tags == null
                ? new List<KeyValuePair<string, int>>()     //Create, so no tags selected yet
                : Tags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList();

            UserChosenTags.SetupMultiSelectList(
                allTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)), preselectedTags);
        }

        protected static IQueryable<Post> PostWithTags(SampleWebAppDb db, int postId)
        {
            return db.Posts.Include(p => p.Tags).Where(p => p.PostId == postId);
        }

        protected IStatusGeneric CheckSelections(Blog blogger, int[] requiredTagIds, int numTagsFound)
        {
            var status = new StatusGenericHandler();
            if (Bloggers.SelectedValueAsInt == null)
                status.AddError("The blogger was not selected. You must do that before the post can be saved.", nameof(Bloggers));
            else if (blogger == null)
                status.AddError("Could not find the blogger you selected. Did another user delete it?", nameof(Bloggers));
            else
                BlogId = blogger.BlogId;

            if (!requiredTagIds.Any())
                status.AddError("You must select at least one tag for the post.", nameof(UserChosenTags));
            else if (numTagsFound != requiredTagIds.Distinct().Count())
                status.AddError("Could not find one of the tags. Did another user delete it?", nameof(UserChosenTags));
            return status;
        }
    }
}
