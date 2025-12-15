using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using SampleWebApp.Core.Common;

namespace SampleWebApp.Core.DTOs
{
    public class SimplePostDto : BaseDto
    {
        [UIHint("HiddenInput")]
        public int PostId { get; set; }

        [UIHint("HiddenInput")]
        public int BlogId { get; set; }

        public string BloggerName { get; set; }

        [MinLength(2), MaxLength(128)]
        public string Title { get; set; }

        [ScaffoldColumn(false)]
        public ICollection<TagDto> Tags { get; set; }

        [ScaffoldColumn(false)]
        public DateTime LastUpdated { get; set; }

        public DateTime LastUpdatedUtc => DateTime.SpecifyKind(LastUpdated, DateTimeKind.Utc);

        public string TagNames => Tags != null ? string.Join(", ", Tags.Select(x => x.Name)) : string.Empty;

        public SimplePostDto()
        {
            Tags = new List<TagDto>();
        }
    }
}
