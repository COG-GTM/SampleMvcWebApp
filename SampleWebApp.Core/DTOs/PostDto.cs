using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using SampleWebApp.Core.Common;
using SampleWebApp.Core.UiClasses;

namespace SampleWebApp.Core.DTOs
{
    public class PostDto : BaseDto
    {
        public int PostId { get; set; }

        [Required]
        [MinLength(2), MaxLength(128)]
        public string Title { get; set; }

        [Required]
        [DataType(DataType.MultilineText)]
        public string Content { get; set; }

        public string BloggerName { get; set; }

        public int BlogId { get; set; }

        public ICollection<TagDto> Tags { get; set; }

        public DateTime LastUpdated { get; set; }

        public DropDownListType Bloggers { get; set; }

        public MultiSelectListType UserChosenTags { get; set; }

        public DateTime LastUpdatedUtc => DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc);

        public string TagNames => Tags != null ? string.Join(", ", Tags.Select(x => x.Name)) : string.Empty;

        public PostDto()
        {
            Bloggers = new DropDownListType();
            UserChosenTags = new MultiSelectListType();
            Tags = new List<TagDto>();
        }
    }
}
