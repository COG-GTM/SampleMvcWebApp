using System.ComponentModel.DataAnnotations;

namespace SampleWebApp.Core.Entities
{
    public class Tag
    {
        public int TagId { get; set; }

        [MaxLength(64)]
        [Required]
        public string Slug { get; set; } = string.Empty;

        [MaxLength(128)]
        [Required]
        public string Name { get; set; } = string.Empty;

        public ICollection<Post> Posts { get; set; } = new List<Post>();
    }
}
