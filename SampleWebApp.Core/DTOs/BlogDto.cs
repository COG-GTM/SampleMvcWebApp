using System.ComponentModel.DataAnnotations;
using SampleWebApp.Core.Common;

namespace SampleWebApp.Core.DTOs
{
    public class BlogDto : BaseDto
    {
        public int BlogId { get; set; }

        [Required]
        [MinLength(2)]
        [MaxLength(64)]
        public string Name { get; set; }

        [Required]
        [MaxLength(256)]
        [EmailAddress]
        public string EmailAddress { get; set; }

        public int PostsCount { get; set; }
    }
}
