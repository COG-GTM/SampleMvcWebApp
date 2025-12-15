using System.ComponentModel.DataAnnotations;
using SampleWebApp.Core.Common;

namespace SampleWebApp.Core.DTOs
{
    public class TagDto : BaseDto
    {
        public int TagId { get; set; }

        [Required]
        [MaxLength(64)]
        [RegularExpression(@"\w*", ErrorMessage = "The slug must not contain spaces or non-alphanumeric characters.")]
        public string Slug { get; set; }

        [Required]
        [MaxLength(128)]
        public string Name { get; set; }

        public int PostsCount { get; set; }
    }
}
