using System.ComponentModel.DataAnnotations;

namespace SampleWebApp.Core.Entities
{
    public class Post
    {
        public int PostId { get; set; }

        [MinLength(2), MaxLength(128)]
        [Required]
        public string Title { get; set; } = string.Empty;

        [Required]
        public string Content { get; set; } = string.Empty;

        public DateTime LastUpdated { get; set; }

        public int BlogId { get; set; }
        public virtual Blog Blogger { get; set; } = null!;

        public ICollection<Tag> Tags { get; set; } = new List<Tag>();
    }
}
